package io.github.asagnc.aurora.root

import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.system.Os
import com.topjohnwu.superuser.ipc.RootService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import io.github.asagnc.aurora.runtime.NativeCommandRunner
import kotlinx.coroutines.runBlocking

class AuroraRootService : RootService() {
    private val commands = RootCommandSession()
    private val packets by lazy { RootPacketCapture(this) }
    private val terminals by lazy { RootTerminalSessions(this) }
    private val linuxFiles by lazy { RootLinuxFiles(this) }
    private val linuxRuns by lazy { RootLinuxRuns(this) { linux.identity } }
    private val linux by lazy { RootLinuxPreparation(this) }
    private val metadata by lazy { RootMetadataMaintenance(this) { linux.identity } }
    private val managed by lazy { RootManagedRecovery(this) { linux.identity } }
    private val managedDispatch by lazy { RootManagedDispatch(this) { linux.identity } }

    private fun verifyCaller() {
        check(Binder.getCallingUid() == applicationInfo.uid) { "Caller is outside Aurora" }
        check(Process.myUid() == 0) { "Service has no root identity" }
    }

    private fun conversationRoot(id: String): Path {
        require(UUID.fromString(id).toString() == id)
        var target = filesDir.toPath()
        for (part in listOf("spaces", "conversations", id)) {
            target = target.resolve(part)
            require(!Files.isSymbolicLink(target) && Files.isDirectory(target))
        }
        return target
    }

    override fun onBind(intent: Intent): IBinder = object : IRootControl.Stub() {
        override fun start(id: String, conversation: String, request: Bundle, callback: IRootResult) {
            verifyCaller()
            val decoded = RootCommandWire.decode(request)
            commands.start(id, callback) {
                NativeCommandRunner(this@AuroraRootService, applicationInfo.uid)
                    .execute(conversationRoot(conversation), decoded, id).toString()
            }
        }

        override fun capturePackets(id: String, conversation: String, request: Bundle, callback: IRootResult) {
            verifyCaller()
            val encoded = requireNotNull(request.getString("arguments")).also { require(it.length <= 8192) }
            val args = io.github.asagnc.aurora.core.WireJson.parseToJsonElement(encoded) as kotlinx.serialization.json.JsonObject
            val options = io.github.asagnc.aurora.core.PacketCaptureRequest.parse(args)
            commands.start(id, callback) { packets.execute(conversationRoot(conversation), id, options) }
        }

        override fun cancelAndWait(id: String) {
            verifyCaller()
            runBlocking { commands.cancelAndWait(id) }
        }

        override fun closeCommands() {
            verifyCaller()
            runBlocking { commands.close(); terminals.close(); metadata.close() }
        }

        override fun openTerminal(id: String, conversation: String, request: Bundle, callback: IRootResult): Bundle {
            verifyCaller()
            return terminals.open(id, conversationRoot(conversation), RootTerminalWire.decode(request), callback)
        }
        override fun activateTerminal(id: String) { verifyCaller(); terminals.activate(id) }
        override fun stopTerminal(id: String) { verifyCaller(); runBlocking { terminals.stop(id) } }
        override fun startLinuxRun(id: String, request: Bundle, callback: IRootResult) {
            verifyCaller()
            val ticket = runTicket(id, request)
            commands.start(id, callback) { linuxRuns.start(ticket) }
        }
        override fun recoverLinuxRun(id: String, request: Bundle, callback: IRootResult) {
            verifyCaller()
            val ticket = runTicket(id, request)
            runBlocking(kotlinx.coroutines.Dispatchers.IO) { commands.cancelAndWait(id); terminals.stop(id) }
            commands.start(id, callback) { linuxRuns.recover(ticket) }
        }
        override fun recoverManagedRun(id: String, request: Bundle, callback: IRootResult) {
            verifyCaller()
            val ticket = RootManagedWire.ticket(id, request, applicationInfo.uid)
            runBlocking(kotlinx.coroutines.Dispatchers.IO) { commands.cancelAndWait(id) }
            commands.start(id, callback) { managed.recover(ticket) }
        }
        override fun startManagedRun(id: String, request: Bundle, script: android.os.ParcelFileDescriptor,
            bridge: android.os.ParcelFileDescriptor?, callback: IRootResult) {
            var owned = emptyList<android.os.ParcelFileDescriptor>()
            try {
                verifyCaller()
                val ticket = RootManagedWire.ticket(id, request, applicationInfo.uid)
                owned = RootManagedWire.snapshots(ticket, script, bridge)
                val captured = owned
                commands.start(id, callback, { RootManagedWire.close(captured) }) { managedDispatch.start(ticket, captured) }
            } catch (failure: Throwable) { RootManagedWire.close(owned); throw failure }
            finally { RootManagedWire.close(listOfNotNull(script, bridge)) }
        }
        override fun openLinuxTerminal(id: String, request: Bundle, callback: IRootResult): Bundle {
            verifyCaller()
            val ticket = runTicket(id, request)
            val terminal = RootTerminalWire.decode(requireNotNull(request.getBundle("terminal")))
            return terminals.open(id, conversationRoot(ticket.owner), terminal, callback, linuxRuns.terminal(ticket, terminal))
        }
        override fun linuxFiles(id: String, owner: String, request: Bundle, callback: IRootResult) {
            verifyCaller()
            val operation = requireNotNull(request.getString("operation"))
            val encoded = requireNotNull(request.getString("arguments")).also { require(it.length <= 262144) }
            val arguments = io.github.asagnc.aurora.core.WireJson.parseToJsonElement(encoded) as kotlinx.serialization.json.JsonObject
            commands.start(id, callback) { linuxFiles.execute(owner, operation, arguments) }
        }
        override fun prepareLinux(operation: String, callback: IRootResult) {
            verifyCaller()
            commands.start(operation, callback) { linux.prepare(operation) }
        }
        override fun maintainLinuxMetadata(id: String, request: Bundle, callback: IRootResult) {
            verifyCaller()
            val encoded = requireNotNull(request.getString("arguments")).also { require(it.length <= 8192) }
            val args = io.github.asagnc.aurora.core.WireJson.parseToJsonElement(encoded) as kotlinx.serialization.json.JsonObject
            commands.start(id, callback) { metadata.invoke(args) }
        }
        override fun discardLinux(operation: String) {
            verifyCaller()
            runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                if (linux.belongsToCurrent(operation)) commands.cancelAndWait(operation)
                linux.discard(operation)
            }
        }

        override fun snapshot(): Bundle {
            verifyCaller()
            val kernel = Os.uname()
            return Bundle().apply {
                putInt("protocol", 1)
                putInt("commandProtocol", 2)
                putInt("packetCaptureProtocol", 1)
                putInt("terminalProtocol", 1)
                putInt("linuxRunProtocol", 3)
                putInt("linuxFileProtocol", 1)
                putInt("linuxTerminalProtocol", 1)
                putInt("linuxMetadataProtocol", 1)
                putInt("managedRecoveryProtocol", 1)
                // Transport support is separate from worker/ART qualification; no ready claim.
                putInt("managedRunProtocol", 1)
                runCatching {
                    val encoded = io.github.asagnc.aurora.core.WireJson.encodeToString(
                        io.github.asagnc.aurora.core.LinuxRootIdentity.serializer(), linux.identity)
                    putString("linuxIdentity", encoded)
                    putInt("linuxPrepareProtocol", 2)
                }
                putInt("uid", Process.myUid())
                putInt("pid", Process.myPid())
                putInt("sdk", Build.VERSION.SDK_INT)
                putString("kernel", kernel.release.take(256))
                putString("architecture", kernel.machine.take(64))
                putString("selinux", runCatching { File("/proc/self/attr/current").readText()
                    .substringBefore('\u0000').trim().take(128) }.getOrDefault("不可读取"))
            }
        }
    }

    private fun runTicket(id: String, request: Bundle): io.github.asagnc.aurora.core.LinuxRunTicket {
        val encoded = requireNotNull(request.getString("ticket")).also { require(it.length <= 262144) }
        return io.github.asagnc.aurora.core.WireJson.decodeFromString<io.github.asagnc.aurora.core.LinuxRunTicket>(encoded).also {
            it.validate(); require(it.runId == id && it.appUid == applicationInfo.uid)
        }
    }

    override fun onDestroy() { runBlocking { commands.close(); terminals.close(); metadata.close() } }
}
