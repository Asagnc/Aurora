package io.github.asagnc.aurora.linux

import android.os.Process
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.LinuxRunRegistry
import io.github.asagnc.aurora.root.RootLinuxRunClient
import io.github.asagnc.aurora.terminal.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Durable execution intent precedes dispatch; only exact stopped proof releases its lease. */
internal class LinuxRunManager(private val app: AuroraApplication, private val environments: LinuxEnvironmentManager) {
    private val records = LinuxRunRegistry(app.conversations)
    private data class Dispatch(val owner: String, val argv: List<String>, val timeoutMs: Long,
        val runId: String, val generation: String?, val toolBundle: String? = null)
    suspend fun execute(owner: String, argv: List<String>, timeoutMs: Long = 60_000,
        runId: String = UUID.randomUUID().toString(), generation: String? = null): JsonObject =
        dispatch(Dispatch(owner, argv.toList(), timeoutMs, runId, generation))

    internal suspend fun executeTools(owner: String, argv: List<String>, bundle: String,
        timeoutMs: Long = 60_000, runId: String = UUID.randomUUID().toString()): JsonObject =
        dispatch(Dispatch(owner, argv.toList(), timeoutMs, runId, null, bundle))

    private suspend fun dispatch(request: Dispatch): JsonObject {
        val connection = requireNotNull(app.root.linuxExecution) { "Reconnect a current Root service for Linux execution" }
        val client = connection.run
        val ticket = reserve(request, connection.identity)
        try {
            app.spaces.commandRoot(request.owner)
            currentCoroutineContext().ensureActive()
            val result = client.invoke(ticket, recover = false)
            if (ticket.confirms(result)) withContext(NonCancellable) { records.finish(ticket, result) }
            return result
        } finally {
            // A transport failure or cancellation retains the durable intent until exact exit proof.
            recoverPending(ticket, client)
        }
    }
    suspend fun terminal(owner: String, id: String, request: PtyRequest,
        run: suspend (suspend () -> PtyEndpoint) -> Unit, generation: String? = null) {
        request.validate()
        require(request.directory.isEmpty() && request.environment.isEmpty())
        val connection = requireNotNull(app.root.linuxExecution) { "Reconnect a current Root service for Linux PTY" }
        val terminal = requireNotNull(connection.terminal) { "Reconnect a current Root service for Linux PTY" }
        val recovery = connection.run
        val ticket = reserve(Dispatch(owner, request.argv.toList(), request.timeoutMs, id, generation), connection.identity)
        try {
            run {
                currentCoroutineContext().ensureActive()
                terminal.openLinux(ticket, request) { proof -> withContext(NonCancellable) { records.finish(ticket, proof) } }
            }
        } finally { recoverPending(ticket, recovery) }
    }
    // Dispatch parameters are explicit; the identity must come from the same captured connection as its clients.
    private suspend fun reserve(request: Dispatch, identity: LinuxRootIdentity): LinuxRunTicket = withContext(NonCancellable) {
        val record: suspend () -> LinuxRunTicket = { environments.catalog.withLock {
            val generation = request.generation ?: requireNotNull(environments.registry.current()) { "Install a Root Linux generation first" }
            LinuxRunTicket(request.runId, request.owner, generation, identity, Process.myUid(), request.argv,
                request.timeoutMs, toolBundle = request.toolBundle).also { records.begin(it) }
        } }
        request.toolBundle?.let { app.sharedToolchain.reserve(it, record) } ?: record()
    }
    private suspend fun recoverPending(ticket: LinuxRunTicket, client: RootLinuxRunClient) = withContext(NonCancellable) {
        runCatching {
            if (records.contains(ticket.runId)) {
                val response = client.invoke(ticket, recover = true)
                if (ticket.confirms(response)) records.finish(ticket, response)
            }
        }
        Unit
    }
    suspend fun recover(owner: String? = null): JsonObject {
        val client = requireNotNull(app.root.linuxRunClient) { "Connect Root to recover Linux runs" }
        var recovered = 0
        var retained = 0
        records.pending(owner).forEach { ticket ->
            try {
                val result = client.invoke(ticket, recover = true)
                if (ticket.confirms(result)) { records.finish(ticket, result); recovered++ } else retained++
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { retained++ }
        }
        return objectOf("recovered" to JsonPrimitive(recovered), "retained" to JsonPrimitive(retained))
    }
    suspend fun usesToolBundle(id: String) = records.usesToolBundle(id)
    suspend fun pendingCount(owner: String? = null) = records.count(owner).toInt()
}
