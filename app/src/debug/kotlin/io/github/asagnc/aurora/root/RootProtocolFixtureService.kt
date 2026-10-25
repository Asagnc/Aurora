package io.github.asagnc.aurora.root

import android.app.Service
import android.content.Intent
import android.os.*
import io.github.asagnc.aurora.runtime.NativeCommandRunner
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** Ordinary-UID cross-process IPC fixture. Never included in release; does not simulate Root privileges. */
class RootProtocolFixtureService : Service() {
    private val commands = RootCommandSession()
    private val terminals by lazy { RootTerminalSessions(this) }
    override fun onBind(intent: Intent): IBinder = object : IRootControl.Stub() {
        override fun snapshot() = Bundle().apply { putInt("uid", Process.myUid()); putInt("pid", Process.myPid()) }
        override fun start(id: String, conversation: String, request: Bundle, callback: IRootResult) {
            check(Binder.getCallingUid() == applicationInfo.uid)
            require(UUID.fromString(conversation).toString() == conversation)
            val root = filesDir.toPath().resolve("root-protocol-fixture").resolve(conversation)
            val decoded = RootCommandWire.decode(request)
            commands.start(id, callback) {
                Files.createDirectories(root)
                NativeCommandRunner(this@RootProtocolFixtureService).execute(root, decoded, id).toString()
            }
        }
        override fun cancelAndWait(id: String) { runBlocking { commands.cancelAndWait(id) } }
        override fun closeCommands() { runBlocking { commands.close(); terminals.close() } }
        override fun openTerminal(id: String, conversation: String, request: Bundle, callback: IRootResult): Bundle {
            check(Binder.getCallingUid() == applicationInfo.uid)
            require(UUID.fromString(conversation).toString() == conversation)
            val directory = filesDir.toPath().resolve("root-protocol-fixture").resolve(conversation)
            Files.createDirectories(directory)
            return terminals.open(id, directory, RootTerminalWire.decode(request), callback)
        }
        override fun activateTerminal(id: String) { terminals.activate(id) }
        override fun stopTerminal(id: String) { runBlocking { terminals.stop(id) } }
        override fun prepareLinux(operation: String, callback: IRootResult) { throw SecurityException("Fixture is not Root") }
        override fun linuxFiles(id: String, owner: String, request: android.os.Bundle, callback: IRootResult) { throw SecurityException("Fixture is not Root") }
        override fun capturePackets(id: String, conversation: String, request: Bundle, callback: IRootResult) { throw SecurityException("Fixture is not Root") }
        override fun injectPackets(id: String, conversation: String, request: Bundle, callback: IRootResult) { throw SecurityException("Fixture is not Root") }
        override fun maintainLinuxMetadata(id: String, request: android.os.Bundle, callback: IRootResult) { throw SecurityException("Fixture has no metadata capability") }
        override fun openLinuxTerminal(id: String, request: android.os.Bundle, callback: IRootResult): android.os.Bundle { throw SecurityException("Fixture is not Root") }
        override fun startLinuxRun(id: String, request: android.os.Bundle, callback: IRootResult) { throw SecurityException("Fixture is not Root") }
        override fun recoverLinuxRun(id: String, request: android.os.Bundle, callback: IRootResult) { throw SecurityException("Fixture is not Root") }
        override fun discardLinux(operation: String) { throw SecurityException("Fixture is not Root") }
        override fun recoverManagedRun(id: String, request: Bundle, callback: IRootResult) { throw SecurityException("Fixture is not Root") }
        override fun startManagedRun(id: String, request: Bundle, script: ParcelFileDescriptor,
            bridge: ParcelFileDescriptor?, callback: IRootResult) {
            RootManagedWire.close(listOfNotNull(script, bridge)); throw SecurityException("Fixture is not Root")
        }
    }
    override fun onDestroy() { runBlocking { commands.close(); terminals.close() }; super.onDestroy() }
}
