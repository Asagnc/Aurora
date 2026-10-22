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
    suspend fun execute(owner: String, argv: List<String>, timeoutMs: Long = 60_000,
        runId: String = UUID.randomUUID().toString()): JsonObject {
        val client = requireNotNull(app.root.linuxRunClient) { "Reconnect a current Root service for Linux execution" }
        val ticket = reserve(owner, runId, argv, timeoutMs)
        try {
            app.spaces.commandRoot(owner)
            currentCoroutineContext().ensureActive()
            val result = client.invoke(ticket, recover = false)
            if (ticket.confirms(result)) withContext(NonCancellable) { records.finish(ticket, result) }
            return result
        } finally {
            // A transport failure or cancellation retains the durable intent.
            // Explicit recovery joins/proves the old dispatcher dead; no command replay.
            recoverPending(ticket, client)
        }
    }
    suspend fun terminal(owner: String, id: String, request: PtyRequest,
        run: suspend (suspend () -> PtyEndpoint) -> Unit) {
        request.validate()
        require(request.directory.isEmpty() && request.environment.isEmpty())
        val terminal = requireNotNull(app.root.linuxTerminalClient) { "Reconnect a current Root service for Linux PTY" }
        val recovery = requireNotNull(app.root.linuxRunClient)
        val ticket = reserve(owner, id, request.argv, request.timeoutMs)
        try {
            run {
                currentCoroutineContext().ensureActive()
                terminal.openLinux(ticket, request) { proof -> withContext(NonCancellable) { records.finish(ticket, proof) } }
            }
        } finally { recoverPending(ticket, recovery) }
    }
    private suspend fun reserve(owner: String, runId: String, argv: List<String>, timeoutMs: Long): LinuxRunTicket {
        val identity = requireNotNull(app.root.linuxClient).identity
        return withContext(NonCancellable) { environments.catalog.withLock {
            val generation = requireNotNull(environments.registry.current()) { "Install a Root Linux generation first" }
            LinuxRunTicket(runId, owner, generation, identity, Process.myUid(), argv.toList(), timeoutMs).also { records.begin(it) }
        } }
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
    suspend fun recover(): JsonObject {
        val client = requireNotNull(app.root.linuxRunClient) { "Connect Root to recover Linux runs" }
        var recovered = 0
        var retained = 0
        records.pending().forEach { ticket ->
            try {
                val result = client.invoke(ticket, recover = true)
                if (ticket.confirms(result)) { records.finish(ticket, result); recovered++ } else retained++
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { retained++ }
        }
        return objectOf("recovered" to JsonPrimitive(recovered), "retained" to JsonPrimitive(retained))
    }
    suspend fun pendingCount() = records.count().toInt()
}
