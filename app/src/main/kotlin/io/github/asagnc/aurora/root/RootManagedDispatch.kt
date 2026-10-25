package io.github.asagnc.aurora.root

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.nio.file.Path

/** Pins installed code and snapshots until the process barrier, including cancellation before GO. */
internal class RootManagedDispatch(private val context: Context, private val identity: () -> LinuxRootIdentity) {
    private val files = RootManagedFiles(context)
    private val bundle = RootManagedBundle(context)
    private val runner = RootManagedProcessRunner(identity)
    private val recovery = RootManagedRecovery(context, identity)
    private val qualification = AtomicInteger()
    private data class Launch(val ticket: RootManagedRunTicket, val state: Path, val guard: RootManagedExecutable,
        val worker: RootManagedExecutable, val directory: ParcelFileDescriptor)

    suspend fun metadata(): FridaManagedPackage {
        val current = qualification.get()
        return bundle.metadata(current and 1 != 0, current and 2 != 0)
    }

    suspend fun captureMetadata(): RootCapturePackage = bundle.captureMetadata(qualification.get() and 4 != 0)

    suspend fun start(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>): String {
        ticket.validate()
        require(ticket.identity == identity() && ticket.appUid == context.applicationInfo.uid)
        require(ticket.appBuild == context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString())
        if (ticket.kind == "capture") {
            val config = RootCaptureMemory.read(ticket, snapshots)
            val probe = RootCaptureProbe.matches(ticket, config)
            require(ticket.action != "capture_probe" || probe)
            require(probe || qualification.get() and 4 != 0) { "Run the explicit loopback capture probe in this Root service first" }
            return execute(ticket, snapshots, probe)
        }
        if (ticket.bridge != null) require(ticket.bridge == bundle.bridgeSnapshot()) { "Java bridge differs from the installed immutable bundle" }
        val probe = FridaManagedProbe.matches(ticket, identity(), bundle.bridgeSnapshot())
        require(probe || (qualification.get() and (if (ticket.bridge == null) 1 else 2)) != 0) {
            "Run the matching explicit Root service self-probe before attaching user targets"
        }
        return execute(ticket, snapshots, probe)
    }

    private suspend fun execute(ticket: RootManagedRunTicket, snapshots: List<ParcelFileDescriptor>, probe: Boolean): String =
        bundle.guard().use { guard -> (if (ticket.kind == "capture") bundle.captureWorker() else bundle.worker()).use { worker ->
            require(worker.sha256 == ticket.workerSha256)
            val state = withContext(Dispatchers.IO) { files.state(ticket) }
            files.open(state).use { directory ->
                run(Launch(ticket, state, guard, worker, directory), snapshots, probe)
            }
        } }

    private suspend fun run(launch: Launch, snapshots: List<ParcelFileDescriptor>, probe: Boolean): String {
        try { RootScriptChannel.create(launch.ticket, snapshots).use { handoff(launch, it) } }
        catch (failure: Throwable) {
            // Structured concurrency joins the runner before recovery obtains the native lock.
            try { recovery.recover(launch.ticket) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
        val result = recovery.recover(launch.ticket)
        if (!probe) return result
        val decoded = WireJson.decodeFromString<RootManagedResult>(result)
        if (launch.ticket.kind == "capture") {
            val observed = decoded.successful && decoded.captureArtifact != null
            if (observed) qualification.updateAndGet { it or 4 }
            return WireJson.encodeToString(RootManagedResult.serializer(), decoded.copy(probeObserved = observed))
        }
        val observed = decoded.successful && runCatching {
            RootFridaProbeEvidence.observed(launch.directory, launch.ticket)
        }.getOrDefault(false)
        if (observed) {
            qualification.updateAndGet { it or (if (launch.ticket.bridge == null) 1 else 3) }
        }
        return WireJson.encodeToString(RootManagedResult.serializer(), decoded.copy(probeObserved = observed))
    }

    private suspend fun handoff(launch: Launch, channel: RootScriptChannel) = coroutineScope {
        val delivery = async { channel.handoff(launch.directory) }
        try { runner.start(launch.ticket, launch.state, launch.guard, launch.worker, channel); delivery.await() }
        finally { channel.close(); delivery.cancelAndJoin() }
    }
}
