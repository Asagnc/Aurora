package io.github.asagnc.aurora.root

import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.Os
import android.system.OsConstants.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.removeSkillTree
import io.github.asagnc.aurora.core.WireJson
import io.github.asagnc.aurora.core.number
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android FD/filesystem checks in owned data. Only the explicit hard-link
 * fixture uses Root, because this device denies link creation to ordinary UID. */
@RunWith(AndroidJUnit4::class)
class RootManagedExistingArtifactTest {
    @Test fun exactExistingCopyCanBeReused() { fixture { source, directory ->
        Files.copy(source, directory.resolve("frida.log"))
        Os.chmod(directory.resolve("frida.log").toString(), 384)
        verify(source, directory)
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(directory.resolve("frida.log")))
    } }

    @Test fun differentBytesAreRejectedAndNeverOverwritten() { fixture { source, directory ->
        val target = directory.resolve("frida.log")
        Files.write(target, "other\n".toByteArray()); Os.chmod(target.toString(), 384)
        assertThrows(Exception::class.java) { verify(source, directory) }
        assertEquals("other\n", Files.readAllBytes(target).toString(Charsets.UTF_8))
    } }

    @Test fun truncatedCopyIsRetainedAndRejected() { fixture { source, directory ->
        val target = directory.resolve("frida.log")
        Files.write(target, byteArrayOf(1)); Os.chmod(target.toString(), 384)
        assertThrows(Exception::class.java) { verify(source, directory) }
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(target))
    } }

    @Test fun symbolicLinkIsNotFollowed() { fixture { source, directory ->
        Files.createSymbolicLink(directory.resolve("frida.log"), source)
        assertThrows(Exception::class.java) { verify(source, directory) }
        assertTrue(Files.isSymbolicLink(directory.resolve("frida.log")))
        assertEquals("owned\n", Files.readAllBytes(source).toString(Charsets.UTF_8))
    } }

    @RootRequired @Test fun hardLinkCreatedByRootIsRejected() = runBlocking<Unit> {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); app.root.connect()
        val owner = app.conversations.create("Aurora Frida log link qualification fixture")
        try {
            app.spaces.conversation(owner)
            fixture { source, directory ->
                val reply = runBlocking { requireNotNull(app.root.commandClient).execute(owner,
                    CommandRequest(listOf("/system/bin/ln", source.toString(), directory.resolve("frida.log").toString()))) }
                assertEquals(reply, 0L, WireJson.parseToJsonElement(reply).jsonObject.number("exitCode"))
                assertThrows(Exception::class.java) { verify(source, directory) }
                assertEquals(2L, Os.stat(source.toString()).st_nlink)
            }
        } finally {
            try { app.spaces.delete(owner, app.tasks::quiesce) } finally { app.root.disconnect() }
        }
    }

    @Test fun broaderPermissionsAreRejectedWithoutChmod() { fixture { source, directory ->
        val target = directory.resolve("frida.log")
        Files.copy(source, target); Os.chmod(target.toString(), 420)
        assertThrows(Exception::class.java) { verify(source, directory) }
        assertEquals(420, Os.stat(target.toString()).st_mode and 4095)
    } }

    private fun verify(source: Path, directory: Path) {
        val original = Os.open(source.toString(), O_RDONLY or O_CLOEXEC, 0)
        val parent = Os.open(directory.toString(), O_RDONLY or O_NONBLOCK or O_NOFOLLOW or O_CLOEXEC, 0)
        try { ParcelFileDescriptor.dup(parent).use {
            RootManagedExistingArtifact.verify(original, Os.fstat(original), it, Process.myUid())
        } } finally { Os.close(parent); Os.close(original) }
    }

    private fun fixture(block: (Path, Path) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val temporary = Files.createTempDirectory(context.filesDir.toPath(), "owned-frida-log-")
        try {
            val source = temporary.resolve("original.log")
            Files.write(source, "owned\n".toByteArray()); Os.chmod(source.toString(), 384)
            block(source, Files.createDirectory(temporary.resolve("run")))
        } finally { removeSkillTree(temporary) }
    }
}
