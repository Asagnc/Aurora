package io.github.asagnc.aurora.root

import android.system.ErrnoException
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.asagnc.aurora.core.RootScriptSnapshot
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootScriptMemoryTest {
    private val bytes = "send('Aurora')".toByteArray()
    private fun expected() = RootScriptSnapshot("fixture", MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }, bytes.size.toLong())
    @Test fun immutableAcrossDuplicationAndReceiverDoesNotMoveOffset() = runBlocking {
        val expected = expected()
        RootScriptMemory.capture(ByteArrayInputStream(bytes), expected).use { original ->
            android.os.ParcelFileDescriptor.dup(original.fileDescriptor).use { duplicate ->
                original.close()
                val fd = duplicate.fileDescriptor
                RootScriptMemory.verify(fd, expected)
                assertEquals(0L, Os.lseek(fd, 0, android.system.OsConstants.SEEK_CUR))
                try { Os.write(fd, byteArrayOf(0), 0, 1); fail("Sealed snapshot modified") }
                catch (_: ErrnoException) { }
                for (size in listOf(0L, expected.bytes + 1)) {
                    try { Os.ftruncate(fd, size); fail("Sealed snapshot resized") } catch (_: ErrnoException) { }
                }
                RootScriptMemory.verify(fd, expected)
            }
        }
    }
    @Test fun changedSourceOrDigestFailsAndClosesInput() = runBlocking {
        for (metadata in listOf(expected().copy(bytes = 1), expected().copy(bytes = bytes.size + 1L),
            expected().copy(sha256 = "a".repeat(64)))) {
            var closed = false
            val input = object : ByteArrayInputStream(bytes) { override fun close() { closed = true; super.close() } }
            try { RootScriptMemory.capture(input, metadata).close(); fail("Changed source accepted") }
            catch (_: IllegalArgumentException) { }
            assertTrue(closed)
        }
    }
    @Test fun closeFailureAndCancellationDoNotLeakSnapshots() = runBlocking {
        fun snapshots(): Int = java.io.File("/proc/self/fd").listFiles().orEmpty().count {
            try { Os.readlink(it.absolutePath).contains("memfd:aurora-script") } catch (_: ErrnoException) { false }
        }
        val before = snapshots()
        val failing = object : ByteArrayInputStream(bytes) { override fun close() { throw java.io.IOException("fixture close") } }
        try { RootScriptMemory.capture(failing, expected()).close(); fail("Close error swallowed") }
        catch (_: java.io.IOException) { }
        assertEquals(before, snapshots())
        kotlinx.coroutines.coroutineScope {
            val job = kotlinx.coroutines.Job(coroutineContext[kotlinx.coroutines.Job])
            var closed = false
            val input = object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    job.cancel(); return super.read(buffer, offset, length)
                }
                override fun close() { closed = true; super.close() }
            }
            try { kotlinx.coroutines.withContext(job) { RootScriptMemory.capture(input, expected()).close() }; fail("Cancellation swallowed") }
            catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(closed)
        }
        assertEquals(before, snapshots())
    }
    @Test fun receiverRejectsUnsealedOrDifferentContent() = runBlocking {
        val raw = Os.memfd_create("aurora-unsealed-test", 3)
        try {
            Os.write(raw, bytes, 0, bytes.size)
            try { RootScriptMemory.verify(raw, expected()); fail("Unsealed input accepted") }
            catch (_: IllegalArgumentException) { }
        } finally { Os.close(raw) }
        RootScriptMemory.capture(ByteArrayInputStream(bytes), expected()).use {
            try { RootScriptMemory.verify(it.fileDescriptor, expected().copy(sha256 = "a".repeat(64))); fail("Wrong digest accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
