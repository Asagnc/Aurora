package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import io.github.asagnc.aurora.core.RootScriptSnapshot
import java.io.FileDescriptor
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Consumes the selected local stream; returned PFD is owned by the caller. No disk fallback. */
internal object RootScriptMemory {
    // Verified against NDK30 linux/memfd.h and linux/fcntl.h UAPI, not hidden Android APIs.
    private const val MFD_CLOEXEC = 0x0001
    private const val MFD_ALLOW_SEALING = 0x0002
    private const val F_ADD_SEALS = 1033
    private const val F_GET_SEALS = 1034
    private const val F_SEAL_SEAL = 0x0001
    private const val F_SEAL_SHRINK = 0x0002
    private const val F_SEAL_GROW = 0x0004
    private const val F_SEAL_WRITE = 0x0008
    private const val REQUIRED_SEALS = F_SEAL_SEAL or F_SEAL_SHRINK or F_SEAL_GROW or F_SEAL_WRITE

    suspend fun capture(source: InputStream, expected: RootScriptSnapshot): ParcelFileDescriptor {
        var captured: ParcelFileDescriptor? = null
        try {
            val result = source.use { input -> withContext(Dispatchers.IO) {
                expected.validate(); currentCoroutineContext().ensureActive()
                val fd = Os.memfd_create("aurora-script", MFD_CLOEXEC or MFD_ALLOW_SEALING)
                try {
                    copy(input, fd, expected)
                    Os.fcntlInt(fd, F_ADD_SEALS, REQUIRED_SEALS)
                    requireSealed(fd, expected)
                    Os.lseek(fd, 0, OsConstants.SEEK_SET)
                    currentCoroutineContext().ensureActive()
                    ParcelFileDescriptor.dup(fd).also {
                        captured = it
                        Os.fcntlInt(it.fileDescriptor, OsConstants.F_SETFD, OsConstants.FD_CLOEXEC)
                    }
                } finally { Os.close(fd) }
            } }
            currentCoroutineContext().ensureActive()
            return result
        } catch (failure: Throwable) {
            try { captured?.close() } catch (closing: Throwable) { failure.addSuppressed(closing) }
            throw failure
        }
    }
    private suspend fun copy(input: InputStream, fd: FileDescriptor, expected: RootScriptSnapshot) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536); var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            require(count > 0 && total + count <= expected.bytes) { "Script snapshot size changed" }
            var offset = 0
            while (offset < count) {
                currentCoroutineContext().ensureActive()
                val written = Os.write(fd, buffer, offset, count - offset)
                check(written > 0); offset += written
            }
            digest.update(buffer, 0, count); total += count
        }
        require(total == expected.bytes && hex(digest.digest()) == expected.sha256) { "Script snapshot digest changed" }
    }
    /** Receiver verifies seals/size before reading; pread leaves the shared descriptor offset unchanged. */
    suspend fun verify(fd: FileDescriptor, expected: RootScriptSnapshot) = withContext(Dispatchers.IO) {
        expected.validate(); requireSealed(fd, expected)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65_536); var offset = 0L
        while (offset < expected.bytes) {
            currentCoroutineContext().ensureActive()
            val count = Os.pread(fd, buffer, 0, minOf(buffer.size.toLong(), expected.bytes - offset).toInt(), offset)
            require(count > 0) { "Script snapshot truncated" }
            digest.update(buffer, 0, count); offset += count
        }
        require(hex(digest.digest()) == expected.sha256) { "Script snapshot digest mismatch" }
    }
    private fun requireSealed(fd: FileDescriptor, expected: RootScriptSnapshot) {
        val stat = Os.fstat(fd)
        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size == expected.bytes)
        require(Os.fcntlInt(fd, F_GET_SEALS, 0) and REQUIRED_SEALS == REQUIRED_SEALS) { "Script snapshot is not immutable" }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
