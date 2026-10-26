package io.github.asagnc.aurora.root

import android.system.Os
import android.system.OsConstants
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Stable source descriptor plus an optional selected digest; a replaced source cannot change the published export. */
internal object RootLinuxExport {
    suspend fun copy(source: Path, target: Path, maxBytes: Long, expectedSha256: String?): String {
        require(maxBytes in 1..32L * 1024 * 1024)
        require(expectedSha256 == null || expectedSha256.matches(Regex("[a-f0-9]{64}")))
        val input = Os.open(source.toString(), OsConstants.O_RDONLY or OsConstants.O_NONBLOCK or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)
        try {
            val before = Os.fstat(input)
            require(OsConstants.S_ISREG(before.st_mode) && before.st_size in 1..maxBytes)
            val hash = MessageDigest.getInstance("SHA-256"); var total = 0L
            FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { output ->
                val bytes = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = Os.read(input, bytes, 0, bytes.size); if (count == 0) break
                    total += count; require(total <= maxBytes); hash.update(bytes, 0, count)
                    val buffer = ByteBuffer.wrap(bytes, 0, count)
                    while (buffer.hasRemaining()) { currentCoroutineContext().ensureActive(); require(output.write(buffer) > 0) }
                }
                val after = Os.fstat(input)
                require(total == before.st_size && after.st_size == before.st_size && after.st_mtime == before.st_mtime &&
                    after.st_ctime == before.st_ctime && after.st_mtim.tv_nsec == before.st_mtim.tv_nsec && after.st_ctim.tv_nsec == before.st_ctim.tv_nsec)
                output.force(true)
            }
            val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(expectedSha256 == null || actual == expectedSha256) { "Export bytes differ from the selected digest" }
            return actual
        } finally { Os.close(input) }
    }
}
