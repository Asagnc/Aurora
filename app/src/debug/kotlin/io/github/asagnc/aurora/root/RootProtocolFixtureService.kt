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
    override fun onBind(intent: Intent): IBinder = object : IRootControl.Stub() {
        override fun snapshot() = Bundle().apply { putInt("uid", Process.myUid()); putInt("pid", Process.myPid()) }
        override fun start(id: String, conversation: String, request: Bundle, callback: IRootResult) {
            check(Binder.getCallingUid() == applicationInfo.uid)
            require(UUID.fromString(conversation).toString() == conversation)
            val root = filesDir.toPath().resolve("root-protocol-fixture").resolve(conversation)
            val decoded = RootCommandWire.decode(request)
            commands.start(id, callback) {
                Files.createDirectories(root)
                NativeCommandRunner(this@RootProtocolFixtureService).execute(root, decoded).toString()
            }
        }
        override fun cancelAndWait(id: String) { runBlocking { commands.cancelAndWait(id) } }
        override fun closeCommands() { runBlocking { commands.close() } }
    }
    override fun onDestroy() { runBlocking { commands.close() }; super.onDestroy() }
}
