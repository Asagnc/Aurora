package io.github.asagnc.aurora.core

import kotlin.test.*

class RootFdFrameTest {
    private val codec = RootFdFrame("11".repeat(32), "22".repeat(32), 1)
    @Test fun canonicalNativeFixtureAndRolesAgree() {
        val hello = codec.encode(1)
        assertContentEquals("AFD1".toByteArray() + byteArrayOf(1, 1, 0, 0) + ByteArray(32) { 17 } + ByteArray(32) { 34 }, hello)
        assertTrue(codec.matches(1, hello))
        assertFalse(codec.matches(2, hello)); assertFalse(codec.matches(3, hello))
        hello[8] = 0
        assertFalse(codec.matches(1, hello))
        assertTrue(codec.matches(1, codec.encode(1)))
    }
    @Test fun malformedHeadersAndForeignTaskCannotPassHandshake() {
        val frame = codec.encode(2)
        for (index in listOf(0, 4, 5, 6, 7, 8, 39, 40, 71)) {
            val changed = frame.copyOf(); changed[index] = (changed[index].toInt() xor 1).toByte()
            assertFalse(codec.matches(2, changed))
        }
        assertFalse(codec.matches(2, frame.copyOf(71)))
        assertFalse(codec.matches(2, frame + byteArrayOf(0)))
        assertFalse(RootFdFrame("33".repeat(32), "22".repeat(32), 1).matches(2, frame))
        assertFalse(RootFdFrame("11".repeat(32), "44".repeat(32), 1).matches(2, frame))
        assertFalse(RootFdFrame("11".repeat(32), "22".repeat(32), 2).matches(2, frame))
    }
    @Test fun invalidNonceDigestOrDescriptorCountIsRejected() {
        for (count in listOf(0, 3, Int.MAX_VALUE)) assertFails { RootFdFrame("11".repeat(32), "22".repeat(32), count) }
        for (value in listOf("", "a".repeat(63), "A".repeat(64), "x".repeat(64))) {
            assertFails { RootFdFrame(value, "22".repeat(32), 1) }
            assertFails { RootFdFrame("11".repeat(32), value, 1) }
        }
        assertFails { codec.encode(0) }; assertFails { codec.encode(4) }
    }
}
