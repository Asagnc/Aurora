package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*

/** Only after all exact conversation copies and their manifest are durable. Retains small keyed recovery records. */
internal object RootCapturePayloadCleanup {
    fun remove(directory: ParcelFileDescriptor, config: RootCaptureConfiguration, manifest: RootCaptureManifest) {
        manifest.validate(config.binding, manifest.ticketSha256, config.options)
        for (ordinal in 0 until config.options.segments) {
            val stem = "capture-%03d".format(java.util.Locale.ROOT, ordinal)
            for (extension in listOf("pcap", "partial")) {
                val path = "/proc/self/fd/${directory.fd}/$stem.$extension"
                val info = try { Os.lstat(path) }
                catch (missing: ErrnoException) { if (missing.errno == ENOENT) continue else throw missing }
                require(S_ISREG(info.st_mode) && info.st_uid == 0 && info.st_nlink == 1L &&
                    info.st_mode and 511 in setOf(256, 384) && info.st_size in 0..config.options.segmentBytes)
                if (extension == "pcap") manifest.segments.getOrNull(ordinal)?.let { seal ->
                    require(info.st_dev == seal.device && info.st_ino == seal.inode && info.st_size == seal.bytes)
                }
                Os.remove(path)
            }
        }
        Os.fsync(directory.fileDescriptor)
    }
}
