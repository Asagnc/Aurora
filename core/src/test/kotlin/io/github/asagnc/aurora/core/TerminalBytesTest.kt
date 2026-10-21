package io.github.asagnc.aurora.core

import kotlin.test.*

class TerminalBytesTest {
    @Test fun cursorsReadIncrementallyWithoutReplayingEarlierOutput() {
        val buffer = TerminalBytes(4096)
        buffer.append("first".toByteArray(), 5)
        val first = buffer.read(0, 3)
        assertEquals("fir", String(first.bytes)); assertEquals(3L, first.next)
        buffer.append(" second".toByteArray(), 7)
        assertEquals("st second", String(buffer.read(first.next).bytes))
    }
    @Test fun overflowReportsLostCursorAndRetainsLastBytes() {
        val buffer = TerminalBytes(4096)
        val bytes = ByteArray(9000) { (it % 251).toByte() }
        buffer.append(bytes, bytes.size)
        val tail = buffer.read(0)
        assertTrue(tail.dropped); assertEquals(4904L, tail.start); assertEquals(9000L, tail.next)
        assertContentEquals(bytes.takeLast(4096).toByteArray(), tail.bytes)
    }
    @Test fun exactCapacityAndWrappedWritesPreserveOrder() {
        val buffer = TerminalBytes(4096)
        buffer.append(ByteArray(4096) { 1 }, 4096)
        assertFalse(buffer.read(0).dropped)
        buffer.append(byteArrayOf(2, 3), 2)
        assertContentEquals(byteArrayOf(1, 1, 2, 3), buffer.read(4094).bytes)
        assertTrue(buffer.read(0).dropped)
    }
    @Test fun splitUtf8RemainsByteExact() {
        val buffer = TerminalBytes(4096)
        val bytes = "🌌极光".toByteArray()
        buffer.append(bytes, bytes.size)
        val first = buffer.read(0, 2)
        val next = buffer.read(first.next)
        assertContentEquals(bytes, first.bytes + next.bytes)
        assertEquals(bytes.size.toLong(), next.next)
    }
    @Test fun rejectsFutureNegativeAndUnboundedReads() {
        val buffer = TerminalBytes(4096)
        assertFailsWith<IllegalArgumentException> { buffer.read(1) }
        assertFailsWith<IllegalArgumentException> { buffer.read(-1) }
        assertFailsWith<IllegalArgumentException> { buffer.read(0, 65537) }
        assertFailsWith<IllegalArgumentException> { buffer.append(byteArrayOf(1), 2) }
    }
}
