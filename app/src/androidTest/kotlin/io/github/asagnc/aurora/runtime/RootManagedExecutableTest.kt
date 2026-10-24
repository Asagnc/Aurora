package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.removeSkillTree
import io.github.asagnc.aurora.root.RootManagedExecutable
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Loader/pin checks only. Runtime cgroup, ART and Frida qualification remain separate. */
@RunWith(AndroidJUnit4::class)
class RootManagedExecutableTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val binary get() = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_managed_guard.so")
    private fun digest(): String = Files.newInputStream(binary).use { input ->
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65_536)
        while (true) { val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Test fun checkedDescriptorStartsThePackagedGuard() = runBlocking { withContext(Dispatchers.IO) {
        RootManagedExecutable.open(binary, digest()).use { pinned -> pinned.usePath { path ->
            assertTrue(path.startsWith("/proc/${android.os.Process.myPid()}/fd/"))
            val child = ProcessBuilder(path, "--version").redirectErrorStream(true).start()
            try {
                child.outputStream.close()
                assertTrue(child.waitFor(5, TimeUnit.SECONDS))
                assertEquals(0, child.exitValue())
                assertTrue(child.inputStream.readNBytes(4096).toString(Charsets.UTF_8).contains("aurora-managed-guard"))
            } finally { if (child.isAlive) child.destroyForcibly(); child.waitFor(); child.inputStream.close(); child.errorStream.close() }
        } }
    } }

    @Test fun wrongQualifiedDigestIsRejected() = runBlocking {
        try { RootManagedExecutable.open(binary, "0".repeat(64)).use { fail("Wrong digest admitted") } }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun appOwnedExecutableAndSymbolicLinkAreRejected() = runBlocking {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "managed-pin-")
        try {
            val writable = directory.resolve("worker")
            Files.copy(binary, writable)
            android.system.Os.chmod(writable.toString(), 0x1C0)
            try { RootManagedExecutable.open(writable, digest()).use { fail("App-owned worker admitted") } }
            catch (_: IllegalArgumentException) { }
            val link = Files.createSymbolicLink(directory.resolve("link"), binary)
            try { RootManagedExecutable.open(link, digest()).use { fail("Executable link admitted") } }
            catch (_: android.system.ErrnoException) { }
        } finally { removeSkillTree(directory) }
    }

    @Test fun closingPinPreventsLaterLaunch() = runBlocking {
        val pin = RootManagedExecutable.open(binary, digest())
        pin.close(); pin.close()
        try { pin.usePath { fail("Closed descriptor admitted") } }
        catch (_: IllegalStateException) { }
    }

    @Test fun sameVersionScopesDoNotSerializeOrCloseEachOthersDescriptors() = runBlocking {
        val pin = RootManagedExecutable.open(binary, digest())
        val ready = CountDownLatch(2); val finish = CountDownLatch(1)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = List(2) { Thread {
            try { pin.usePath { path ->
                ready.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                check(Files.isRegularFile(Path.of(path)))
            } } catch (failure: Throwable) { failures.add(failure); ready.countDown() }
        } }
        try {
            threads.forEach { it.start() }
            assertTrue("Separate executions must enter concurrently", ready.await(3, TimeUnit.SECONDS))
            pin.close()
        } finally {
            finish.countDown(); threads.forEach { it.join(6_000) }; pin.close()
        }
        assertTrue(threads.none { it.isAlive })
        failures.firstOrNull()?.let { throw it }
    }
}
