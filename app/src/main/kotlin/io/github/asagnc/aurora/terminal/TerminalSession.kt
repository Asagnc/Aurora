package io.github.asagnc.aurora.terminal

import io.github.asagnc.aurora.core.TerminalBytes
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class TerminalSession(val initial: TerminalRecord, val request: PtyRequest,
    private val records: TerminalRecords, private val changed: () -> Unit) {
    val bytes = TerminalBytes()
    val version = MutableStateFlow(0L)
    val ready = CompletableDeferred<Unit>()
    val opened = CompletableDeferred<Unit>()
    val replies = Channel<ByteArray>(16)
    val screen = TerminalScreen(request) { reply ->
        if (replies.trySend(reply).isFailure) {
            failure = "Terminal reply queue exceeded its budget"; endpoint?.stop()
        }
    }
    @Volatile var record = initial
        private set
    @Volatile var endpoint: PtyEndpoint? = null
        private set
    @Volatile private var failure: String? = null
    lateinit var job: Job

    suspend fun run(open: suspend () -> PtyEndpoint) {
        var final = "interrupted"
        var code: Int? = null
        try {
            records.directory(initial.owner, initial.id, true)
            records.save(record)
            withTimeout(10_000) { ready.await() }
            endpoint = open()
            record = record.copy(state = "running"); records.save(record); announce()
            opened.complete(Unit)
            coroutineScope {
                val stopOnCancel = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { endpoint?.stop() }
                }
                val responses = launch { for (reply in replies) endpoint?.write(reply) }
                try {
                    drain(requireNotNull(endpoint))
                    val outcome = requireNotNull(endpoint).exit.await()
                    code = outcome.exitCode
                    final = if (failure == null) outcome.state else "failed"
                    if (failure == null) failure = outcome.reason
                } finally { responses.cancelAndJoin(); stopOnCancel.cancelAndJoin() }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (caught: Exception) { failure = caught.javaClass.simpleName; final = "failed" }
        finally {
            withContext(NonCancellable + Dispatchers.IO) {
                endpoint?.let { transport -> transport.stop(); runCatching { transport.exit.await() }; transport.closeStreams() }
                replies.close()
                record = record.copy(state = final, exitCode = code, reason = failure ?: if (final == "interrupted") "Session stopped; input not replayed" else null)
                runCatching { records.save(record) }
                if (!opened.isCompleted) opened.completeExceptionally(IllegalStateException(record.reason ?: "Terminal startup failed"))
                announce()
            }
        }
    }

    private suspend fun drain(transport: PtyEndpoint) {
        val file = records.directory(initial.owner, initial.id).resolve("output.bin")
        Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { log ->
            val buffer = ByteArray(4096)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = runInterruptible(Dispatchers.IO) { transport.output.read(buffer) }
                if (count < 0) break
                if (count == 0) continue
                val stored = minOf(count.toLong(), (LOG_LIMIT - record.stored).coerceAtLeast(0)).toInt()
                if (stored > 0) log.write(buffer, 0, stored)
                bytes.append(buffer, count)
                withContext(Dispatchers.Default) { screen.append(buffer, count) }
                record = record.copy(bytes = record.bytes + count, stored = record.stored + stored)
                announce()
            }
        }
    }

    suspend fun write(data: ByteArray) {
        check(record.state == "running") { "Terminal is not running" }
        withTimeout(10_000) { requireNotNull(endpoint).write(data) }
    }

    suspend fun resize(columns: Int, rows: Int) {
        check(record.state == "running") { "Terminal is not running" }
        require(columns in 2..300 && rows in 2..200)
        requireNotNull(endpoint).resize(columns, rows)
        withContext(Dispatchers.Default) { screen.resize(columns, rows) }
        announce()
    }

    private fun announce() { version.update { it + 1 }; changed() }
    companion object { const val LOG_LIMIT = 16L * 1024 * 1024 }
}
