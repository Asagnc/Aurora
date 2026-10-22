package io.github.asagnc.aurora.core

import kotlin.test.*

class CommandOutputTextTest {
    @Test fun validPartialScalarsAreOmittedOnlyForClippedPreview() {
        for (text in listOf("é", "极", "😀")) {
            val scalar = text.toByteArray()
            for (end in 1 until scalar.size) {
                val bytes = "output:".toByteArray() + scalar.copyOf(end)
                assertEquals("output:", commandOutputText(bytes, truncated = true))
                assertNull(commandOutputText(bytes, truncated = false))
            }
            assertEquals("output:$text", commandOutputText("output:$text".toByteArray(), true))
        }
    }

    @Test fun clippingCannotHideInvalidOrBinarySuffix() {
        for (suffix in listOf(byteArrayOf(0xff.toByte()), byteArrayOf(0x80.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte()), byteArrayOf(0xf4.toByte(), 0x90.toByte()),
            byteArrayOf(0xe6.toByte(), 65), byteArrayOf(0), byteArrayOf(1))) {
            assertNull(commandOutputText("output:".toByteArray() + suffix, true))
        }
    }

    @Test fun terminalWhitespaceAndAnsiRemainReadable() {
        val text = "\t极光\r\n\u001b[32mok\u001b[0m"
        assertEquals(text, commandOutputText(text.toByteArray(), false))
        assertEquals("", commandOutputText(byteArrayOf(), false))
    }
}
