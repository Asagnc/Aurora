package io.github.asagnc.aurora.terminal

import android.content.Intent
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.AgentTaskService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Application-owned PTYs, independent of visible pages and individual model turns. */
internal class TerminalManager(private val app: AuroraApplication) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val admission = Mutex()
    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val records = TerminalRecords(app.filesDir.toPath())
    private val process = PtyProcess(app)
    private val awake = io.github.asagnc.aurora.runtime.CommandWakeLock(app, "TerminalWork")
    val version = MutableStateFlow(0L)
    val available get() = process.available
    private fun active(session: TerminalSession) = session.job.isActive && session.record.state in setOf("starting", "running")
    val hasActive get() = sessions.values.any(::active)
    val activeOwner get() = sessions.values.firstOrNull(::active)?.initial?.owner
    internal fun session(owner: String, id: String) = sessions[id]?.takeIf { it.initial.owner == owner }
    internal fun current(owner: String) = sessions.values.filter { it.initial.owner == owner }.map { it.record }
    internal fun serviceReady(id: String): Boolean = sessions[id]?.takeIf { it.job.isActive }?.let { it.ready.complete(Unit); true } ?: false
    internal fun serviceFailed(id: String, failure: Exception) { sessions[id]?.ready?.completeExceptionally(failure) }
    // Cancellation guards perform descriptor/process shutdown on IO, not the service's main thread.
    internal fun stopAll() { sessions.values.forEach { it.job.cancel() } }
    internal suspend fun forget(owner: String) {
        val owned = sessions.values.filter { it.initial.owner == owner }
        owned.forEach { it.endpoint?.stop(); it.job.cancel() }
        owned.map { it.job }.joinAll()
        owned.forEach { sessions.remove(it.initial.id, it) }
        version.update { it + 1 }
    }
    suspend fun recover() = withContext(Dispatchers.IO) { records.recover() }

    suspend fun start(owner: String, request: PtyRequest, root: Boolean = false): TerminalRecord =
        startBackend(owner, request, if (root) "android-root" else "android-native")

    /** Linux shares terminal transport/records; its durable lease controls guest cleanup. */
    internal suspend fun startLinux(owner: String, request: PtyRequest = PtyRequest(argv = listOf("/bin/sh"), timeoutMs = 270_000),
        generation: String? = null): TerminalRecord = startBackend(owner, request, "linux-root", generation)

    private suspend fun startBackend(owner: String, supplied: PtyRequest, backend: String, generation: String? = null): TerminalRecord = withContext(Dispatchers.IO) {
        val request = supplied.copy(argv = supplied.argv.toList(), environment = supplied.environment.toMap())
        request.validate()
        if (backend == "linux-root") {
            require(request.directory.isEmpty() && request.environment.isEmpty() && request.timeoutMs <= 270_000)
            check(app.root.linuxTerminalClient != null) { "Reconnect a current Root service for Linux PTY" }
        }
        val session = admission.withLock {
            check(sessions.values.count { it.job.isActive } < 8) { "Eight active terminals is the global budget" }
            check(sessions.values.count { it.initial.owner == owner && it.job.isActive } < 4) { "Four active terminals per conversation" }
            val id = UUID.randomUUID().toString()
            val remote = if (backend == "android-root") requireNotNull(app.root.terminalClient) { "Root is not connected" } else null
            val initial = TerminalRecord(id, owner, backend, "starting", System.currentTimeMillis())
            val next = TerminalSession(initial, request, records) { version.update { it + 1 } }
            next.job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    app.tasks.run(owner) {
                        val directory = app.spaces.commandRoot(owner)
                        var lease: io.github.asagnc.aurora.runtime.CommandWakeLock.Lease? = null
                        try {
                            suspend fun consume(open: suspend () -> PtyEndpoint) = next.run {
                                if (request.awakeMs > 0) lease = awake.acquire(request.awakeMs)
                                open()
                            }
                            if (backend == "linux-root") app.linux.runs.terminal(owner, id, request, ::consume, generation)
                            else consume { remote?.open(id, owner, request) ?: process.open(directory, request) }
                        } finally { lease?.let { if (it.held) it.close() } }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) { next.failStart(failure) }
                finally {
                    if (!next.opened.isCompleted) next.opened.completeExceptionally(IllegalStateException("Conversation closing"))
                    version.update { it + 1 }
                }
            }
            // At most twelve resident emulators, including up to eight active sessions.
            sessions.values.filter { !it.job.isActive }.sortedBy { it.initial.started }
                .take((sessions.size - 11).coerceAtLeast(0)).forEach { sessions.remove(it.initial.id, it) }
            sessions[id] = next
            next.job.invokeOnCompletion {
                scope.launch(Dispatchers.Main.immediate) {
                    AgentTaskService.refresh(app)
                }
            }
            next.job.start()
            next
        }
        try {
            withContext(Dispatchers.Main.immediate) {
                try { app.startForegroundService(Intent(app, AgentTaskService::class.java).putExtra("terminal", session.initial.id)) }
                catch (failure: Exception) { session.ready.completeExceptionally(failure) }
            }
            session.opened.await()
            session.record
        } catch (failure: Throwable) {
            session.endpoint?.stop(); session.job.cancel()
            withContext(NonCancellable) { session.job.join() }
            throw failure
        }
    }

    suspend fun list(owner: String): List<TerminalRecord> = withContext(Dispatchers.IO) {
        (records.list(owner).associateBy { it.id } + sessions.values.filter { it.initial.owner == owner }
            .associate { it.initial.id to it.record }).values.sortedByDescending { it.started }
    }

    suspend fun stop(owner: String, id: String): TerminalRecord = withContext(Dispatchers.IO) {
        session(owner, id)?.let { session -> session.endpoint?.stop(); session.job.cancelAndJoin(); session.record }
            ?: records.load(owner, id)
    }

    suspend fun write(owner: String, id: String, text: String) {
        val bytes = text.toByteArray()
        require(bytes.size in 1..4096) { "Input must be 1..4096 UTF-8 bytes; split larger input explicitly" }
        requireNotNull(session(owner, id)) { "Unknown live terminal for this conversation" }.write(bytes)
    }

    suspend fun resize(owner: String, id: String, columns: Int, rows: Int) =
        requireNotNull(session(owner, id)) { "Unknown live terminal for this conversation" }.resize(columns, rows)

    suspend fun read(owner: String, id: String, request: TerminalRead = TerminalRead()): JsonObject = withContext(Dispatchers.IO) {
        val (cursor, limit, waitMs, view, includeBase64) = request
        require(waitMs in 0..10_000 && limit in 1..65536 && cursor >= 0)
        require(view in setOf("output", "screen", "both"))
        val live = session(owner, id)
        if (live != null && live.bytes.sequence == cursor && live.job.isActive && waitMs > 0) {
            withTimeoutOrNull(waitMs) { live.version.first { live.bytes.sequence != cursor || !active(live) } }
        }
        val record = live?.record ?: records.load(owner, id)
        val slice = live?.bytes?.read(cursor, limit) ?: records.read(record, cursor, limit)
        require(live != null || view != "screen") { "Historical session has only its raw persisted log, not a recovered VT screen" }
        val screen = if (view == "output") null else live?.let { withContext(Dispatchers.Default) { it.screen.snapshot() } }
        JsonObject(record.json() + mapOf("start" to JsonPrimitive(slice.start), "next" to JsonPrimitive(slice.next),
            "end" to JsonPrimitive(slice.end), "cursorDropped" to JsonPrimitive(slice.dropped),
            "source" to string(if (live != null) "live-ring" else "persisted-log"),
            "text" to if (view == "screen") JsonNull else string(String(slice.bytes, Charsets.UTF_8)),
            "base64" to if (includeBase64) string(java.util.Base64.getEncoder().encodeToString(slice.bytes)) else JsonNull,
            "screen" to (screen?.let(::string) ?: JsonNull), "encoding" to string("raw UTF-8 preview; use base64 for byte-exact split sequences")))
    }
}

internal data class TerminalRead(val cursor: Long = 0, val limit: Int = 8192, val waitMs: Long = 0,
    val view: String = "output", val includeBase64: Boolean = false)
