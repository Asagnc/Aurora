package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*

/** Reads only the completed Root-private log, never the App-writable published copy. */
internal object RootFridaProbeEvidence {
    fun observed(directory: ParcelFileDescriptor, ticket: RootManagedRunTicket): Boolean {
        check(Process.myUid() == 0)
        val fd = Os.open("/proc/self/fd/${directory.fd}/worker-events.log", O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val before = Os.fstat(fd)
            require(S_ISREG(before.st_mode) && before.st_uid == 0 && before.st_nlink == 1L &&
                before.st_mode and 63 == 0 && before.st_size in 1..4_259_840)
            val bytes = ByteArray(before.st_size.toInt() + 1)
            var used = 0
            while (used < bytes.size) {
                val count = Os.read(fd, bytes, used, bytes.size - used)
                if (count == 0) break
                require(count > 0); used += count
            }
            val after = Os.fstat(fd)
            require(used.toLong() == before.st_size && before.st_size == after.st_size &&
                before.st_mtim == after.st_mtim && before.st_ctim == after.st_ctim)
            return FridaProbeEvidence.observed(ticket, String(bytes, 0, used, Charsets.UTF_8))
        } finally { Os.close(fd) }
    }
}
