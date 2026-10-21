package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootLinuxClient
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

/** Called on the extraction IO thread, after regular files and before symlinks. */
internal class LinuxRootLinkBatch(private val client: RootLinuxClient, private val stage: Path,
    private val ticket: LinuxPreparationTicket, private val manifest: LinuxRootfsManifest) {
    fun hooks() = RootfsExtractionHooks(hardlinks = {
        LinuxPreparationState.mark(stage, ticket)
        // runInterruptible interrupts this thread on cancellation. runBlocking
        // then cancels its Root client and waits for that client's cleanup.
        runBlocking { finish() }
    }, cleanup = { target ->
        if (!LinuxPreparationState.pending(stage)) removeSkillTree(target)
    })

    private suspend fun finish() {
        var stopped = false
        var failure: Throwable? = null
        try {
            val response = client.prepare(ticket.operation) { stopped = true }
            check(stopped) { "Root preparation completion not acknowledged" }
            require(response.text("image") == ticket.image && response.number("entries") == manifest.entries.toLong() &&
                response.number("bytes") == manifest.expandedBytes)
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally { if (stopped) {
            try { LinuxPreparationState.settled(stage) }
            catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        } }
    }
}
