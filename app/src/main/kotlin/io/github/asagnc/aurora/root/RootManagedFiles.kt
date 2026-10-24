package io.github.asagnc.aurora.root

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.RootManagedRunTicket
import java.nio.file.Path

/** Journals outlive conversation files. Native retirement/locks remain the authority for replay. */
internal class RootManagedFiles(private val context: Context) {
    fun state(ticket: RootManagedRunTicket): Path {
        ticket.validate()
        var path = context.filesDir.toPath().toRealPath()
        for (part in listOf("spaces", "resources")) {
            path = path.resolve(part)
            require(S_ISDIR(Os.lstat(path.toString()).st_mode))
        }
        path = directory(path, "root-managed-runtime")
        return directory(path, ticket.runId)
    }

    fun open(path: Path): ParcelFileDescriptor {
        val fd = Os.open(path.toString(), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            checkPrivate(Os.fstat(fd))
            val owned = ParcelFileDescriptor.dup(fd)
            try { Os.fcntlInt(owned.fileDescriptor, F_SETFD, FD_CLOEXEC); return owned }
            catch (failure: Throwable) { owned.close(); throw failure }
        } finally { Os.close(fd) }
    }

    private fun directory(parent: Path, name: String): Path {
        val path = parent.resolve(name)
        try { Os.mkdir(path.toString(), 448) }
        catch (failure: ErrnoException) { if (failure.errno != EEXIST) throw failure }
        checkPrivate(Os.lstat(path.toString()))
        // Also retry durability after an earlier mkdir/fsync failure.
        val fd = Os.open(parent.toString(), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try { require(S_ISDIR(Os.fstat(fd).st_mode)); Os.fsync(fd) } finally { Os.close(fd) }
        return path
    }

    private fun checkPrivate(info: android.system.StructStat) {
        require(S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0)
    }
}
