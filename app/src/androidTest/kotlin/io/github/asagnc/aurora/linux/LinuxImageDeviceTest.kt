package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinuxImageDeviceTest {
    @LinuxImageRequired @Test fun missingHardlinksFailBeforeDownloadAndLeaveNoStaging() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraLinuxImageTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await()
        val probe = java.nio.file.Files.createTempDirectory(app.cacheDir.toPath(), "link-capability-")
        val supportsHardlinks = try {
            val source = java.nio.file.Files.createFile(probe.resolve("source"))
            try { java.nio.file.Files.createLink(probe.resolve("target"), source); true }
            catch (_: java.io.IOException) { false }
        } finally { io.github.asagnc.aurora.core.removeSkillTree(probe) }
        org.junit.Assume.assumeFalse("This negative case requires denied ordinary hardlinks", supportsHardlinks)
        org.junit.Assume.assumeTrue("This negative case requires an uninstalled base", app.linux.registry.current() == null)
        val failure = runCatching { app.linux.install() }.exceptionOrNull()
        assertTrue(failure.toString(), failure is LinuxInstallCapabilityException)
        assertTrue(app.linux.status.value.contains("硬链接"))
        val stage = app.filesDir.toPath().resolve("spaces/resources/.linux-staging")
        java.nio.file.Files.newDirectoryStream(stage).use { assertFalse(it.iterator().hasNext()) }
    }
    @LinuxImageRequired @Test fun officialImageRunsFixedOrdinaryProotWorkload() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraLinuxImageTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await()
        val previous = app.linux.registry.current()
        try { app.linux.install() }
        catch (unsupported: LinuxInstallCapabilityException) {
            assertEquals(previous, app.linux.registry.current())
            org.junit.Assume.assumeNoException("Ordinary hardlink capability unavailable; existing generation retained", unsupported)
        }
        val result = app.linux.probe()
        assertEquals(result.toString(), JsonPrimitive(true), result["workloadPassed"])
        assertEquals(JsonPrimitive(false), result["executionReady"])
    }
}
