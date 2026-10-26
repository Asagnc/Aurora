package io.github.asagnc.aurora.root

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.removeSkillTree
import java.nio.file.*
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Ordinary-UID Android FD/streaming tests. Compiling does not establish Android or Root qualification. */
@RunWith(AndroidJUnit4::class)
class RootLinuxExportTest {
    private suspend fun fixture(block: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "linux-export-")
        try { block(root) } finally { removeSkillTree(root) }
    }

    @Test fun exactSelectedBytesAreSyncedAndExistingTargetsAreNeverReplaced() = runBlocking { fixture { root ->
        val data = ByteArray(150000) { (it % 251).toByte() }
        val source = root.resolve("source"); Files.write(source, data)
        val hash = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) }
        val target = root.resolve("target")
        assertEquals(hash, RootLinuxExport.copy(source, target, 200000, hash)); assertArrayEquals(data, Files.readAllBytes(target))
        try { RootLinuxExport.copy(source, target, 200000, hash); fail("Existing target was replaced") } catch (_: FileAlreadyExistsException) { }
        assertArrayEquals(data, Files.readAllBytes(target))
    } }

    @Test fun byteBudgetAndSelectedDigestFailuresCannotReturnAValidExport() = runBlocking { fixture { root ->
        val source = root.resolve("source"); Files.write(source, "owned-certificate-fixture".toByteArray())
        val target = root.resolve("target")
        try { RootLinuxExport.copy(source, target, 4, null); fail("Budget ignored") } catch (_: IllegalArgumentException) { }
        assertFalse(Files.exists(target))
        try { RootLinuxExport.copy(source, target, 4096, "0".repeat(64)); fail("Wrong digest accepted") } catch (_: IllegalArgumentException) { }
        // This private staged output belongs to the export caller's finally-cleanup, never its published destination.
        assertTrue(Files.exists(target))
    } }

    @Test fun fifoAndSymlinkSourcesFailWithoutBlocking() = runBlocking { fixture { root ->
        val fifo = root.resolve("fifo"); Os.mkfifo(fifo.toString(), 384)
        withTimeout(2000) {
            try { RootLinuxExport.copy(fifo, root.resolve("target"), 4096, null); fail("FIFO accepted") } catch (_: IllegalArgumentException) { }
        }
        val link = root.resolve("link"); Files.createSymbolicLink(link, fifo)
        try { RootLinuxExport.copy(link, root.resolve("target"), 4096, null); fail("Symlink accepted") } catch (_: android.system.ErrnoException) { }
        assertFalse(Files.exists(root.resolve("target")))
    } }
}
