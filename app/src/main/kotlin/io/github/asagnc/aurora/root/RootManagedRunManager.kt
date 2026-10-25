package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.data.RootManagedRunRegistry
import io.github.asagnc.aurora.core.RootManagedRunTicket
import io.github.asagnc.aurora.core.RootManagedResult
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.FileAlreadyExistsException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only indexed pending intents are visited; no history scan, automatic reconnect, or injection replay. */
internal class RootManagedRunManager(private val app: AuroraApplication) {
    private val records = RootManagedRunRegistry(app.conversations)
    private val recovery = Mutex()
    private val cursors = linkedMapOf<String, String>()

    /** Caller must supply a ticket from the same qualified endpoint/version and retain its snapshots. */
    suspend fun execute(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>,
        client: RootManagedClient, toolRunId: String? = null): RootManagedResult = recovery.withLock {
        app.spaces.sharedResources()
        check(records.count(ticket.owner) == 0L) { "Recover this conversation's unconfirmed Root injection before another dispatch" }
        records.begin(ticket, toolRunId)
        var confirmed = false
        try {
            prepareArtifact(ticket)
            val result = client.start(ticket, snapshots)
            if (result.confirmed) withContext(NonCancellable) { records.finish(ticket, result); confirmed = true }
            result
        } finally {
            if (!confirmed) withContext(NonCancellable) {
                // Connection failure retains the exact intent; never auto-reconnect or replay.
                runCatching {
                    val result = client.recover(ticket)
                    if (result.confirmed) records.finish(ticket, result)
                }
            }
        }
    }

    private suspend fun prepareArtifact(ticket: RootManagedRunTicket) = withContext(Dispatchers.IO) {
        val root = app.spaces.commandRoot(ticket.owner)
        val runs = root.resolve("runs")
        try { Files.createDirectory(runs) } catch (_: FileAlreadyExistsException) { }
        require(Files.isDirectory(runs, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(runs))
        Files.createDirectory(runs.resolve(ticket.runId))
    }

    suspend fun recover(owner: String, discardCapture: Boolean = false): Int = recovery.withLock {
        val pending = records.pending(owner, limit = 4, after = cursors[owner].orEmpty())
            .ifEmpty { records.pending(owner, limit = 4) }
        if (pending.isEmpty()) return@withLock 0
        val client = app.root.managedClient ?: return@withLock records.count(owner).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        app.spaces.sharedResources()
        for (ticket in pending) {
            try {
                val result = client.recover(ticket, discardCapture && ticket.kind == "capture")
                if (result.confirmed) records.finish(ticket, result)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Keep the immutable intent for an explicit later recovery. */ }
            cursors.remove(owner)
            cursors[owner] = ticket.runId
            if (cursors.size > 64) cursors.remove(cursors.keys.first())
        }
        records.count(owner).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    suspend fun pendingCount(owner: String): Long = records.count(owner)
}
