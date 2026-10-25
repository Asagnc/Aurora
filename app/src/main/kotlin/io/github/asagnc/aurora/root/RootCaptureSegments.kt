package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.*
import io.github.asagnc.aurora.core.*
import java.io.FileDescriptor
import java.io.InputStream

/** Revalidates an ordered registered prefix after native recovery. Never infers a seal from orphan/partial bytes. */
internal object RootCaptureSegments {
    fun configuration(directory: ParcelFileDescriptor, ticket: RootManagedRunTicket): RootCaptureConfiguration {
        check(Process.myUid() == 0)
        val raw = requireNotNull(metadata(directory, "capture.config", 4372))
        return RootCaptureConfiguration.decode(raw).also { it.validateTicket(ticket) }
    }

    fun read(directory: ParcelFileDescriptor, ticket: RootManagedRunTicket, config: RootCaptureConfiguration): RootCaptureManifest {
        config.validateTicket(ticket)
        check(Process.myUid() == 0)
        val state = RootManagedRecords.finished(directory, ticket)
        require(ticket.confirms(requireNotNull(state.normalCleanupProof()))) { "Capture handles have no actual worker/domain exit proof" }
        ParcelFileDescriptor.dup(directory.fileDescriptor).use { owned ->
            val info = Os.fstat(owned.fileDescriptor)
            require(S_ISDIR(info.st_mode) && info.st_uid == 0 && info.st_mode and 63 == 0)
            var manifest: RootCaptureManifest? = null
            var missing = false
            for (ordinal in 0 until config.options.segments) {
                val name = "capture-%03d.seal".format(java.util.Locale.ROOT, ordinal)
                val raw = metadata(owned, name)
                if (raw == null) { missing = true; continue }
                require(!missing) { "Capture seal records contain a gap" }
                val record = WireJson.decodeFromString<RootCaptureSealRecord>(raw.toString(Charsets.UTF_8))
                record.validate(config.binding, ticket.digest(), config.options)
                require(record.segment.ordinal == ordinal)
                val prefix = manifest ?: RootCaptureManifest(config.binding, ticket.digest(), record.startedBootMs)
                verify(owned, record.segment, config.options)
                manifest = prefix.appendRecord(record, config.options)
            }
            return requireNotNull(manifest) { "No complete registered capture segment" }
        }
    }

    /** Caller keeps the task directory and confirmed resource barrier pinned during verification/publication. */
    fun verify(directory: ParcelFileDescriptor, seal: CaptureSegmentSeal, options: CaptureRotationOptions) {
        seal.validate(options)
        val source = Os.open("/proc/self/fd/${directory.fd}/${seal.fileName}", O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try {
            val before = Os.fstat(source)
            require(S_ISREG(before.st_mode) && before.st_uid == 0 && before.st_mode and 511 == 256 && before.st_nlink == 1L &&
                before.st_dev == seal.device && before.st_ino == seal.inode && before.st_size == seal.bytes)
            CaptureSealedPcap.verify(descriptorInput(source), seal, options)
            val after = Os.fstat(source)
            require(before.st_size == after.st_size && before.st_dev == after.st_dev && before.st_ino == after.st_ino &&
                before.st_nlink == after.st_nlink && before.st_mode == after.st_mode && before.st_uid == after.st_uid &&
                before.st_mtim == after.st_mtim && before.st_ctim == after.st_ctim) { "Closed capture segment changed" }
        } finally { Os.close(source) }
    }

    private fun metadata(directory: ParcelFileDescriptor, name: String, maximum: Int = 1024): ByteArray? {
        val fd = try { Os.open("/proc/self/fd/${directory.fd}/$name", O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0) }
        catch (failure: ErrnoException) { if (failure.errno == ENOENT) return null else throw failure }
        try {
            val before = Os.fstat(fd)
            require(S_ISREG(before.st_mode) && before.st_uid == 0 && before.st_nlink == 1L &&
                before.st_mode and 511 == 256 && before.st_size in 1..maximum.toLong())
            val raw = descriptorInput(fd).readNBytes(maximum + 1)
            require(raw.size.toLong() == before.st_size)
            val after = Os.fstat(fd)
            require(before.st_size == after.st_size && before.st_mtim == after.st_mtim && before.st_ctim == after.st_ctim &&
                before.st_mode == after.st_mode && before.st_nlink == after.st_nlink)
            return raw
        } finally { Os.close(fd) }
    }

    private fun descriptorInput(fd: FileDescriptor) = object : InputStream() {
        override fun read(): Int {
            val byte = ByteArray(1)
            return if (read(byte, 0, 1) < 0) -1 else byte[0].toInt() and 255
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val count = Os.read(fd, buffer, offset, length)
            return if (count == 0) -1 else count
        }
    }
}
