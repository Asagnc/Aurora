package io.github.asagnc.aurora.root

import android.content.Context
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxDurability
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

/** Reuses existing Root ticket/stopped evidence. No new journal, PID guess or Root-owned marker is introduced. */
internal class RootProxyCacheCleanup(private val context: Context, private val files: RootLinuxRunFiles) {
    suspend fun execute(owner: String, root: Path, id: String): JsonObject {
        require(UUID.fromString(id).toString() == id)
        check(!Files.exists(root.resolve(".active-run"), LinkOption.NOFOLLOW_LINKS)) { "An active or unconfirmed guest retains its private cache" }
        // Native Linux mounts workspace/project at /mnt; private paths written
        // under /mnt therefore belong to this anchored project, not workspace.
        val project = root.resolve("project"); directory(project)
        RootLinuxMountSafety.requireUnmounted(project)
        val prefixes = listOf(".aurora-proxy-private", ".aurora-proxy-read-private")
        for (prefix in prefixes) {
            val parent = project.resolve(prefix)
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) directory(parent)
        }
        if (prefixes.none { Files.exists(project.resolve(it).resolve(id), LinkOption.NOFOLLOW_LINKS) })
            return objectOf("removed" to JsonPrimitive(false), "pending" to JsonPrimitive(false), "session" to string(id))
        val runtime = files.resources().resolve("linux-runtime"); directory(runtime)
        val state = runtime.resolve(id); directory(state)
        regular(state.resolve("ticket.json")); regular(state.resolve("stopped"))
        require(Os.lstat(state.resolve("stopped").toString()).st_size == 0L)
        regular(state.resolve("completed.json"))
        require(WireJson.parseToJsonElement(files.text(state.resolve("completed.json"), 4096)).jsonObject["scopeRemoved"] == JsonPrimitive(true)) {
            "Native domain closure is not confirmed"
        }
        val ticket = WireJson.decodeFromString<LinuxRunTicket>(files.text(state.resolve("ticket.json"), 262144))
        require(ticket.runId == id)
        val prefix = networkProxyPrivatePrefix(ticket, owner, context.applicationInfo.uid)
        val target = project.resolve(prefix).resolve(id)
        directory(target); RootLinuxMountSafety.requireUnmounted(target)
        remove(target)
        val unexpected = prefixes.filter { it != prefix }.any { Files.exists(project.resolve(it).resolve(id), LinkOption.NOFOLLOW_LINKS) }
        return objectOf("removed" to JsonPrimitive(true), "pending" to JsonPrimitive(unexpected), "session" to string(id))
    }

    private suspend fun remove(root: Path) {
        val lifetime = currentCoroutineContext(); var entries = 0
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            private fun check(path: Path) {
                lifetime.ensureActive(); require(++entries <= 100000)
                val info = Os.lstat(path.toString())
                require(info.st_uid == 0)
                require(OsConstants.S_ISREG(info.st_mode) || OsConstants.S_ISDIR(info.st_mode) || OsConstants.S_ISLNK(info.st_mode))
                if (!OsConstants.S_ISLNK(info.st_mode)) require(info.st_mode and 18 == 0)
            }
            override fun preVisitDirectory(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(path); return FileVisitResult.CONTINUE
            }
            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(path); Files.delete(path); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(path: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                lifetime.ensureActive(); Files.delete(path); return FileVisitResult.CONTINUE
            }
        })
        LinuxDurability.directory(root.parent)
    }

    private fun directory(path: Path) {
        val info = Os.lstat(path.toString())
        require(OsConstants.S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0)
    }
    private fun regular(path: Path) {
        val info = Os.lstat(path.toString())
        require(OsConstants.S_ISREG(info.st_mode) && info.st_uid == 0 && info.st_nlink == 1L && info.st_mode and 18 == 0)
    }
}
