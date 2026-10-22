package io.github.asagnc.aurora.root

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.nio.channels.FileChannel
import java.nio.file.*
import io.github.asagnc.aurora.core.KeyedMutex

/** Coroutine mutex plus kernel file lock also serialize old/new Root processes. */
internal object RootWorkspaceLocks {
    private val locks = KeyedMutex()
    suspend fun <T> use(context: Context, owner: String, action: suspend () -> T): T = locks.withLock(owner) {
        var parent = context.filesDir.toPath()
        for (part in listOf("spaces", "resources")) {
            parent = parent.resolve(part)
            require(Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(parent))
        }
        val directory = parent.resolve("linux-workspace-locks")
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try { Os.mkdir(directory.toString(), 448) }
            catch (race: android.system.ErrnoException) { if (race.errno != OsConstants.EEXIST) throw race }
        }
        val info = Os.lstat(directory.toString())
        require(OsConstants.S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0)
        FileChannel.open(directory.resolve(owner), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val lock = requireNotNull(channel.tryLock()) { "Previous Root file operation still owns this workspace" }
            lock.use { action() }
        }
    }
}
