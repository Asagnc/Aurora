package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.*
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Package/linker smoke test, deliberately independent of downloading a rootfs. */
@RunWith(AndroidJUnit4::class)
class LinuxNativePackageTest {
    @Test fun packagedProotStartsUnderOrdinaryAndroidUid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createDirectory(context.cacheDir.toPath().resolve("proot-package-${UUID.randomUUID()}"))
        val native = java.nio.file.Path.of(context.applicationInfo.nativeLibraryDir)
        try {
            listOf("libaurora_proot.so", "libaurora_proot_loader.so", "libaurora_talloc.so").forEach {
                assertTrue("Missing packaged $it", Files.isRegularFile(native.resolve(it)))
            }
            val result = NativeCommandRunner(context).execute(root, CommandRequest(
                listOf(native.resolve("libaurora_proot.so").toString(), "--version"), timeoutMs = 10_000,
                environment = mapOf("LD_LIBRARY_PATH" to native.toString(), "PROOT_TMP_DIR" to root.toString())))
            assertEquals(result.toString(), 0L, result.number("exitCode"))
            assertTrue(result.toString(), result["stdout"]!!.jsonObject.text("text")!!.contains("5.1.107.98"))
        } finally { removeSkillTree(root) }
    }
}
