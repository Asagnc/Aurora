package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants.*
import android.system.StructStat
import java.io.FileDescriptor

/** A repeated recovery may reuse only the complete bytes of this Root log.
 * No truncation, deletion or replacement of a mismatching destination. */
internal object RootManagedExistingArtifact {
    fun verify(source: FileDescriptor, expected: StructStat, directory: ParcelFileDescriptor, uid: Int) {
        val destination = Os.open(path(directory), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val before = Os.fstat(destination)
            require(S_ISREG(before.st_mode) && before.st_uid in setOf(0, uid) &&
                before.st_gid == before.st_uid && before.st_nlink == 1L &&
                before.st_mode and 4095 == 384 && before.st_size == expected.st_size)
            val original = ByteArray(65536)
            val copy = ByteArray(65536)
            var remaining = expected.st_size
            while (remaining > 0) {
                val count = minOf(remaining, original.size.toLong()).toInt()
                readExactly(source, original, count)
                readExactly(destination, copy, count)
                require((0 until count).all { original[it] == copy[it] }) { "Existing Frida log differs from Root original" }
                remaining -= count
            }
            require(stable(expected, Os.fstat(source)) && stable(before, Os.fstat(destination)))
            require(stable(before, Os.lstat(path(directory)))) { "Existing Frida log entry changed" }
            handoff(destination, directory, uid, expected.st_size)
        } finally { Os.close(destination) }
    }

    fun handoff(destination: FileDescriptor, directory: ParcelFileDescriptor, uid: Int, size: Long) {
        val before = Os.fstat(destination)
        require(S_ISREG(before.st_mode) && before.st_nlink == 1L && before.st_size == size &&
            before.st_mode and 4095 == 384 && before.st_uid in setOf(0, uid))
        val entry = Os.lstat(path(directory))
        require(stable(before, entry)) { "Frida log entry was replaced before handoff" }
        Os.fsync(destination)
        if (before.st_uid == 0) Os.fchown(destination, uid, uid)
        Os.fsync(destination)
        val after = Os.fstat(destination)
        require(after.st_uid == uid && after.st_gid == uid && after.st_size == size && after.st_nlink == 1L && after.st_mode and 4095 == 384)
        require(stable(after, Os.lstat(path(directory)))) { "Frida log entry changed during handoff" }
        Os.fsync(directory.fileDescriptor)
    }

    private fun readExactly(file: FileDescriptor, data: ByteArray, size: Int) {
        var used = 0
        while (used < size) {
            val count = Os.read(file, data, used, size - used)
            require(count > 0) { "Frida log was truncated" }
            used += count
        }
    }

    private fun path(directory: ParcelFileDescriptor) = "/proc/self/fd/${directory.fd}/frida.log"
    private fun stable(left: StructStat, right: StructStat) = left.st_dev == right.st_dev && left.st_ino == right.st_ino &&
        left.st_uid == right.st_uid && left.st_gid == right.st_gid && left.st_mode == right.st_mode &&
        left.st_nlink == right.st_nlink && left.st_size == right.st_size && left.st_mtim == right.st_mtim && left.st_ctim == right.st_ctim
}
