package io.github.asagnc.aurora.root

import android.content.Context
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.*
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class RootLinuxRuns(private val context: Context, private val identity: () -> LinuxRootIdentity) {
    private val files = RootLinuxRunFiles(context)
    suspend fun start(ticket: LinuxRunTicket): String = withContext(Dispatchers.IO) {
        val commandRequest = prepare(ticket, terminal = false)
        val state = files.state(ticket)
        var command: JsonObject? = null
        try {
            command = NativeCommandRunner(context, ticket.appUid).execute(files.conversation(ticket.owner),
                commandRequest, ticket.runId)
        } finally {
            // Generic runner exit cannot prove that the separate guard stopped.
            withContext(NonCancellable) { runCatching { recoverStopped(ticket, state) } }
        }
        response(ticket, state, command).toString()
    }
    private suspend fun prepare(ticket: LinuxRunTicket, terminal: Boolean): CommandRequest {
        require(ticket.identity == identity()) { "Root service epoch changed" }
        val state = files.state(ticket)
        files.bind(state, ticket, starting = true)
        val rootfs = files.rootfs(ticket)
        val workspace = RootWorkspaceLocks.use(context, ticket.owner) { files.workspace(ticket.owner, claimRun = ticket.runId) }
        return CommandRequest(listOf(helper(), if (terminal) "--run-pty" else "--run", "--state-dir", state.toString(),
            "--generation", ticket.generation, "--run-id", ticket.runId, "--rootfs", rootfs.toString(),
            "--workspace", workspace.toString(), "--timeout-ms", ticket.timeoutMs.toString(), "--") + ticket.argv,
            timeoutMs = ticket.timeoutMs + 20_000)
    }
    fun terminal(ticket: LinuxRunTicket, request: io.github.asagnc.aurora.terminal.PtyRequest): RootTerminalLifecycle {
        request.validate()
        require(request.argv == ticket.argv && request.timeoutMs == ticket.timeoutMs)
        require(request.directory.isEmpty() && request.environment.isEmpty()) { "Linux terminal uses the fixed guest environment and /mnt" }
        return object : RootTerminalLifecycle {
            override suspend fun open() = withContext(Dispatchers.IO) {
                val command = prepare(ticket, terminal = true)
                io.github.asagnc.aurora.terminal.PtyProcess(context).open(files.conversation(ticket.owner),
                    request.copy(argv = command.argv, timeoutMs = command.timeoutMs))
            }
            override suspend fun finish(outcome: io.github.asagnc.aurora.terminal.PtyOutcome): String = withContext(Dispatchers.IO) {
                val proof = WireJson.parseToJsonElement(recover(ticket)).jsonObject
                val completion = proof["completion"]?.takeUnless { it == JsonNull }?.jsonObject
                val interrupted = completion?.get("interrupted") == JsonPrimitive(true)
                val timedOut = completion?.get("timedOut") == JsonPrimitive(true)
                val actual = if (interrupted && outcome.state == "exited") outcome.copy(
                    state = if (timedOut) "timed_out" else "interrupted", reason = if (timedOut) "Linux guest deadline" else "Linux guest interrupted") else outcome
                JsonObject(WireJson.parseToJsonElement(actual.wire()).jsonObject + ("linuxProof" to proof)).toString()
            }
        }
    }
    suspend fun recover(ticket: LinuxRunTicket): String = withContext(Dispatchers.IO) {
        require(previousServiceGone(ticket.identity)) { "Previous Root service is still alive" }
        val state = files.state(ticket)
        files.bind(state, ticket, starting = false)
        recoverStopped(ticket, state)
        response(ticket, state, null).toString()
    }
    private suspend fun recoverStopped(ticket: LinuxRunTicket, state: Path) {
        val result = NativeCommandRunner(context, ticket.appUid).execute(files.conversation(ticket.owner), CommandRequest(
            listOf(helper(), "--recover", "--state-dir", state.toString()), timeoutMs = 20_000), UUID.randomUUID().toString())
        val observed = result["stdout"]?.jsonObject?.text("text")?.let { WireJson.parseToJsonElement(it).jsonObject }
        require(result.number("exitCode") == 0L && result["error"] == null && observed?.get("recovered") == JsonPrimitive(true))
        RootWorkspaceLocks.use(context, ticket.owner) { files.settle(ticket) }
        // Root marker is published only after the process barrier and active-workspace marker.
        val marker = state.resolve("stopped")
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            java.nio.channels.FileChannel.open(marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { it.force(true) }
            io.github.asagnc.aurora.linux.LinuxDurability.directory(state)
        }
    }
    private fun response(ticket: LinuxRunTicket, state: Path, result: JsonObject?): JsonObject {
        val completion = state.resolve("completed.json")
        return objectOf("protocol" to JsonPrimitive(1), "runId" to string(ticket.runId), "generation" to string(ticket.generation),
            "stopped" to JsonPrimitive(Files.isRegularFile(state.resolve("stopped"), LinkOption.NOFOLLOW_LINKS)),
            "completion" to if (Files.exists(completion, LinkOption.NOFOLLOW_LINKS)) WireJson.parseToJsonElement(files.text(completion, 4096)) else JsonNull,
            "result" to (result ?: JsonNull))
    }
    private fun previousServiceGone(previous: LinuxRootIdentity): Boolean {
        val current = identity()
        if (previous == current || previous.bootId != current.bootId) return true
        val path = Path.of("/proc/${previous.pid}/stat")
        return try {
            val text = Files.newInputStream(path).use { String(it.readNBytes(65537), Charsets.UTF_8) }
            linuxProcessStartTicks(text) != previous.startTicks
        } catch (missing: NoSuchFileException) {
            try { Os.kill(previous.pid, 0); false }
            catch (failure: android.system.ErrnoException) { if (failure.errno == OsConstants.ESRCH) true else throw failure }
        }
    }
    private fun helper() = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_linux_scope.so").toString()
}
