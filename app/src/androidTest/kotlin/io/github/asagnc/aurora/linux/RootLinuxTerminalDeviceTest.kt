package io.github.asagnc.aurora.linux

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootRequired
import io.github.asagnc.aurora.terminal.*
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual selected Ubuntu generation, Root Binder FD, foreground service and SQL lease. */
@RunWith(AndroidJUnit4::class)
class RootLinuxTerminalDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as AuroraApplication

    @RootRequired @LinuxImageRequired @Test fun linuxPtyUsesExistingRecordsInputResizeAndConfirmedExit() = runBlocking {
        fixture { owner ->
            val pending = app.linux.runs.pendingCount()
            val record = app.terminals.startLinux(owner, PtyRequest(listOf("/bin/sh", "-c",
                "test -t 0 && test -t 1 && test -t 2 || exit 91; printf ready; read value; printf '<%s>' \"\u0024value\"; stty size; exit 37"), timeoutMs = 20_000))
            assertEquals("linux-root", record.backend)
            val session = requireNotNull(app.terminals.session(owner, record.id))
            withTimeout(15_000) { session.version.first { session.bytes.sequence > 0 || !session.job.isActive } }
            assertTrue(app.terminals.read(owner, record.id).text("text").orEmpty().contains("ready"))
            assertEquals(pending + 1, app.linux.runs.pendingCount())
            app.terminals.resize(owner, record.id, 113, 41)
            app.terminals.write(owner, record.id, "极光😀\n")
            withTimeout(30_000) { session.job.join() }
            assertEquals("exited", session.record.state)
            assertEquals(37, session.record.exitCode)
            assertEquals(pending, app.linux.runs.pendingCount())
            val output = app.terminals.read(owner, record.id).text("text").orEmpty()
            assertTrue(output.contains("<极光😀>")); assertTrue(output.contains("41 113"))
            val saved = TerminalRecords(app.filesDir.toPath()).load(owner, record.id)
            assertEquals("linux-root", saved.backend); assertEquals("exited", saved.state)
        }
    }

    @RootRequired @LinuxImageRequired @Test fun stopJoinsLinuxRecoveryBeforeConversationCanBeDeleted() = runBlocking {
        fixture { owner ->
            val pending = app.linux.runs.pendingCount()
            val record = app.terminals.startLinux(owner, PtyRequest(listOf("/bin/sh", "-c",
                "printf ready; read value"), timeoutMs = 60_000))
            val session = requireNotNull(app.terminals.session(owner, record.id))
            withTimeout(15_000) { session.version.first { session.bytes.sequence > 0 || !session.job.isActive } }
            assertEquals(pending + 1, app.linux.runs.pendingCount())
            val stopped = withTimeout(40_000) { app.terminals.stop(owner, record.id) }
            assertNotEquals("exited", stopped.state)
            assertFalse(session.job.isActive)
            assertEquals(pending, app.linux.runs.pendingCount())
            val root = app.spaces.commandRoot(owner)
            app.spaces.delete(owner, app.tasks::quiesce)
            assertFalse(Files.exists(root))
        }
    }

    @RootRequired @LinuxImageRequired @Test fun guestDeadlineIsRecordedAsTimeoutAfterConfirmedCleanup() = runBlocking {
        fixture { owner ->
            val pending = app.linux.runs.pendingCount()
            val record = app.terminals.startLinux(owner, PtyRequest(listOf("/bin/sh", "-c", "printf ready; read value"), timeoutMs = 1_000))
            val session = requireNotNull(app.terminals.session(owner, record.id))
            withTimeout(30_000) { session.job.join() }
            assertEquals("timed_out", session.record.state)
            assertEquals(pending, app.linux.runs.pendingCount())
        }
    }

    private suspend fun fixture(block: suspend (String) -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraLinuxImageTest") == "true")
        app.ready.await()
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        app.root.connect()
        var owner: String? = null
        try {
            check(app.root.linuxTerminalClient != null) { "Reconnect current Root service" }
            val current = app.linux.registry.current()
            check(app.linux.registry.versions().any { it.id == current && it.manifest.backend == LinuxBackend.CHROOT })
            owner = app.conversations.create("Linux PTY qualification")
            block(owner)
        } finally {
            withContext(NonCancellable) { try {
                owner?.let { app.terminals.forget(it); app.spaces.delete(it, app.tasks::quiesce) }
            } finally { app.root.disconnect() } }
        }
    }
}
