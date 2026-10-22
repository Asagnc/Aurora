package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootRequired
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit real-image qualification. Never downloads or substitutes a host shell. */
@RunWith(AndroidJUnit4::class)
class RootLinuxRunDeviceTest {
    @RootRequired @LinuxImageRequired @Test fun guestExitFilesExportAndConversationDeletion() = runBlocking {
        fixture { app, owner ->
            val result = app.linux.runs.execute(owner, listOf("/bin/sh", "-c",
                "set -eu; test \"\$(pwd)\" = /mnt; printf '极光😀' > /mnt/result.txt; chmod 600 /mnt/result.txt; exit 37"))
            assertEquals(result.toString(), JsonPrimitive(true), result["stopped"])
            assertEquals(result.toString(), 37L, result["completion"]!!.jsonObject.number("exitCode"))
            assertEquals(0, app.linux.runs.pendingCount())
            val first = read(app, owner, "project/result.txt", 0, 4)
            assertEquals("极", first.text("text"))
            assertEquals(3L, first.number("next"))
            assertEquals("光😀", read(app, owner, "project/result.txt", 3, 32).text("text"))
            val client = requireNotNull(app.root.linuxFileClient)
            val exported = client.invoke(owner, "export", objectOf("path" to string("project/result.txt")))
            val root = app.spaces.commandRoot(owner)
            val path = requireNotNull(exported.text("path"))
            assertTrue(path.matches(Regex("exports/root-[0-9a-f-]{36}\\.bin")))
            assertEquals("极光😀", String(Files.readAllBytes(root.resolve(path)), Charsets.UTF_8))
            val edit = objectOf("edits" to JsonArray(listOf(objectOf("path" to string("project/result.txt"),
                "expectedVersion" to requireNotNull(first["version"]), "text" to string("updated")))))
            assertEquals("committed", client.invoke(owner, "files_edit_batch", edit).text("status"))
            try { client.invoke(owner, "files_edit_batch", edit); fail("Stale version was accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals("updated", read(app, owner, "project/result.txt", 0, 32).text("text"))
            app.spaces.delete(owner, {})
            assertFalse(Files.exists(root))
        }
    }

    @RootRequired @LinuxImageRequired @Test fun timeoutStopsBackgroundWriterBeforeLeaseRelease() = runBlocking {
        fixture { app, owner ->
            val result = app.linux.runs.execute(owner, listOf("/bin/sh", "-c", WRITER), timeoutMs = 1_000)
            assertEquals(result.toString(), JsonPrimitive(true), result["stopped"])
            assertEquals(0, app.linux.runs.pendingCount())
            assertWriterStopped(app, owner)
        }
    }

    @RootRequired @LinuxImageRequired @Test fun cancellationJoinsGuestAndRecoversLease() = runBlocking {
        fixture { app, owner ->
            coroutineScope {
                val task = async { app.linux.runs.execute(owner, listOf("/bin/sh", "-c", WRITER)) }
                try {
                    withTimeout(20_000) {
                        while (read(app, owner, "project/ticks", 0, 32).text("text").isNullOrEmpty()) {
                            check(!task.isCompleted) { "Guest exited before readiness" }
                            delay(100)
                        }
                    }
                    val edit = objectOf("edits" to JsonArray(listOf(objectOf("path" to string("project/forbidden"),
                        "expectedVersion" to JsonNull, "text" to string("must not be written")))))
                    try { requireNotNull(app.root.linuxFileClient).invoke(owner, "files_edit_batch", edit); fail("Active workspace allowed edits") }
                    catch (_: IllegalArgumentException) { }
                } finally { task.cancelAndJoin() }
                assertTrue(task.isCancelled)
            }
            assertEquals(0, app.linux.runs.pendingCount())
            assertWriterStopped(app, owner)
        }
    }

    private suspend fun assertWriterStopped(app: AuroraApplication, owner: String) {
        val before = read(app, owner, "project/ticks", 0, 32)
        assertNull(before["error"]?.takeUnless { it == JsonNull })
        assertTrue(before.number("size")!! > 0)
        delay(500)
        val after = read(app, owner, "project/ticks", 0, 32)
        assertEquals(before["size"], after["size"])
        assertEquals(before["version"], after["version"])
    }

    private suspend fun read(app: AuroraApplication, owner: String, path: String, offset: Long, limit: Int): JsonObject {
        val response = requireNotNull(app.root.linuxFileClient).invoke(owner, "files_read_batch",
            objectOf("files" to JsonArray(listOf(objectOf("path" to string(path),
                "offset" to JsonPrimitive(offset), "limit" to JsonPrimitive(limit))))))
        return response.array("files").single().jsonObject
    }

    private suspend fun fixture(block: suspend (AuroraApplication, String) -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraLinuxImageTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await()
        app.root.connect()
        var owner: String? = null
        var original: Throwable? = null
        try {
            check(app.root.linuxRunClient != null && app.root.linuxFileClient != null) { "Reconnect current Root service" }
            val current = app.linux.registry.current()
            check(app.linux.registry.versions().any { it.id == current && it.manifest.backend == LinuxBackend.CHROOT }) {
                "Prepare and select a real Root Linux generation first"
            }
            owner = app.conversations.create("Linux qualification")
            block(app, owner)
        } catch (failure: Throwable) { original = failure; throw failure
        } finally {
            // Failed recovery must retain the owner and its lease for diagnosis.
            withContext(NonCancellable) { try {
                try { owner?.let { app.spaces.delete(it, {}) } }
                catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
            } finally { app.root.disconnect() } }
        }
    }

    private companion object {
        const val WRITER = "set -eu; (while :; do printf x >> /mnt/ticks; sleep 0.1; done) & wait"
    }
}
