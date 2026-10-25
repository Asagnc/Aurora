package io.github.asagnc.aurora.root

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*
import java.io.FileDescriptor
import java.security.MessageDigest
import kotlinx.serialization.encodeToString

/** Recovered closed segments stay Root-private. A separate complete copy is handed to its original conversation. */
internal class RootCapturePublication(private val context: Context) {
    private val uid get() = context.applicationInfo.uid
    private val runs = RootManagedPublication(context)

    fun publish(ticket: RootManagedRunTicket, state: ParcelFileDescriptor): String {
        val config = RootCaptureSegments.configuration(state, ticket)
        val manifest = RootCaptureSegments.read(state, ticket, config)
        val metadata = WireJson.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        require(metadata.size <= CaptureRotationOptions.MAX_MANIFEST_BYTES)
        runs.ownerDirectory(ticket).use { run ->
            val path = "/proc/self/fd/${run.fd}/segments"
            try { Os.mkdir(path, 448) }
            catch (failure: ErrnoException) { if (failure.errno != EEXIST) throw failure }
            val raw = Os.open(path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
            try {
                val info = Os.fstat(raw)
                require(S_ISDIR(info.st_mode) && info.st_uid in setOf(0, uid) && info.st_mode and 63 == 0)
                ParcelFileDescriptor.dup(raw).use { directory ->
                    for (segment in manifest.segments) copy(state, directory, segment)
                    publishMetadata(directory, metadata)
                    Os.fsync(raw); Os.fchown(raw, uid, uid); Os.fsync(raw); Os.fsync(run.fileDescriptor)
                    val visible = Os.lstat(path)
                    require(visible.st_dev == info.st_dev && visible.st_ino == info.st_ino)
                }
            } finally { Os.close(raw) }
        }
        return "runs/${ticket.runId}/segments/capture-manifest.json"
    }

    private fun copy(state: ParcelFileDescriptor, directory: ParcelFileDescriptor, segment: CaptureSegmentSeal) {
        val source = Os.open("/proc/self/fd/${state.fd}/${segment.fileName}", O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val info = Os.fstat(source)
            require(S_ISREG(info.st_mode) && info.st_uid == 0 && info.st_nlink == 1L && info.st_mode and 511 == 256 &&
                info.st_dev == segment.device && info.st_ino == segment.inode && info.st_size == segment.bytes)
            val target = "/proc/self/fd/${directory.fd}/${segment.fileName}"
            val output = create(target)
            if (output == null) { verifyExisting(target, segment.bytes, segment.sha256); return }
            try {
                val buffer = ByteArray(65536); val hash = MessageDigest.getInstance("SHA-256"); var copied = 0L
                while (copied < segment.bytes) {
                    val count = Os.read(source, buffer, 0, minOf(buffer.size.toLong(), segment.bytes - copied).toInt())
                    require(count > 0); write(output, buffer, count); hash.update(buffer, 0, count); copied += count
                }
                require(hex(hash.digest()) == segment.sha256)
                val after = Os.fstat(source)
                require(after.st_nlink == 1L && after.st_size == info.st_size && after.st_mtim == info.st_mtim && after.st_ctim == info.st_ctim)
                Os.fsync(output); Os.fchown(output, uid, uid); Os.fsync(output)
            } finally { Os.close(output) }
        } finally { Os.close(source) }
    }

    private fun publishMetadata(directory: ParcelFileDescriptor, bytes: ByteArray) {
        val path = "/proc/self/fd/${directory.fd}/capture-manifest.json"
        val output = create(path)
        if (output == null) { verifyExisting(path, bytes.size.toLong(), hex(MessageDigest.getInstance("SHA-256").digest(bytes))); return }
        try { write(output, bytes, bytes.size); Os.fsync(output); Os.fchown(output, uid, uid); Os.fsync(output) }
        finally { Os.close(output) }
    }

    private fun create(path: String): FileDescriptor? = try { Os.open(path, O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384) }
    catch (failure: ErrnoException) { if (failure.errno == EEXIST) null else throw failure }

    private fun verifyExisting(path: String, size: Long, expected: String) {
        val fd = Os.open(path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val before = Os.fstat(fd)
            require(S_ISREG(before.st_mode) && before.st_uid in setOf(0, uid) && before.st_nlink == 1L && before.st_mode and 63 == 0 && before.st_size == size)
            val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536); var remaining = size
            while (remaining > 0) {
                val count = Os.read(fd, buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                require(count > 0); hash.update(buffer, 0, count); remaining -= count
            }
            val after = Os.fstat(fd)
            require(before.st_size == after.st_size && before.st_nlink == after.st_nlink && before.st_mtim == after.st_mtim && before.st_ctim == after.st_ctim)
            require(hex(hash.digest()) == expected) { "Existing publication is partial or differs; retained for explicit recovery" }
            Os.fsync(fd); Os.fchown(fd, uid, uid); Os.fsync(fd)
        } finally { Os.close(fd) }
    }

    private fun write(fd: FileDescriptor, bytes: ByteArray, count: Int) {
        var offset = 0
        while (offset < count) { val written = Os.write(fd, bytes, offset, count - offset); require(written > 0); offset += written }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
