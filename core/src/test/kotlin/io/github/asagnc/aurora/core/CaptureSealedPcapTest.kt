package io.github.asagnc.aurora.core

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.*

class CaptureSealedPcapTest {
    private val options = CaptureRotationOptions(segmentBytes = 1024, bytes = 2048)
    private fun bytes(order: ByteOrder = ByteOrder.LITTLE_ENDIAN) = ByteBuffer.allocate(44).order(order).apply {
        putInt(0xa1b2c3d4.toInt()); putShort(2); putShort(4); putInt(0); putInt(0); putInt(262144); putInt(1)
        putInt(123); putInt(456); putInt(4); putInt(8); put(byteArrayOf(1, 2, 3, 4))
    }.array()
    private fun seal(bytes: ByteArray) = CaptureSegmentSeal(0, 1, 2, bytes.size.toLong(), 1, 1,
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }, 1, 0, 1)

    @Test fun bothNativeByteOrdersAndShortStreamReadsMatchActualPacketFacts() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) {
            val data = bytes(order)
            val short = object : ByteArrayInputStream(data) {
                override fun read(output: ByteArray, offset: Int, length: Int) = super.read(output, offset, minOf(length, 1))
            }
            CaptureSealedPcap.verify(short, seal(data), options)
        }
    }

    @Test fun hashAloneCannotMakeMalformedPcapPublishable() {
        for ((offset, value) in listOf(0 to 0, 4 to 3, 8 to 1, 16 to 65535, 20 to 113, 28 to 1000000,
            32 to 262145, 32 to 9, 36 to 3)) {
            val data = bytes().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value) }
            assertFailsWith<IllegalArgumentException> { CaptureSealedPcap.verify(data.inputStream(), seal(data), options) }
        }
    }

    @Test fun truncationTrailingBytesHashAndRegisteredCountsMustAllAgree() {
        val data = bytes(); val seal = seal(data)
        for (changed in listOf(seal.copy(sha256 = "a".repeat(64)), seal.copy(packets = 0, snapshotTruncated = 0), seal.copy(snapshotTruncated = 0)))
            assertFailsWith<IllegalArgumentException> { CaptureSealedPcap.verify(data.inputStream(), changed, options) }
        assertFailsWith<IllegalArgumentException> { CaptureSealedPcap.verify(data.copyOf(43).inputStream(), seal, options) }
        assertFailsWith<IllegalArgumentException> { CaptureSealedPcap.verify((data + byteArrayOf(0)).inputStream(), seal, options) }
    }
}
