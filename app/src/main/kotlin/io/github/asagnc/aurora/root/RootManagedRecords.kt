package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*

/** Bounded IO on an already anchored private task directory. Caller retains its FD and lifecycle lease.
 * Records are append-once; malformed/partial files are not removed or converted to missing facts. */
internal object RootManagedRecords {
    fun admission(directory: ParcelFileDescriptor, ticket: RootManagedRunTicket): RootManagedAdmission =
        anchored(directory) { owned ->
            ticket.validate()
            require(read(owned, "managed.intent", 107)?.contentEquals(key(ticket)) == true)
            require(read(owned, "managed.retired", 107, optional = true) == null) { "Task is already retired" }
            RootManagedAdmission.decode(ticket, requireNotNull(read(owned, "managed.ready", 164)))
        }

    /** Call after the coordinator/recovery process exit barrier; this reader does not wait or stop tasks. */
    fun finished(directory: ParcelFileDescriptor, ticket: RootManagedRunTicket): RootManagedNativeState =
        anchored(directory) { owned ->
            val state = RootManagedNativeState.decode(ticket,
                read(owned, "managed.ready", 164, optional = true),
                read(owned, "managed.worker", 52, optional = true),
                requireNotNull(read(owned, "managed.stopped", 123)),
                if (ticket.action == "spawn") RootSpawnState.decode(ticket,
                    (1..5).map { read(owned, "spawn.$it", 64, optional = true) }) else null)
            val key = key(ticket)
            for ((name, expected) in listOf("managed.intent" to (state.phase and 1 != 0),
                "managed.released" to (state.phase and 4 != 0), "managed.retired" to true)) {
                val bytes = read(owned, name, 107, optional = true)
                require(if (expected) bytes?.contentEquals(key) == true else bytes == null) { "Task journal stages disagree" }
            }
            state
        }

    private fun <T> anchored(directory: ParcelFileDescriptor, block: (ParcelFileDescriptor) -> T): T {
        check(Process.myUid() == 0) { "Managed native records require Root" }
        return ParcelFileDescriptor.dup(directory.fileDescriptor).use { owned ->
            val info = Os.fstat(owned.fileDescriptor)
            require(S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0) { "Task directory is not Root-private" }
            block(owned)
        }
    }

    private fun read(directory: ParcelFileDescriptor, name: String, size: Int, optional: Boolean = false): ByteArray? {
        val descriptor = try { Os.open("/proc/self/fd/${directory.fd}/$name", O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0) }
        catch (failure: ErrnoException) {
            if (optional && failure.errno == ENOENT) return null
            throw failure
        }
        try {
            fun checked() {
                val info = Os.fstat(descriptor)
                require(S_ISREG(info.st_mode) && info.st_uid == 0 && info.st_nlink == 1L &&
                    info.st_mode and 63 == 0 && info.st_size == size.toLong()) { "Unsafe or partial native task record" }
            }
            checked()
            val bytes = ByteArray(size + 1)
            var used = 0
            while (used < bytes.size) {
                val count = Os.read(descriptor, bytes, used, bytes.size - used)
                if (count == 0) break
                require(count > 0); used += count
            }
            require(used == size) { "Native task record changed or is truncated" }
            checked()
            return bytes.copyOf(size)
        } finally { Os.close(descriptor) }
    }

    private fun key(ticket: RootManagedRunTicket) = "ARI1\n${ticket.runId}\n${ticket.digest()}\n".toByteArray(Charsets.US_ASCII)
}
