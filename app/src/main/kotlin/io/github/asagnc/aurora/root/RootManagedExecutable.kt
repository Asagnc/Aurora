package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import android.system.StructStat
import java.io.Closeable
import java.io.FileDescriptor
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Pins the checked inode through execution. Caller must also retain its immutable version lease.
 * This is not a sandbox against another Root process rewriting the same inode. */
internal class RootManagedExecutable private constructor(
    private val descriptor: ParcelFileDescriptor, val sha256: String, private val identity: StructStat,
) : Closeable {
    private var closed = false

    /** Blocking launch and its entire exit barrier must remain inside this scope. No FD inheritance needed. */
    fun <T> usePath(block: (String) -> T): T {
        val owned = synchronized(this) {
            check(!closed) { "Executable pin is closed" }
            duplicate(descriptor.fileDescriptor)
        }
        return owned.use {
            require(same(identity, Os.fstat(it.fileDescriptor))) { "Pinned executable changed" }
            val path = "/proc/${Process.myPid()}/fd/${it.fd}"
            ordinaryExecution(path)
            block(path)
        }
    }

    @Synchronized override fun close() {
        if (!closed) { closed = true; descriptor.close() }
    }

    companion object {
        private const val GROUP_OTHER_WRITE = 0x12
        private const val ANY_EXECUTE = 0x49
        private const val SET_ID = 0xC00

        /** Expected digest comes from the qualified immutable bundle, never from the selected file itself. */
        suspend fun open(path: Path, expectedSha256: String): RootManagedExecutable {
            var captured: RootManagedExecutable? = null
            try {
                return withContext(Dispatchers.IO) { openBlocking(path, expectedSha256).also { captured = it } }
            } catch (failure: Throwable) {
                try { captured?.close() } catch (closing: Throwable) { failure.addSuppressed(closing) }
                throw failure
            }
        }

        private suspend fun openBlocking(path: Path, expectedSha256: String): RootManagedExecutable {
            require(path.isAbsolute && path.normalize() == path)
            require(expectedSha256.matches(Regex("[a-f0-9]{64}")))
            currentCoroutineContext().ensureActive()
            val fd = Os.open(path.toString(), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
            try {
                val before = Os.fstat(fd)
                validate(before)
                duplicate(fd).use { ordinaryExecution("/proc/${Process.myPid()}/fd/${it.fd}") }
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65_536); var offset = 0L
                while (offset < before.st_size) {
                    currentCoroutineContext().ensureActive()
                    val count = Os.pread(fd, buffer, 0, minOf(buffer.size.toLong(), before.st_size - offset).toInt(), offset)
                    require(count > 0) { "Executable truncated" }
                    digest.update(buffer, 0, count); offset += count
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
                require(actual == expectedSha256 && same(before, Os.fstat(fd))) { "Executable digest or identity changed" }
                currentCoroutineContext().ensureActive()
                return RootManagedExecutable(duplicate(fd), actual, before)
            } finally { Os.close(fd) }
        }

        private fun validate(info: StructStat) {
            require(S_ISREG(info.st_mode) && info.st_nlink == 1L && info.st_uid in setOf(0, 1000) &&
                info.st_mode and GROUP_OTHER_WRITE == 0 && info.st_mode and ANY_EXECUTE != 0 && info.st_mode and SET_ID == 0 &&
                info.st_size in 1..134_217_728) { "Executable is not an immutable ordinary native bundle file" }
        }

        private fun ordinaryExecution(path: String) {
            try {
                Os.getxattr(path, "security.capability")
                error("File capabilities are not allowed on managed executables")
            } catch (failure: ErrnoException) {
                if (failure.errno != ENODATA && failure.errno != EOPNOTSUPP) throw failure
            }
        }

        private fun duplicate(fd: FileDescriptor): ParcelFileDescriptor {
            val owned = ParcelFileDescriptor.dup(fd)
            try { Os.fcntlInt(owned.fileDescriptor, F_SETFD, FD_CLOEXEC); return owned }
            catch (failure: Throwable) { try { owned.close() } catch (closing: Throwable) { failure.addSuppressed(closing) }; throw failure }
        }

        private fun same(left: StructStat, right: StructStat): Boolean =
            left.st_dev == right.st_dev && left.st_ino == right.st_ino && left.st_size == right.st_size &&
                left.st_uid == right.st_uid && left.st_gid == right.st_gid && left.st_mode == right.st_mode &&
                left.st_nlink == right.st_nlink && left.st_mtim == right.st_mtim && left.st_ctim == right.st_ctim
    }
}
