package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ImageArtifactTest {
    @Test fun referenceIsAnIdentifierNotAPathOrAnotherOwner() {
        val id = UUID.randomUUID().toString()
        assertEquals(id, imageArtifactId("aurora-image:$id"))
        for (value in listOf("aurora-image:../secret", "aurora-image:$id/extra", "file:///secret", "https://example.com/$id"))
            assertNull(imageArtifactId(value))
    }
    @Test fun descriptionCannotInjectAnotherMarkdownImage() {
        val image = ImageArtifact(UUID.randomUUID().toString(), "image/png", 1, 1, 1, "hash", "a] [b\\c\n")
        assertEquals("![a\\] \\[b\\\\c ](${image.link})", imageMarkdown(image))
    }
    @Test fun longPreviewPreservesCompleteSourceAndUnicode() {
        val text = "x".repeat(23999) + "😀" + "tail"
        assertEquals(23999, replyPreviewEnd(text))
        assertEquals("😀tail", text.substring(replyPreviewEnd(text)))
    }
    @Test fun galleryPreviewDoesNotComposeEveryImage() {
        val text = (1..12).joinToString("\n\n") { "![image$it](https://example.com/$it.png)" }
        val preview = text.take(replyPreviewEnd(text))
        assertTrue("image4" in preview)
        assertFalse("image5" in preview)
        assertTrue("image12" in text)
    }
    @Test fun binarySnapshotRejectsOversizeAndTraversal() = runTest {
        val root = Files.createTempDirectory("aurora-image-read")
        try {
            Files.write(root.resolve("image"), byteArrayOf(1, 2, 3))
            val owned = OwnedFiles(root)
            assertContentEquals(byteArrayOf(1, 2, 3), owned.readBinary("image", 3))
            assertFailsWith<IllegalArgumentException> { owned.readBinary("image", 2) }
            assertFailsWith<IllegalArgumentException> { owned.readBinary("../image", 8) }
        } finally { Files.deleteIfExists(root.resolve("image")); Files.delete(root) }
    }
}
