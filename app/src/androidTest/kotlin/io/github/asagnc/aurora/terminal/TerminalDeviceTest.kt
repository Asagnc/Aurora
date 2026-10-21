package io.github.asagnc.aurora.terminal

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as AuroraApplication

    @Test fun imeCompositionCommitsChineseOnlyOnce() {
        val received = mutableListOf<String>()
        instrumentation.runOnMainSync {
            val view = TerminalCanvasView(app).apply { send = { received += it } }
            val connection = view.onCreateInputConnection(android.view.inputmethod.EditorInfo())
            connection.setComposingText("ni", 1)
            connection.setComposingText("你好", 1)
            assertTrue(received.isEmpty())
            connection.commitText("你好", 1)
            connection.finishComposingText()
        }
        assertEquals(listOf("你好"), received)
    }

    @Test fun nativePtyHasTerminalMergedOutputUnicodeAndWindowChanges() = runBlocking {
        fixture { owner ->
            val endpoint = PtyProcess(app).open(app.spaces.commandRoot(owner), PtyRequest(listOf("/system/bin/sh", "-c",
                "test -t 0 && test -t 1 && test -t 2 || exit 8; printf ready; read word; printf '<%s>' \"\u0024word\"; printf err >&2; stty size; exit 7")))
            val output = async(Dispatchers.IO) { String(endpoint.output.readBytes()) }
            try {
                endpoint.resize(113, 41)
                endpoint.write("极光🌌\n".toByteArray())
                assertEquals(PtyOutcome(7, "exited", null), withTimeout(5000) { endpoint.exit.await() })
                val text = withTimeout(5000) { output.await() }
                assertTrue(text.contains("<极光🌌>err")); assertTrue(text.contains("41 113"))
            } finally { endpoint.stop(); endpoint.exit.await(); endpoint.closeStreams(); output.cancelAndJoin() }
        }
    }

    @Test fun nativeDeadlineIsExplicitlyTimedOut() = runBlocking {
        fixture { owner ->
            val endpoint = PtyProcess(app).open(app.spaces.commandRoot(owner), PtyRequest(listOf("/system/bin/sleep", "60"), timeoutMs = 200))
            try {
                val outcome = withTimeout(5000) { endpoint.exit.await() }
                assertEquals("timed_out", outcome.state); assertNotEquals(0, outcome.exitCode)
            } finally { endpoint.stop(); endpoint.closeStreams() }
        }
    }

    @Test fun stopUnblocksAChildProducingOutputWithoutAViewer() = runBlocking {
        fixture { owner ->
            val endpoint = PtyProcess(app).open(app.spaces.commandRoot(owner), PtyRequest(listOf("/system/bin/sh", "-c", "while :; do echo output; done")))
            try {
                endpoint.stop()
                val result = runCatching { withTimeout(5000) { endpoint.exit.await() } }
                assertTrue(result.isFailure || result.getOrThrow().state != "exited")
            } finally { endpoint.closeStreams() }
        }
    }

    @Test fun screenParsesSplitUtf8AnsiAndAlternateBufferWithoutClipboardWrites() {
        val replies = mutableListOf<ByteArray>()
        val screen = TerminalScreen(PtyRequest()) { replies += it }
        val first = "极光🌌".toByteArray()
        screen.append(first.copyOfRange(0, 2), 2)
        screen.append(first.copyOfRange(2, first.size), first.size - 2)
        assertTrue(screen.snapshot().contains("极光🌌"))
        val ansi = "\u001b[2J\u001b[H\u001b[31mred\u001b[0m".toByteArray()
        screen.append(ansi, ansi.size)
        assertTrue(screen.snapshot().startsWith("red")); assertFalse(screen.snapshot().contains("[31m"))
        val alternate = "\u001b[?1049hALT\u001b[?1049l".toByteArray()
        screen.append(alternate, alternate.size)
        assertTrue(screen.snapshot().startsWith("red"))
        val clipboard = "\u001b]52;c;c2VjcmV0\u0007".toByteArray()
        screen.append(clipboard, clipboard.size)
        assertTrue(replies.isEmpty())
        val narrow = TerminalScreen(PtyRequest(columns = 2)) {}
        val lines = "x\r\n".repeat(1200).toByteArray()
        narrow.append(lines, lines.size)
        narrow.resize(300, 24)
        assertTrue(narrow.transcript().lines().size <= 218)
    }

    @Test fun managerKeepsPtyAcrossCallsAndSeparatesRawOutputFromScreen() = runBlocking {
        fixture { owner ->
            foreground()
            val terminal = app.terminals.start(owner, PtyRequest(listOf("/system/bin/sh", "-c", "printf ready; read value; echo \"\u0024value\"; exit 6")))
            val session = requireNotNull(app.terminals.session(owner, terminal.id))
            withTimeout(5000) { session.version.first { session.bytes.sequence > 0 } }
            val first = app.terminals.read(owner, terminal.id)
            assertTrue(first.text("text").orEmpty().contains("ready")); assertEquals(JsonNull, first["base64"])
            assertEquals(JsonNull, first["screen"])
            app.terminals.write(owner, terminal.id, "done\n")
            withTimeout(5000) { session.job.join() }
            assertEquals(6, session.record.exitCode)
            val screen = app.terminals.read(owner, terminal.id, TerminalRead(view = "screen"))
            assertEquals(JsonNull, screen["text"]); assertTrue(screen.text("screen").orEmpty().contains("done"))
            assertTrue(runCatching { app.terminals.write(UUID.randomUUID().toString(), terminal.id, "bad") }.isFailure)
        }
    }

    @Test fun deleteCancelsActivePtyBeforeRemovingFilesAndResidentState() = runBlocking {
        fixture { owner ->
            foreground()
            val terminal = app.terminals.start(owner, PtyRequest(listOf("/system/bin/sh", "-c", "echo ready; sleep 60")))
            val session = requireNotNull(app.terminals.session(owner, terminal.id))
            val path = app.spaces.commandRoot(owner)
            withTimeout(5000) { app.spaces.delete(owner, app.tasks::quiesce) { app.terminals.forget(it) } }
            assertTrue(session.job.isCompleted); assertFalse(Files.exists(path))
            assertNull(app.terminals.session(owner, terminal.id))
        }
    }

    @Test fun recoveryMarksInterruptedAndNeverReplaysInput() = runBlocking {
        fixture { owner ->
            app.spaces.commandRoot(owner)
            val records = TerminalRecords(app.filesDir.toPath())
            val id = UUID.randomUUID().toString()
            val directory = records.directory(owner, id, true)
            records.save(TerminalRecord(id, owner, "android-native", "running", System.currentTimeMillis()))
            Files.write(directory.resolve("output.bin"), "saved".toByteArray())
            records.recover()
            val record = records.load(owner, id)
            assertEquals("interrupted", record.state); assertFalse(record.bytesExact); assertEquals(5L, record.stored)
            assertNull(app.terminals.session(owner, id))
            assertEquals("saved", app.terminals.read(owner, id).text("text"))
        }
    }

    @Test fun conversationAdmissionRejectsFifthLiveTerminal() = runBlocking {
        fixture { owner ->
            foreground()
            repeat(4) { app.terminals.start(owner, PtyRequest(listOf("/system/bin/sleep", "60"))) }
            assertTrue(runCatching { app.terminals.start(owner, PtyRequest()) }.isFailure)
            assertEquals(4, app.terminals.current(owner).count { it.state == "running" })
        }
    }

    private fun foreground() {
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
    }
    private suspend fun fixture(block: suspend (String) -> Unit) {
        app.ready.await()
        val owner = app.conversations.create("terminal-fixture")
        try { block(owner) } finally {
            app.terminals.forget(owner)
            if (app.conversations.header(owner) != null) app.spaces.delete(owner, app.tasks::quiesce)
        }
    }
}
