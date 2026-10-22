package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*
import java.nio.file.Path
import kotlinx.serialization.json.*

/** Opens only selected, descriptor-anchored paths after the writer has stopped. */
internal object RootPacketPublication {
    private fun directory(path: String): ParcelFileDescriptor {
        val fd = Os.open(path, O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        return try { require(S_ISDIR(Os.fstat(fd).st_mode)); ParcelFileDescriptor.dup(fd) } finally { Os.close(fd) }
    }
    private fun ParcelFileDescriptor.child(name: String) = "/proc/self/fd/$fd/$name"

    fun publish(root: Path, id: String, capture: JsonObject, request: PacketCaptureRequest, uid: Int): JsonObject {
        require(java.util.UUID.fromString(id).toString() == id && uid > 0)
        return directory(root.toString()).use { owner ->
            directory(owner.child("runs")).use { runs ->
                directory(runs.child(id)).use { run ->
                    val fd = Os.open(run.child("capture.pcap"), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
                    try {
                        val info = Os.fstat(fd)
                        require(S_ISREG(info.st_mode) && info.st_nlink == 1L && info.st_uid == 0)
                        require(info.st_dev == capture.number("device") && info.st_ino == capture.number("inode"))
                        require(info.st_size == capture.number("bytes") && info.st_size in 24..request.bytes)
                        Os.fsync(fd)
                        Os.fchmod(fd, 384)
                        Os.fchown(fd, uid, uid)
                        Os.fsync(fd)
                    } finally { Os.close(fd) }
                    val published = JsonObject(capture + mapOf("path" to string("runs/$id/capture.pcap")))
                    metadata(run, published, uid)
                    Os.fsync(run.fileDescriptor)
                    published
                }
            }
        }
    }

    private fun metadata(run: ParcelFileDescriptor, capture: JsonObject, uid: Int) {
        val fd = Os.open(run.child("capture-status.json"), O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC, 384)
        try {
            val bytes = capture.toString().toByteArray(Charsets.UTF_8)
            var offset = 0
            while (offset < bytes.size) {
                val count = Os.write(fd, bytes, offset, bytes.size - offset)
                check(count > 0); offset += count
            }
            Os.fsync(fd)
            Os.fchown(fd, uid, uid)
            Os.fsync(fd)
        } finally { Os.close(fd) }
    }
}
