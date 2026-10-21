package io.github.asagnc.aurora.runtime

import android.content.Context
import io.github.asagnc.aurora.core.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

data class CommandRequest(val argv: List<String>, val directory: String = "", val timeoutMs: Long = 60_000,
    val outputLimit: Int = 8192, val environment: Map<String, String> = emptyMap())

/** Foreground command scope, not a sandbox for programs running under Aurora's UID. */
class NativeCommandRunner(context: Context, private val artifactOwner: Int = android.os.Process.myUid()) {
    private val executable = File(context.applicationInfo.nativeLibraryDir, "libaurora_runner.so")
    private val permits = Semaphore(2)
    private val awake = CommandWakeLock(context, "NativeCommand")
    val available get() = executable.isFile && executable.canExecute()

    suspend fun execute(root: Path, request: CommandRequest, artifactId: String = UUID.randomUUID().toString()): JsonObject = permits.withPermit {
        runInterruptible(Dispatchers.IO) {
            validate(request)
            require(UUID.fromString(artifactId).toString() == artifactId)
            awake.acquire(request.timeoutMs + 1_000).use { executeBlocking(root, request, artifactId) }
        }
    }

    private fun executeBlocking(root: Path, request: CommandRequest, artifactId: String): JsonObject {
        check(available) { "Packaged native runner unavailable for this ABI" }
        val cwd = resolveDirectory(root, request.directory)
        val runs = root.resolve("runs")
        require(!Files.isSymbolicLink(runs))
        Files.createDirectories(runs)
        restoreOwner(runs)
        val directory = Files.createDirectory(runs.resolve(artifactId))
        restoreOwner(directory)
        val log = Files.createFile(directory.resolve("supervisor.log"))
        restoreOwner(log)
        val process = ProcessBuilder(listOf(executable.absolutePath, directory.toString(), "--") + request.argv)
            .directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve("supervisor.log").toFile())
            .apply { environment().apply { clear(); put("PATH", "/system/bin:/system/xbin"); put("HOME", root.toString()); put("TMPDIR", cwd.toString()); putAll(request.environment) } }
            .start()
        try {
            process.outputStream.close()
            val finished = process.waitFor(request.timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) stop(process)
            return result(root, directory, request.outputLimit, !finished, process.exitValue())
        } finally {
            // Conversation deletion waits for this cleanup through its OwnedTasks lease.
            stop(process)
            process.inputStream.close(); process.errorStream.close()
        }
    }

    private fun restoreOwner(path: Path) {
        if (android.os.Process.myUid() != 0) return
        val fd = android.system.Os.open(path.toString(), android.system.OsConstants.O_RDONLY or
            android.system.OsConstants.O_NOFOLLOW or android.system.OsConstants.O_CLOEXEC, 0)
        try { android.system.Os.fchown(fd, artifactOwner, artifactOwner) } finally { android.system.Os.close(fd) }
    }

    private fun stop(process: Process) {
        // Keep the creating OS thread alive until the native supervisor exits (PDEATHSIG is thread-scoped).
        var interrupted = Thread.interrupted()
        try {
            if (process.isAlive) process.destroy()
            while (true) {
                try { process.waitFor(); break }
                catch (_: InterruptedException) { interrupted = true }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }

    private fun validate(request: CommandRequest) {
        require(request.argv.size in 1..128 && request.argv.all { it.length <= 65536 && '\u0000' !in it })
        require(request.argv.first().isNotBlank() && request.argv.sumOf { it.toByteArray().size.toLong() } <= 262144)
        require(request.timeoutMs in 1..300_000 && request.outputLimit in 1024..65536)
        require(request.environment.size <= 64 && request.environment.all { (key, value) ->
            key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && value.length <= 8192 && '\u0000' !in value
        })
        require(request.environment.entries.sumOf { (key, value) -> key.toByteArray().size + value.toByteArray().size + 2 } <= 65536)
    }

    private fun resolveDirectory(root: Path, relative: String): Path {
        require(root.isAbsolute && !Files.isSymbolicLink(root))
        val path = Path.of(relative)
        require(!path.isAbsolute && path.none { it.toString() in setOf("..", ".aurora-transactions") })
        var target = root
        for (part in path) {
            target = target.resolve(part)
            require(!Files.isSymbolicLink(target)) { "Working directory cannot follow a link" }
        }
        require(Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
        return target
    }

    private fun result(root: Path, directory: Path, limit: Int, timedOut: Boolean, supervisorExit: Int): JsonObject {
        return runCatching { readResult(root, directory, limit, timedOut, supervisorExit) }.getOrElse {
            objectOf("error" to string("command_outcome_uncertain"), "artifactDirectory" to string(root.relativize(directory).toString()),
                "reason" to string(it.javaClass.simpleName), "timedOut" to JsonPrimitive(timedOut))
        }
    }

    private fun readResult(root: Path, directory: Path, limit: Int, timedOut: Boolean, supervisorExit: Int): JsonObject {
        val metadata = directory.resolve("result.json")
        if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS) || Files.size(metadata) > 4096)
            return objectOf("error" to string("command_outcome_uncertain"), "artifactDirectory" to string(root.relativize(directory).toString()),
                "timedOut" to JsonPrimitive(timedOut))
        val record = WireJson.parseToJsonElement(String(Files.readAllBytes(metadata), Charsets.UTF_8)).jsonObject
        require(record.number("protocol") == 1L)
        val code = requireNotNull(record.number("exitCode"))
        val stopped = record["stopped"]?.jsonPrimitive?.boolean == true
        require(supervisorExit.toLong() == if (stopped) 124L else code)
        val complete = record["captureComplete"]?.jsonPrimitive?.boolean == true
        return JsonObject(linkedMapOf<String, JsonElement>().apply {
            if (timedOut || stopped || code != 0L || !complete) put("error", string(when {
                timedOut -> "command_timeout"; stopped -> "command_stopped"; !complete -> "capture_incomplete"; else -> "command_exit"
            }))
            put("backend", string(if (android.os.Process.myUid() == 0) "android-root" else "android-native")); put("uid", JsonPrimitive(android.os.Process.myUid()))
            put("exitCode", JsonPrimitive(code)); put("timedOut", JsonPrimitive(timedOut)); put("stopped", JsonPrimitive(stopped)); put("captureComplete", JsonPrimitive(complete))
            put("stdout", output(root, directory, "stdout", record, limit)); put("stderr", output(root, directory, "stderr", record, limit))
        })
    }

    private fun output(root: Path, directory: Path, name: String, record: JsonObject, limit: Int): JsonObject {
        val file = directory.resolve("$name.bin")
        val bytes = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(limit) }
        val total = requireNotNull(record.number("${name}Bytes"))
        val stored = requireNotNull(record.number("${name}Stored"))
        val text = decodeText(bytes, stored > bytes.size)
        return objectOf("text" to (text?.let(::string) ?: JsonNull), "bytes" to JsonPrimitive(total), "storedBytes" to JsonPrimitive(stored),
            "previewTruncated" to JsonPrimitive(total > bytes.size), "artifactTruncated" to JsonPrimitive(total > stored),
            "artifact" to string(root.relativize(file).toString()), "encoding" to string(if (text == null) "binary-or-partial-UTF8" else "UTF-8"))
    }

    private fun decodeText(bytes: ByteArray, truncated: Boolean): String? {
        if (bytes.any { val value = it.toInt(); value in 0..31 && value != 9 && value != 10 && value != 13 && value != 27 }) return null
        for (tail in 0..if (truncated) minOf(3, bytes.size) else 0) {
            val text = runCatching { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, 0, bytes.size - tail)).toString() }.getOrNull()
            if (text != null) return text
        }
        return null
    }
}
