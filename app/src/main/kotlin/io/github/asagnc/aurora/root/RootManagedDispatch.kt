package io.github.asagnc.aurora.root

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*

/** Pins installed code and snapshots until the process barrier, including cancellation before GO. */
internal class RootManagedDispatch(private val context: Context, private val identity: () -> LinuxRootIdentity) {
    private val files = RootManagedFiles(context)
    private val bundle = RootManagedBundle(context)
    private val runner = RootManagedProcessRunner(identity)
    private val recovery = RootManagedRecovery(context, identity)

    suspend fun start(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>): String {
        ticket.validate()
        require(ticket.identity == identity() && ticket.appUid == context.applicationInfo.uid)
        require(ticket.appBuild == context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString())
        // Bridge publication/qualification is independent of the transport accepting a second FD.
        require(ticket.bridge == null) { "Packaged Java bridge is not qualified" }
        return bundle.guard().use { guard -> bundle.worker().use { worker ->
            require(worker.sha256 == ticket.workerSha256)
            val state = withContext(Dispatchers.IO) { files.state(ticket) }
            files.open(state).use { directory ->
                try {
                    RootScriptChannel.create(ticket, snapshots).use { channel ->
                        coroutineScope {
                            val handoff = async { channel.handoff(directory) }
                            try { runner.start(ticket, state, guard, worker, channel); handoff.await() }
                            finally { channel.close(); handoff.cancelAndJoin() }
                        }
                    }
                } catch (failure: Throwable) {
                    // Even cancelled handoff joins the runner before recovery obtains the native lock.
                    try { recovery.recover(ticket) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                    throw failure
                }
                recovery.recover(ticket)
            }
        } }
    }
}
