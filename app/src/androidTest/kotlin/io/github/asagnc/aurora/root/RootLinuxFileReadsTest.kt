package io.github.asagnc.aurora.root

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.nio.file.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Ordinary-UID filesystem tests; do not qualify Root or Android chroot. */
@RunWith(AndroidJUnit4::class)
class RootLinuxFileReadsTest {
    @Test fun boundedReadReportsContinuationAndVersionForEdits() = runBlocking {
        val root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "linux-read-")
        try {
            Files.createDirectory(root.resolve("project")); Files.writeString(root.resolve("project/file"), "abcdef")
            val result = RootLinuxFileReads.batch(root, JsonArray(listOf(objectOf("path" to string("project/file"),
                "offset" to JsonPrimitive(1), "limit" to JsonPrimitive(2)))))
            val file = result.array("files").single().jsonObject
            assertEquals("bc", file.text("text")); assertEquals(3L, file.number("next"))
            assertEquals(64, requireNotNull(file.text("version")).length)
        } finally { removeSkillTree(root) }
    }
    @Test fun fifoDoesNotBlockAndLinksCannotBeFileTargets() = runBlocking {
        val root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "linux-fifo-")
        try {
            Files.createDirectory(root.resolve("project")); Os.mkfifo(root.resolve("project/fifo").toString(), 384)
            val response = withTimeout(2000) { RootLinuxFileReads.batch(root, JsonArray(listOf(objectOf("path" to string("project/fifo"))))) }
            assertNotNull(response.array("files").single().jsonObject.text("error"))
            Files.createSymbolicLink(root.resolve("project/link"), root.resolve("project/fifo"))
            try { RootLinuxFileReads.resolve(root, "project/link"); fail("Symlink accepted") } catch (_: IllegalArgumentException) { }
            try { RootLinuxFileReads.resolve(root, "../outside"); fail("Traversal accepted") } catch (_: IllegalArgumentException) { }
        } finally { removeSkillTree(root) }
    }
}
