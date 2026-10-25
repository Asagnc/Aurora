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

    /** Explicit conversation deletion only; still requires the same native worker/domain exit proof. */
    fun discard(ticket: RootManagedRunTicket, state: ParcelFileDescriptor) {
        requireStopped(ticket, state)
        val config = RootCaptureSegments.configuration(state, ticket)
        if (config == null) { require(!RootCaptureSegments.hasSealedPrefix(state)); return }
        RootCapturePayloadCleanup.remove(state, config, RootCaptureManifest(config.binding, ticket.digest(), 0))
    }

    fun publish(ticket: RootManagedRunTicket, state: ParcelFileDescriptor): String? {
        requireStopped(ticket, state)
        val config = RootCaptureSegments.configuration(state, ticket)
        if (config == null) { require(!RootCaptureSegments.hasSealedPrefix(state)); return null }
        val manifest = RootCaptureSegments.read(state, ticket, config)
        if (manifest == null) {
            RootCapturePayloadCleanup.remove(state, config, RootCaptureManifest(config.binding, ticket.digest(), 0))
            return null
        }
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
                    for (segment in manifest.segments) copy(state, directory, segment, config.options)
                    publishMetadata(directory, metadata)
                    Os.fsync(raw); Os.fchown(raw, uid, uid); Os.fsync(raw); Os.fsync(run.fileDescriptor)
                    val visible = Os.lstat(path)
                    require(visible.st_dev == info.st_dev && visible.st_ino == info.st_ino)
                }
            } finally { Os.close(raw) }
        }
        RootCapturePayloadCleanup.remove(state, config, manifest)
        return "runs/${ticket.runId}/segments/capture-manifest.json"
    }

    private fun copy(state: ParcelFileDescriptor, directory: ParcelFileDescriptor, segment: CaptureSegmentSeal, options: CaptureRotationOptions) {
        val target = "/proc/self/fd/${directory.fd}/${segment.fileName}"
        val source = try { Os.open("/proc/self/fd/${state.fd}/${segment.fileName}", O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0) }
        catch (missing: ErrnoException) {
            if (missing.errno != ENOENT) throw missing
            verifyExisting(target, segment.bytes, segment.sha256, segment, options); return
        }
        try {
            RootCaptureSegments.verify(state, segment, options)
            val info = Os.fstat(source)
            require(S_ISREG(info.st_mode) && info.st_uid == 0 && info.st_nlink == 1L && info.st_mode and 511 == 256 &&
                info.st_dev == segment.device && info.st_ino == segment.inode && info.st_size == segment.bytes)
            val output = create(target)
            if (output == null) { verifyExisting(target, segment.bytes, segment.sha256, segment, options); return }
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

    private fun verifyExisting(path: String, size: Long, expected: String,
        segment: CaptureSegmentSeal? = null, options: CaptureRotationOptions? = null) {
        val fd = Os.open(path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val before = Os.fstat(fd)
            require(S_ISREG(before.st_mode) && before.st_uid in setOf(0, uid) && before.st_nlink == 1L && before.st_mode and 63 == 0 && before.st_size == size)
            if (segment != null) {
                ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd)).use {
                    CaptureSealedPcap.verify(it, segment, requireNotNull(options))
                }
            } else {
                val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536); var remaining = size
                while (remaining > 0) {
                    val count = Os.read(fd, buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    require(count > 0); hash.update(buffer, 0, count); remaining -= count
                }
                require(hex(hash.digest()) == expected) { "Existing publication is partial or differs; retained for explicit recovery" }
            }
            val after = Os.fstat(fd)
            require(before.st_size == after.st_size && before.st_nlink == after.st_nlink && before.st_mtim == after.st_mtim && before.st_ctim == after.st_ctim)
            Os.fsync(fd); Os.fchown(fd, uid, uid); Os.fsync(fd)
        } finally { Os.close(fd) }
    }

    private fun write(fd: FileDescriptor, bytes: ByteArray, count: Int) {
        var offset = 0
        while (offset < count) { val written = Os.write(fd, bytes, offset, count - offset); require(written > 0); offset += written }
    }
    private fun requireStopped(ticket: RootManagedRunTicket, directory: ParcelFileDescriptor) {
        require(ticket.kind == "capture")
        val proof = RootManagedRecords.finished(directory, ticket).normalCleanupProof()
        require(ticket.confirms(requireNotNull(proof)))
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
