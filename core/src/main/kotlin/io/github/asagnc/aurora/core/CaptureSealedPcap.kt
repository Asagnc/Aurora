package io.github.asagnc.aurora.core

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Streaming validation of an already closed, physically anchored capture segment before publication. */
object CaptureSealedPcap {
    fun verify(input: InputStream, seal: CaptureSegmentSeal, options: CaptureRotationOptions) {
        seal.validate(options)
        val reader = Reader(input, seal.bytes)
        val global = reader.exact(24)
        val little = byteArrayOf(0xd4.toByte(), 0xc3.toByte(), 0xb2.toByte(), 0xa1.toByte())
        val big = little.reversedArray()
        val magic = global.copyOfRange(0, 4)
        require(magic.contentEquals(little) || magic.contentEquals(big)) { "Expected microsecond classic PCAP" }
        val order = if (magic.contentEquals(little)) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val header = ByteBuffer.wrap(global).order(order)
        require(header.getShort(4).toInt() == 2 && header.getShort(6).toInt() == 4 &&
            header.getInt(8) == 0 && header.getInt(12) == 0 && header.getInt(16) == CaptureRotationOptions.SNAPSHOT_BYTES &&
            unsigned(header.getInt(20)) == seal.linkType) { "Capture global header differs from registered segment" }
        var packets = 0L; var truncated = 0L
        while (reader.remaining > 0) {
            require(packets < seal.packets)
            val record = ByteBuffer.wrap(reader.exact(16)).order(order)
            val micros = unsigned(record.getInt(4)); val captured = unsigned(record.getInt(8)); val original = unsigned(record.getInt(12))
            require(micros < 1_000_000 && captured <= CaptureRotationOptions.SNAPSHOT_BYTES && captured <= original)
            reader.skip(captured)
            packets++; if (captured < original) truncated++
        }
        require(packets == seal.packets && truncated == seal.snapshotTruncated && reader.digest() == seal.sha256)
        require(input.read() == -1) { "Segment contains bytes after registered boundary" }
    }

    private fun unsigned(value: Int) = value.toLong() and 0xffffffffL

    private class Reader(private val input: InputStream, private var bytes: Long) {
        private val hash = MessageDigest.getInstance("SHA-256")
        private val buffer = ByteArray(65536)
        val remaining: Long get() = bytes
        fun exact(size: Int): ByteArray {
            require(size.toLong() <= bytes)
            val result = ByteArray(size); var offset = 0
            while (offset < size) {
                val count = input.read(result, offset, size - offset)
                require(count > 0) { "Segment is truncated or stalled" }
                hash.update(result, offset, count); offset += count; bytes -= count
            }
            return result
        }
        fun skip(size: Long) {
            require(size <= bytes)
            var pending = size
            while (pending > 0) {
                val count = input.read(buffer, 0, minOf(pending, buffer.size.toLong()).toInt())
                require(count > 0) { "Captured packet is truncated or stalled" }
                hash.update(buffer, 0, count); pending -= count; bytes -= count
            }
        }
        fun digest(): String = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
