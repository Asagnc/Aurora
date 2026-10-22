package io.github.asagnc.aurora.linux

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootRequired
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit public-image download and Root filesystem test; not part of the ordinary gate. */
@RunWith(AndroidJUnit4::class)
class RootLinuxInstallDeviceTest {
    @RootRequired @LinuxImageRequired @Test fun rootBatchPublishesOriginalOwnershipWithoutConvertingOrdinaryGeneration() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("auroraRootTest") == "true" && arguments.getString("auroraLinuxImageTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await()
        val previous = app.linux.registry.versions()
        org.junit.Assume.assumeTrue("A fresh Root generation is required", previous.none { it.manifest.backend == LinuxBackend.CHROOT })
        app.root.connect()
        try {
            assertTrue("Root Linux ownership protocol unavailable", app.root.rootImagesAvailable)
            val version = app.linux.install()
            assertEquals(LinuxBackend.CHROOT, version.manifest.backend)
            assertEquals(version.manifest.generationId(), version.id)
            val directory = app.linux.directory(version.id)
            val plan = WireJson.decodeFromString<List<RootfsEntry>>(Files.readString(directory.resolve("entries.json")))
            val root = directory.resolve("rootfs")
            val links = plan.filter { it.kind == "hardlink" }
            assertTrue("Official image has no hardlinks to exercise", links.isNotEmpty())
            links.forEach { entry ->
                val target = root.resolve(entry.path)
                val source = resolveRootfsPath(root, entry.target)
                assertTrue(Files.isSameFile(source, target))
                assertEquals(0, Os.lstat(target.toString()).st_uid)
            }
            LinuxElf.validateRootfs(root, app.linux.image.architecture)
            assertEquals(version.id, app.linux.registry.current())
            previous.forEach { old ->
                assertTrue(app.linux.registry.versions().any { it.id == old.id })
                assertEquals(app.applicationInfo.uid, Os.lstat(app.linux.directory(old.id).resolve("rootfs").toString()).st_uid)
            }
            assertEquals(0, Os.lstat(root.toString()).st_uid)
            val stage = app.filesDir.toPath().resolve("spaces/resources/.linux-staging")
            Files.newDirectoryStream(stage).use { assertFalse(it.iterator().hasNext()) }
        } finally { app.root.disconnect() }
    }
}
