package io.github.asagnc.aurora.root

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.RootManagedRunTicket

/** Diagnostic bytes only; publication failure cannot invalidate or invent a cleanup proof. */
internal class RootManagedPublication(private val context: Context) {
    private val uid get() = context.applicationInfo.uid

    fun publish(ticket: RootManagedRunTicket, state: ParcelFileDescriptor): String? {
        val source = try { Os.open("/proc/self/fd/${state.fd}/worker-events.log", O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0) }
        catch (missing: ErrnoException) { if (missing.errno == ENOENT) return null else throw missing }
        try {
            val before = Os.fstat(source)
            require(S_ISREG(before.st_mode) && before.st_uid == 0 && before.st_nlink == 1L &&
                before.st_mode and 63 == 0 && before.st_size in 0..4_259_840)
            return ownerDirectory(ticket).use { run ->
                val destination = try {
                    Os.open("/proc/self/fd/${run.fd}/frida.log", O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
                } catch (existing: ErrnoException) {
                    if (existing.errno != EEXIST) throw existing
                    RootManagedExistingArtifact.verify(source, before, run, uid)
                    return@use "runs/${ticket.runId}/frida.log"
                }
                try {
                    val bytes = ByteArray(65536); var remaining = before.st_size
                    while (remaining > 0) {
                        val count = Os.read(source, bytes, 0, minOf(remaining, bytes.size.toLong()).toInt())
                        require(count > 0)
                        var offset = 0
                        while (offset < count) {
                            val written = Os.write(destination, bytes, offset, count - offset)
                            check(written > 0); offset += written
                        }
                        remaining -= count
                    }
                    val after = Os.fstat(source)
                    require(before.st_size == after.st_size && before.st_mtim == after.st_mtim && before.st_ctim == after.st_ctim)
                    // The App cannot read incomplete Root-owned bytes before this handoff.
                    RootManagedExistingArtifact.handoff(destination, run, uid, before.st_size)
                    "runs/${ticket.runId}/frida.log"
                } finally { Os.close(destination) }
            }
        } finally { Os.close(source) }
    }

    internal fun ownerDirectory(ticket: RootManagedRunTicket): ParcelFileDescriptor {
        ticket.validate()
        var directory = open(context.filesDir.toPath().toRealPath().toString())
        try {
            for (part in listOf("spaces", "conversations", ticket.owner, "runs", ticket.runId)) {
                val path = "/proc/self/fd/${directory.fd}/$part"
                val child = open(path)
                val previous = directory; directory = child; previous.close()
            }
            return directory
        } catch (failure: Throwable) { directory.close(); throw failure }
    }

    private fun open(path: String): ParcelFileDescriptor {
        val fd = Os.open(path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val info = Os.fstat(fd)
            require(S_ISDIR(info.st_mode) && info.st_uid == uid)
            val owned = ParcelFileDescriptor.dup(fd)
            try { Os.fcntlInt(owned.fileDescriptor, F_SETFD, FD_CLOEXEC); return owned }
            catch (failure: Throwable) { owned.close(); throw failure }
        } finally { Os.close(fd) }
    }
}
