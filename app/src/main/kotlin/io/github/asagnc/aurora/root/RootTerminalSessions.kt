package io.github.asagnc.aurora.root

import android.content.Context
import android.os.*
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.terminal.*
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.*

/** Binder is control only; a Unix socket carries framed input and raw merged output. */
internal class RootTerminalSessions(context: Context) {
    private val process = PtyProcess(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class Bridge(val job: Job, val activate: CompletableDeferred<Unit>, val shutdown: () -> Unit)
    private val sessions = mutableMapOf<String, Bridge>()
    private var closed = false

    @Synchronized fun open(id: String, root: Path, request: PtyRequest, callback: IRootResult,
        lifecycle: RootTerminalLifecycle? = null): Bundle {
        require(UUID.fromString(id).toString() == id)
        check(!closed && id !in sessions && sessions.size < 8) { "Root terminal session closed, duplicate or full" }
        val sockets = ParcelFileDescriptor.createSocketPair()
        val server = sockets[0]; val client = sockets[1]
        val activate = CompletableDeferred<Unit>()
        val binder = callback.asBinder()
        var endpoint: PtyEndpoint? = null
        val shutdown = {
            endpoint?.stop()
            runCatching { Os.shutdown(server.fileDescriptor, OsConstants.SHUT_RDWR) }
            Unit
        }
        lateinit var job: Job
        val death = IBinder.DeathRecipient { shutdown(); job.cancel() }
        job = scope.launch(start = CoroutineStart.LAZY) {
            var outcome = PtyOutcome(null, "interrupted", "Root terminal outcome uncertain")
            try {
                // Client acknowledges receipt before any FD may be closed by a fast child exit.
                withTimeout(10_000) { activate.await() }
                endpoint = lifecycle?.open() ?: process.open(root, request)
                val child = requireNotNull(endpoint)
                coroutineScope {
                    val cancel = launch(start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { shutdown() }
                    }
                    val input = launch {
                        try {
                            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(server.fileDescriptor)).use { source ->
                                val header = ByteArray(3)
                                while (true) {
                                    runInterruptible(Dispatchers.IO) { java.io.DataInputStream(source).readFully(header) }
                                    val count = (header[1].toInt() and 255) or ((header[2].toInt() and 255) shl 8)
                                    require(count <= 4096)
                                    val payload = ByteArray(count)
                                    runInterruptible(Dispatchers.IO) { java.io.DataInputStream(source).readFully(payload) }
                                    when (header[0].toInt()) {
                                        1 -> child.write(payload)
                                        2 -> {
                                            require(count == 4)
                                            fun value(at: Int) = (payload[at].toInt() and 255) or ((payload[at + 1].toInt() and 255) shl 8)
                                            child.resize(value(2), value(0))
                                        }
                                        else -> error("Invalid PTY frame")
                                    }
                                }
                            }
                        } finally { child.stop() }
                    }
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(server.fileDescriptor)).use { target ->
                            runInterruptible(Dispatchers.IO) { child.output.copyTo(target, 16384) }
                        }
                        outcome = child.exit.await()
                    } finally { shutdown(); input.cancelAndJoin(); cancel.cancelAndJoin() }
                }
            } catch (_: Exception) { /* -1 is explicitly an uncertain outcome, never a command success. */ }
            finally {
                withContext(NonCancellable) {
                    shutdown()
                    endpoint?.let { runCatching { it.exit.await() }; it.closeStreams() }
                    runCatching { server.close() }; runCatching { client.close() }
                    val result = runCatching { lifecycle?.finish(outcome) ?: outcome.wire() }
                        .getOrElse { PtyOutcome(null, "failed", "Linux cleanup unconfirmed; run lease retained").wire() }
                    runCatching { callback.complete(result) }
                    runCatching { binder.unlinkToDeath(death, 0) }
                    synchronized(this@RootTerminalSessions) { sessions.remove(id) }
                }
            }
        }
        sessions[id] = Bridge(job, activate, shutdown)
        try { binder.linkToDeath(death, 0); job.start() }
        catch (failure: Exception) { shutdown(); job.cancel(); server.close(); client.close(); sessions.remove(id); throw failure }
        return Bundle().apply { putInt("protocol", 1); putParcelable("socket", client) }
    }

    @Synchronized fun activate(id: String) { requireNotNull(sessions[id]).activate.complete(Unit) }
    suspend fun stop(id: String) {
        val bridge = synchronized(this) { sessions[id] } ?: return
        bridge.shutdown(); bridge.job.cancelAndJoin()
    }
    suspend fun close() {
        val pending = synchronized(this) { closed = true; sessions.values.toList() }
        pending.forEach { it.shutdown(); it.job.cancel() }
        pending.map { it.job }.joinAll(); scope.cancel()
    }
}

internal interface RootTerminalLifecycle {
    suspend fun open(): PtyEndpoint
    suspend fun finish(outcome: PtyOutcome): String
}
