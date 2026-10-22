package io.github.asagnc.aurora.core

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class Utf8FileSliceTest {
    @Test fun cutChineseCharacterDoesNotDiscardTheEntireSlice() {
        val bytes = "极光".toByteArray(Charsets.UTF_8)
        val result = decodeUtf8FileSlice(bytes.copyOf(4))
        assertEquals("极", result.text); assertEquals(3, result.consumed)
        assertEquals("光", decodeUtf8FileSlice(bytes.copyOfRange(result.consumed, bytes.size)).text)
    }
    @Test fun binaryAndTooSmallIncompleteSlicesRemainOpaque() {
        val binary = decodeUtf8FileSlice(byteArrayOf(0xff.toByte(), 0, 1))
        assertNull(binary.text); assertEquals(3, binary.consumed)
        assertNull(decodeUtf8FileSlice(byteArrayOf(65, 0xff.toByte())).text)
        assertNull(decodeUtf8FileSlice(byteArrayOf(65, 0xe6.toByte()), allowPartialEnd = false).text)
        assertNull(decodeUtf8FileSlice("极".toByteArray().copyOf(1)).text)
        assertEquals(Utf8FileSlice("", 0), decodeUtf8FileSlice(byteArrayOf()))
    }
    @Test fun fileBatchContinuationRetainsEveryUnicodeCharacter() = runTest {
        val root = Files.createTempDirectory("utf8-slice-")
        try {
            Files.writeString(root.resolve("file"), "极光😀")
            val files = OwnedFiles(root)
            var offset = 0L; val text = StringBuilder()
            do {
                val slice = files.readBatch(listOf(FileRead("file", offset, 4))).single()
                text.append(requireNotNull(slice.text))
                val next = slice.next
                if (next != null) { assertTrue(next > offset); offset = next }
            } while (next != null)
            assertEquals("极光😀", text.toString())
        } finally { removeSkillTree(root) }
    }
}
