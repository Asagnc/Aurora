package io.github.asagnc.aurora.core

import kotlin.test.*

class SkillTextSliceTest {
    @Test fun byteBoundaryPreservesChineseAndEmojiAcrossReads() {
        listOf("中文", "😀完毕").forEach { suffix ->
            val prefix = "a".repeat(65535)
            val bytes = (prefix + suffix).toByteArray()
            val first = decodeSkillText(bytes.copyOfRange(0, 65536), false)
            assertEquals(prefix, first.text); assertEquals(65535, first.consumed)
            val second = decodeSkillText(bytes.copyOfRange(first.consumed, bytes.size), true)
            assertEquals(suffix, second.text)
            assertEquals(bytes.size, first.consumed + second.consumed)
        }
    }
    @Test fun malformedAndTruncatedEndDoNotMasqueradeAsText() {
        assertNull(decodeSkillText(byteArrayOf(0xff.toByte()), true).text)
        val truncated = "中".toByteArray().copyOf(2)
        assertNull(decodeSkillText(truncated, true).text)
        val pending = decodeSkillText(truncated, false)
        assertEquals("", pending.text); assertEquals(0, pending.consumed)
    }
    @Test fun emptyEndOfFileAndFullUnicodeConsumeExactlyTheirBytes() {
        assertEquals(SkillTextSlice("", 0), decodeSkillText(byteArrayOf(), true))
        val text = "🌌 Aurora 极光"
        assertEquals(SkillTextSlice(text, text.toByteArray().size), decodeSkillText(text.toByteArray(), true))
    }
}
