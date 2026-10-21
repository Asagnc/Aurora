package io.github.asagnc.aurora.core

import java.io.*
import java.nio.file.*
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.*
import kotlin.test.*

class SkillArchiveTransferTest {
    private fun fixture(block: suspend CoroutineScope.(Path) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("aurora-skill-transfer")
        try { withTimeout(10_000) { block(root) } } finally { removeSkillTree(root) }
    }
    @Test fun successfulImportClosesItsOwnedStream() = fixture { root ->
        var closed = false
        val input = object : ByteArrayInputStream("archive bytes".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        SkillArchiveTransfer.copy(root.resolve("copy"), { input })
        assertTrue(closed); assertEquals("archive bytes", Files.readString(root.resolve("copy")))
    }
    @Test fun cancellationClosesStreamEvenWhenReadIgnoresThreadInterruption() = fixture { root ->
        val entered = CompletableDeferred<Unit>(); val released = CountDownLatch(1)
        val input = object : InputStream() {
            override fun read(): Int {
                entered.complete(Unit)
                while (true) {
                    try { released.await(); throw IOException("closed") }
                    catch (_: InterruptedException) { /* Provider ignores interrupt; close must release it. */ }
                }
            }
            override fun close() { released.countDown() }
        }
        val task = launch { SkillArchiveTransfer.copy(root.resolve("partial"), { input }) }
        entered.await(); task.cancelAndJoin()
        assertTrue(task.isCancelled); assertEquals(0L, released.count)
        // Removal is safe only after the transfer has joined and released its writer.
        Files.delete(root.resolve("partial"))
    }
    @Test fun cancellationDuringOpenClosesStreamThatArrivesAfterCancellation() = fixture { root ->
        val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { closed = true; super.close() }
        }
        val task = launch { SkillArchiveTransfer.copy(root.resolve("target"), {
            entered.complete(Unit)
            while (true) { try { release.await(); break } catch (_: InterruptedException) { } }
            input
        }, { release.countDown() }) }
        entered.await(); task.cancelAndJoin()
        assertTrue(closed); assertFalse(Files.exists(root.resolve("target")))
    }
    @Test fun importNeverOverwritesExistingArchive() = fixture { root ->
        val target = Files.writeString(root.resolve("existing"), "keep")
        assertFailsWith<FileAlreadyExistsException> { SkillArchiveTransfer.copy(target, { ByteArrayInputStream(byteArrayOf(1)) }) }
        assertEquals("keep", Files.readString(target))
    }
}
