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
                    .execute(conversationRoot(conversation), decoded).toString()
            }
        }

        override fun cancelAndWait(id: String) {
            verifyCaller()
            runBlocking { commands.cancelAndWait(id) }
        }

        override fun closeCommands() {
            verifyCaller()
            runBlocking { commands.close() }
        }

        override fun snapshot(): Bundle {
            verifyCaller()
            val kernel = Os.uname()
            return Bundle().apply {
                putInt("protocol", 1)
                putInt("commandProtocol", 1)
                putInt("uid", Process.myUid())
                putInt("pid", Process.myPid())
                putInt("sdk", Build.VERSION.SDK_INT)
                putString("kernel", kernel.release.take(256))
                putString("architecture", kernel.machine.take(64))
                putString("selinux", runCatching { File("/proc/self/attr/current").readText().trim().take(128) }.getOrDefault("不可读取"))
            }
        }
    }

    override fun onDestroy() { runBlocking { commands.close() } }
}
