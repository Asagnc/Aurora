package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootLinuxClient
import java.nio.file.Path

internal class LinuxRootOwnershipBatch(private val client: RootLinuxClient, private val stage: Path,
    private val manifest: LinuxRootfsManifest, private val appUid: Int) {
    suspend fun complete() {
        check(client.supportsRootImages)
        val ticket = LinuxPreparationTicket(stage.fileName.toString(), client.identity, manifest.image.sha256,
            appUid, protocol = 2, purpose = "root_ownership")
        LinuxPreparationState.mark(stage, ticket)
        var stopped = false
        val result = client.prepare(ticket.operation) { stopped = true }
        check(stopped) { "Root ownership completion not acknowledged" }
        require(result.text("image") == ticket.image && result.text("ownership") == ticket.purpose &&
            result.text("generation") == manifest.generationId() && result.number("entries") == manifest.entries.toLong() &&
            result.number("bytes") == manifest.expandedBytes)
        // Even an acknowledged worker can leave Root-owned files. Keep the
        // ticket until publication moves them away, or Root removes the stage.
    }
}
