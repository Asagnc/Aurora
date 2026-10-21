package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.UUID
import kotlin.test.*

class CommandLogTailTest {
    @Test fun capturesExistingOutputAndSubsequentAppendsInSeparateStreams() = fixture { root, directory ->
        val path = Files.createDirectories(root.resolve(directory))
        Files.write(path.resolve("stdout.bin"), "first".toByteArray())
        val reader = CommandLogTail(root, directory)
        assertEquals("first", reader.snapshot().stdout.text)
        Files.write(path.resolve("stdout.bin"), "+next".toByteArray(), StandardOpenOption.APPEND)
        Files.write(path.resolve("stderr.bin"), "error".toByteArray())
        val next = reader.snapshot()
        assertEquals("first+next", next.stdout.text)
        assertEquals(10L, next.stdout.bytes)
        assertEquals("error", next.stderr.text)
    }
    @Test fun splitUtf8SequenceIsNotReplacedWhileCommandIsRunning() = fixture { root, directory ->
        val path = Files.createDirectories(root.resolve(directory)).resolve("stdout.bin")
        val bytes = "你好😀".toByteArray()
        Files.write(path, bytes.copyOfRange(0, 7))
        val reader = CommandLogTail(root, directory)
        assertEquals("你好", reader.snapshot().stdout.text)
        Files.write(path, bytes.copyOfRange(7, bytes.size), StandardOpenOption.APPEND)
        assertEquals("你好😀", reader.snapshot(complete = true).stdout.text)
    }
    @Test fun largeBurstsKeepBoundedTailAndExactReadOffset() = fixture { root, directory ->
        val path = Files.createDirectories(root.resolve(directory)).resolve("stdout.bin")
        Files.write(path, ("x".repeat(200000) + "done").toByteArray())
        val output = CommandLogTail(root, directory).snapshot().stdout
        assertEquals(CommandLogTail.LIMIT, output.text.length)
        assertTrue(output.text.endsWith("done"))
        assertTrue(output.previewClipped)
        assertEquals(200004L, output.bytes)
    }
    @Test fun truncationResetsOldTail() = fixture { root, directory ->
        val path = Files.createDirectories(root.resolve(directory)).resolve("stdout.bin")
        Files.write(path, "old-output".toByteArray())
        val reader = CommandLogTail(root, directory)
        reader.snapshot()
        Files.write(path, "new".toByteArray(), StandardOpenOption.TRUNCATE_EXISTING)
        val output = reader.snapshot().stdout
        assertEquals("new", output.text)
        assertTrue(output.reset)
    }
    @Test fun pendingDirectoryIsNotCreatedByReader() = fixture { root, directory ->
        assertTrue(CommandLogTail(root, directory).snapshot().pending)
        assertFalse(Files.exists(root.resolve(directory)))
        assertNull(commandArtifactId("runs/../secret"))
        assertFailsWith<IllegalArgumentException> { CommandLogTail(root, "../secret") }
    }
    @Test fun terminalIncompleteUtf8IsReportedWithoutLosingBytes() = fixture { root, directory ->
        val path = Files.createDirectories(root.resolve(directory)).resolve("stdout.bin")
        Files.write(path, byteArrayOf(0xe4.toByte(), 0xbd.toByte()))
        val reader = CommandLogTail(root, directory)
        assertEquals("", reader.snapshot().stdout.text)
        val terminal = reader.snapshot(complete = true).stdout
        assertEquals("�", terminal.text)
        assertEquals(2L, terminal.bytes)
    }

    private fun fixture(block: (Path, String) -> Unit) {
        val root = Files.createTempDirectory("aurora-log-tail")
        try { block(root, "runs/${UUID.randomUUID()}") }
        finally { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
