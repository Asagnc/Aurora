package io.github.asagnc.aurora.terminal

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.*
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resumeWithException
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*

internal data class PtyRequest(val argv: List<String> = listOf("/system/bin/sh"), val directory: String = "",
    val columns: Int = 80, val rows: Int = 24, val timeoutMs: Long = 300_000,
    val environment: Map<String, String> = emptyMap(), val awakeMs: Long = 0) {
    fun validate() {
        require(columns in 2..300 && rows in 2..200 && timeoutMs in 1..3_600_000)
        require(awakeMs in 0..minOf(timeoutMs, 300_000))
        require(directory.length <= 4096 && '\u0000' !in directory)
        require(argv.size in 1..128 && argv.first().isNotBlank() && argv.all { '\u0000' !in it && it.length <= 65536 })
        require(argv.sumOf { it.toByteArray().size.toLong() } <= 262144)
        require(environment.size <= 64 && environment.all { (key, value) ->
            key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && '\u0000' !in value && value.length <= 8192
        })
        require(environment.entries.sumOf { (key, value) -> key.toByteArray().size + value.toByteArray().size + 2 } <= 65536)
    }
}

internal data class PtyOutcome(val exitCode: Int?, val state: String, val reason: String?) {
    fun wire() = objectOf("exitCode" to (exitCode?.let(::JsonPrimitive) ?: JsonNull), "state" to string(state),
        "reason" to (reason?.let(::string) ?: JsonNull)).toString()
    companion object {
        fun decode(value: String): PtyOutcome {
            require(value.length <= 4096)
            val item = WireJson.parseToJsonElement(value).jsonObject
            return PtyOutcome(item.number("exitCode")?.toInt(), requireNotNull(item.text("state")), item.text("reason"))
        }
        fun native(value: String): PtyOutcome {
            require(value.length <= 4096)
            val item = WireJson.parseToJsonElement(value).jsonObject
            require(item.number("protocol") == 1L)
            val reason = requireNotNull(item.text("reason"))
            return PtyOutcome(Math.toIntExact(requireNotNull(item.number("exitCode"))), when (reason) {
                "exit" -> "exited"; "deadline" -> "timed_out"; "stopped" -> "interrupted"; else -> "failed"
            }, reason.takeUnless { it == "exit" })
        }
    }
}

internal class PtyEndpoint(val output: InputStream, private val input: OutputStream,
    val exit: Deferred<PtyOutcome>, private val terminate: () -> Unit) {
    private val writing = Mutex()
    suspend fun write(bytes: ByteArray) = frame(1, bytes)
    suspend fun resize(columns: Int, rows: Int) {
        require(columns in 2..300 && rows in 2..200)
        frame(2, byteArrayOf(rows.toByte(), (rows shr 8).toByte(), columns.toByte(), (columns shr 8).toByte()))
    }
    private suspend fun frame(op: Int, bytes: ByteArray) = writing.withLock {
        require(bytes.size <= 4096)
        coroutineScope {
            val complete = java.util.concurrent.atomic.AtomicBoolean(false)
            // A blocked pipe/socket write is not reliably interruptible. Cancellation stops
            // its supervised peer, which closes the descriptor and unblocks this write.
            val cancel = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { if (!complete.get()) terminate() }
            }
            try {
                runInterruptible(Dispatchers.IO) {
                    input.write(byteArrayOf(op.toByte(), bytes.size.toByte(), (bytes.size shr 8).toByte()))
                    input.write(bytes); input.flush()
                }
                complete.set(true)
            } finally {
                // Successful writes must not stop the peer when the guard is retired.
                cancel.cancelAndJoin()
            }
        }
    }
    fun stop() { terminate() }
    fun closeStreams() { runCatching { input.close() }; runCatching { output.close() } }
}

internal class PtyProcess(context: Context) {
    private val executable = java.io.File(context.applicationInfo.nativeLibraryDir, "libaurora_pty.so")
    val available get() = executable.isFile && executable.canExecute()

    suspend fun open(root: Path, request: PtyRequest): PtyEndpoint {
        request.validate()
        check(available) { "Packaged PTY runner unavailable for this ABI" }
        return suspendCancellableCoroutine { continuation ->
            workers.execute {
                var process: Process? = null
                val exit = CompletableDeferred<PtyOutcome>()
                try {
                    val cwd = directory(root, request.directory)
                    val args = listOf(executable.path, request.rows.toString(), request.columns.toString(), request.timeoutMs.toString(), "--") + request.argv
                    process = ProcessBuilder(args).directory(cwd.toFile())
                        .apply { environment().apply {
                            clear(); put("PATH", "/system/bin:/system/xbin"); put("HOME", root.toString())
                            put("TMPDIR", cwd.toString()); put("TERM", "xterm-256color"); put("LANG", "C.UTF-8"); putAll(request.environment)
                        } }.start()
                    val child = requireNotNull(process)
                    val endpoint = PtyEndpoint(child.inputStream, child.outputStream, exit) { child.destroy() }
                    continuation.resume(endpoint) { _, abandoned, _ -> abandoned.stop(); abandoned.closeStreams() }
                    // PDEATHSIG is tied to this creating OS thread. Do not return it to the pool before waitFor.
                    var interrupted = false
                    while (true) {
                        try {
                            child.waitFor()
                            val metadata = String(child.errorStream.readNBytes(4097), Charsets.UTF_8)
                            exit.complete(PtyOutcome.native(metadata)); break
                        }
                        catch (_: InterruptedException) { interrupted = true; child.destroy() }
                    }
                    if (interrupted) Thread.currentThread().interrupt()
                } catch (failure: Exception) {
                    exit.completeExceptionally(failure)
                    if (continuation.isActive) continuation.resumeWithException(failure)
                } finally { process?.destroy(); process?.errorStream?.close() }
            }
        }
    }

    private fun directory(root: Path, relative: String): Path {
        require(root.isAbsolute && !Files.isSymbolicLink(root))
        val parts = Path.of(relative)
        require(!parts.isAbsolute && parts.none { it.toString() in setOf("..", ".aurora-transactions") })
        var path = root
        for (part in parts) { path = path.resolve(part); require(!Files.isSymbolicLink(path)) }
        require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        return path
    }

    companion object {
        private val workers = Executors.newCachedThreadPool { task -> Thread(task, "AuroraPtyWait").apply { isDaemon = true } }
    }
}
