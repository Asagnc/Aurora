package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootLinuxClient
import java.nio.file.Path
import kotlinx.coroutines.*

/** Same service identity/stop barrier as installation. A transport failure must
 * keep this stage so startup cannot race an older deletion with publication. */
internal class LinuxRootRemovalBatch(private val client: RootLinuxClient, private val stage: Path,
    private val manifest: LinuxRootfsManifest, private val appUid: Int) {
    suspend fun complete() {
        check(client.supportsRootImages)
        val ticket = LinuxPreparationTicket(stage.fileName.toString(), client.identity, manifest.image.sha256,
            appUid, protocol = 2, purpose = "root_removal")
        LinuxPreparationState.mark(stage, ticket)
        var stopped = false
        var failure: Throwable? = null
        try {
            val response = client.prepare(ticket.operation) { stopped = true }
            check(stopped) { "Root removal completion not acknowledged" }
            require(response.text("ownership") == ticket.purpose && response.text("generation") == manifest.generationId())
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally { withContext(NonCancellable + Dispatchers.IO) {
            if (stopped) try {
                LinuxPreparationState.settled(stage); removeSkillTree(stage)
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        } }
    }
}
