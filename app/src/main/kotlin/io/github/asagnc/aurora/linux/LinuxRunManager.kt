package io.github.asagnc.aurora.linux

import android.os.Process
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.LinuxRunRegistry
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

/** Internal runtime; Agent exposure waits for device qualification and PTY. */
internal class LinuxRunManager(private val app: AuroraApplication, private val environments: LinuxEnvironmentManager) {
    private val records = LinuxRunRegistry(app.conversations)
    suspend fun execute(owner: String, argv: List<String>, timeoutMs: Long = 60_000): JsonObject {
        val client = requireNotNull(app.root.linuxRunClient) { "Reconnect a current Root service for Linux execution" }
        val identity = requireNotNull(app.root.linuxClient).identity
        val ticket = withContext(NonCancellable) { environments.catalog.withLock {
            val id = requireNotNull(environments.registry.current()) { "Install a Root Linux generation first" }
            LinuxRunTicket(UUID.randomUUID().toString(), owner, id, identity, Process.myUid(), argv.toList(), timeoutMs).also { records.begin(it) }
        } }
        try {
            app.spaces.commandRoot(owner)
            currentCoroutineContext().ensureActive()
            val result = client.invoke(ticket, recover = false)
            if (ticket.confirms(result)) withContext(NonCancellable) { records.finish(ticket, result) }
            return result
        } finally {
            // A transport failure or cancellation retains the durable intent.
            // Explicit recovery joins/proves the old dispatcher dead; no command replay.
            withContext(NonCancellable) { runCatching {
                if (records.contains(ticket.runId)) {
                    val response = client.invoke(ticket, recover = true)
                    if (ticket.confirms(response)) records.finish(ticket, response)
                }
            } }
        }
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
