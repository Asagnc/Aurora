package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*
import java.io.File
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.encodeToString

/** Direct native coordinator launcher. Never wraps Frida in the ordinary command runner.
 * Caller retains task/version leases, performs the READY FD handoff, and reconciles durable records.
 * A forced or absent exit requires recover; no transport outcome is a script cleanup proof. */
internal class RootManagedProcessRunner(private val identity: () -> LinuxRootIdentity) {
    suspend fun start(ticket: RootManagedRunTicket, state: Path, guard: RootManagedExecutable,
        worker: RootManagedExecutable, channel: RootScriptChannel): RootManagedProcessExit = runInterruptible(Dispatchers.IO) {
        ticket.validate()
        require(ticket.identity == identity() && ticket.identity.pid == Process.myPid()) { "Root service epoch changed" }
        require(worker.sha256 == ticket.workerSha256) { "Worker version differs from immutable ticket" }
        val arguments = channel.arguments
        require(arguments == listOf("attach-channel", channel.name, channel.nonce, WireJson.encodeToString(ticket))) {
            "Script channel belongs to another ticket"
        }
        guard.usePath { helper -> worker.usePath { executable ->
            launch(state, listOf(helper, "run") + key(ticket, state) + listOf("--observe-ms", ticket.observeMs.toString(),
                "--cooperative-cancel", "--", executable) + arguments, ticket.timeoutMs + 5_000, "worker-events.log")
        } }
    }

    /** Retires only this immutable run. The native lock and retirement journal exclude late starts. */
    suspend fun recover(ticket: RootManagedRunTicket, state: Path, guard: RootManagedExecutable): RootManagedProcessExit =
        runInterruptible(Dispatchers.IO) {
            ticket.validate()
            guard.usePath { helper -> launch(state, listOf(helper, "recover") + key(ticket, state), 25_000) }
        }

    private fun key(ticket: RootManagedRunTicket, state: Path) = listOf(
        "--state-dir", state.toString(), "--run-id", ticket.runId, "--ticket-sha", ticket.digest())

    private fun launch(state: Path, arguments: List<String>, budgetMs: Long,
        logName: String = "coordinator-${UUID.randomUUID()}.log"): RootManagedProcessExit {
        check(Process.myUid() == 0) { "Managed native tasks require Root" }
        require(state.isAbsolute && state.normalize() == state)
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val directory = Os.open(state.toString(), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val info = Os.fstat(directory)
            require(S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0) { "Task directory is not Root-private" }
            // The log descriptor is anchored before launch; Java's redirected streams cannot cut
            // the worker's output pipe during Process.destroy's cooperative TERM operation.
            ParcelFileDescriptor.dup(directory).use { pinned ->
                val log = Os.open("/proc/self/fd/${pinned.fd}/$logName",
                    O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
                try { return execute(state, arguments, log, budgetMs) } finally { Os.close(log) }
            }
        } finally { Os.close(directory) }
    }

    private fun execute(state: Path, arguments: List<String>, log: java.io.FileDescriptor, budgetMs: Long): RootManagedProcessExit =
        ParcelFileDescriptor.dup(log).use { pinned ->
            val process = ProcessBuilder(arguments).directory(state.toFile())
                .redirectInput(File("/dev/null")).redirectErrorStream(true)
                .redirectOutput(File("/proc/${Process.myPid()}/fd/${pinned.fd}"))
                .apply { environment().apply { clear(); put("PATH", "/system/bin"); put("HOME", state.toString()); put("TMPDIR", state.toString()) } }
                .start()
            try { RootManagedProcessWait.await(process, budgetMs) }
            catch (failure: Throwable) {
                if (process.isAlive) {
                    try { process.destroy(); RootManagedProcessWait.await(process, 26_000) }
                    catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                }
                throw failure
            } finally { process.outputStream.close(); process.inputStream.close(); process.errorStream.close() }
        }
}
