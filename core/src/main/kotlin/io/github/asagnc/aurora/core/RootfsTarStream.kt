package io.github.asagnc.aurora.core

import java.io.InputStream
import org.apache.commons.compress.archivers.tar.*

/** Use Commons Compress's header parser, bounding extension records before its PAX/GNU allocation. */
internal class RootfsTarStream(input: InputStream, private val entries: Int) : TarArchiveInputStream(input) {
    private var headers = 0
    private var extensions = 0
    private var metadata = 0L
    var eofRecords = 0
        private set
    override fun readRecord(): ByteArray? {
        val record = super.readRecord() ?: return null
        if (record.all { it == 0.toByte() }) { eofRecords++; return record }
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        require(++headers <= entries + 4096) { "Rootfs header budget exceeded" }
        val entry = TarArchiveEntry(record)
        require(entry.isCheckSumOK) { "Invalid TAR header checksum" }
        if (entry.isPaxHeader || entry.isGlobalPaxHeader || entry.isGNULongNameEntry || entry.isGNULongLinkEntry) {
            require(entry.size in 0..65536 && ++extensions <= 16) { "Rootfs extension header exceeds budget" }
            metadata += entry.size; require(metadata <= 8L * 1024 * 1024)
        } else extensions = 0
        return record
    }
}
