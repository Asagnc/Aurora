package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinuxVersionCleanerTest {
    @Test fun missingRootRetainsRootGenerationWhileOrdinaryCleanupStillWorks() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "generation-cleaner-")
        try {
            val image = UbuntuBaseImages.current(LinuxArchitecture.ARM64)
            val manifests = listOf(LinuxRootfsManifest(image, 1, 1), LinuxRootfsManifest(image, 1, 1, LinuxBackend.CHROOT))
            manifests.forEach { manifest ->
                val directory = Files.createDirectory(root.resolve(manifest.generationId()))
                Files.writeString(directory.resolve("manifest.json"), WireJson.encodeToString(manifest))
            }
            val cleaner = LinuxVersionCleaner(app, root) { null }
            val ordinary = manifests[0].generationId(); val privileged = manifests[1].generationId()
            assertFalse(cleaner.available(privileged)); assertFalse(cleaner.remove(privileged))
            assertTrue(Files.isDirectory(root.resolve(privileged)))
            assertTrue(cleaner.available(ordinary)); assertTrue(cleaner.remove(ordinary))
            assertFalse(Files.exists(root.resolve(ordinary)))
            assertTrue(Files.isRegularFile(root.resolve(privileged).resolve("manifest.json")))
        } finally { removeSkillTree(root) }
    }
}
