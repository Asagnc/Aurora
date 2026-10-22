package io.github.asagnc.aurora.runtime

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxImageRequired
import io.github.asagnc.aurora.root.RootRequired
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Scripted in-process model: exercises production Agent routing without a network/API key. */
@RunWith(AndroidJUnit4::class)
class AgentLinuxDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as AuroraApplication

    @RootRequired @LinuxImageRequired @Test fun agentRunsBatchThenInteractiveGuestWithPersistedArtifacts() = runBlocking {
        fixture { owner, connection ->
            var turn = 0
            var terminal = ""
            var definitions: List<ToolDefinition>? = null
            val transport = object : ModelTransport {
                override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                    if (definitions == null) definitions = request.tools else assertEquals(definitions, request.tools)
                    assertTrue(request.tools.any { it.name == "command_execute_linux" })
                    assertTrue(request.tools.any { it.name == "terminal_start_linux" })
                    val step = turn++
                    return when (step) {
                        0 -> call(step, "command_execute_linux", argv("printf batch-output; exit 37"))
                        1 -> {
                            val result = output(request)
                            assertEquals("exited", result.text("state")); assertEquals(37L, result.number("exitCode"))
                            assertEquals(JsonPrimitive(false), result["success"])
                            assertTrue(result.toString().contains("batch-output"))
                            call(step, "terminal_start_linux", argv("printf ready; read value; printf '<%s>' \"\$value\"; exit 23"))
                        }
                        2 -> {
                            terminal = requireNotNull(output(request).text("id"))
                            call(step, "terminal_write", objectOf("id" to string(terminal), "text" to string("极光😀\n")))
                        }
                        else -> {
                            if (step > 3) {
                                val result = output(request)
                                assertNull(result["error"])
                                if (result.text("state") == "exited") {
                                    assertEquals(23L, result.number("exitCode"))
                                    assertTrue(result.text("text").orEmpty().contains("<极光😀>"))
                                    return done()
                                }
                            }
                            check(step < 100) { "Guest did not finish" }
                            delay(50)
                            call(step, "terminal_read", objectOf("id" to string(terminal), "waitMs" to JsonPrimitive(1000)))
                        }
                    }
                }
            }
            execute(owner, connection, transport)
            val runs = app.toolRuns.list(owner)
            assertTrue(runs.all { it.state == "completed" })
            val batch = runs.single { it.tool == "command_execute_linux" }
            assertEquals(37L, summarizeToolResult(batch.presentation)!!.exitCode)
            assertTrue(batch.artifactDirectory.orEmpty().startsWith("runs/"))
            assertEquals(0, app.linux.runs.pendingCount())
            assertEquals("linux-root", app.terminals.list(owner).single().backend)
        }
    }

    @RootRequired @LinuxImageRequired @Test fun agentSeesGuestTimeoutAsFailureDespiteConfirmedCleanup() = runBlocking {
        fixture { owner, connection ->
            var turns = 0
            val transport = object : ModelTransport {
                override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                    if (turns++ == 0) return call(0, "command_execute_linux", JsonObject(argv("sleep 30") + ("timeoutMs" to JsonPrimitive(1000))))
                    val result = output(request)
                    assertEquals("timed_out", result.text("state"))
                    assertEquals(JsonPrimitive(false), result["success"])
                    assertEquals(JsonPrimitive(true), result["stopped"])
                    assertEquals(JsonNull, result["exitCode"])
                    return done()
                }
            }
            execute(owner, connection, transport)
            assertEquals(2, turns)
            assertEquals("command_timeout", summarizeToolResult(app.toolRuns.list(owner).single().presentation)!!.error)
            assertEquals(0, app.linux.runs.pendingCount())
        }
    }

    @RootRequired @LinuxImageRequired @Test fun agentPublishesSelectedRootImageAndRemovesOnlyTransferCopy() = runBlocking {
        fixture { owner, connection ->
            val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.CYAN)
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); bitmap.recycle(); output.toByteArray()
            }
            val encoded = Base64.getEncoder().encodeToString(bytes)
            var turns = 0
            var published: ImageArtifact? = null
            val transport = object : ModelTransport {
                override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn = when (turns++) {
                    0 -> call(0, "command_execute_linux", argv("printf '%s' '$encoded' | base64 -d > /mnt/picture.png; chmod 600 /mnt/picture.png"))
                    1 -> {
                        assertEquals(JsonPrimitive(true), output(request)["success"])
                        call(1, "images_publish", objectOf("source" to string(".aurora-linux/project/picture.png"), "description" to string("极光测试图片")))
                    }
                    else -> {
                        val result = output(request)
                        assertNull(result["error"])
                        published = WireJson.decodeFromJsonElement(ImageArtifact.serializer(), requireNotNull(result["image"]))
                        assertTrue(result.text("markdown").orEmpty().contains("aurora-image:"))
                        done()
                    }
                }
            }
            execute(owner, connection, transport)
            val artifact = requireNotNull(published)
            assertEquals(3, artifact.width); assertEquals(2, artifact.height)
            val file = app.images.file(owner, artifact)
            val decoded = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            try { assertEquals(android.graphics.Color.CYAN, decoded.getPixel(1, 1)) } finally { decoded.recycle() }
            val copied = ByteArrayOutputStream()
            app.images.copyTo(owner, artifact, copied)
            assertArrayEquals(bytes, copied.toByteArray())
            val exports = app.spaces.commandRoot(owner).resolve("exports")
            if (Files.isDirectory(exports)) Files.list(exports).use { paths ->
                assertFalse(paths.anyMatch { it.fileName.toString().startsWith("root-") })
            }
            val retained = app.linux.runs.execute(owner, listOf("/bin/sh", "-c", "test -f /mnt/picture.png; stat -c %u /mnt/picture.png"))
            assertEquals(0L, retained["completion"]!!.jsonObject.number("exitCode"))
            assertEquals("0", retained["result"]!!.jsonObject["stdout"]!!.jsonObject.text("text")!!.trim())
        }
    }

    private suspend fun execute(owner: String, connection: Connection, transport: ModelTransport) {
        val state = MutableStateFlow(ChatState(current = app.conversations.header(owner)))
        withTimeout(90_000) { app.tasks.run(owner) {
            AgentExecution(app, state) { transport }.execute(owner, "offline Linux qualification", connection, System.nanoTime())
        } }
        assertEquals("idle", app.conversations.header(owner)!!.runState)
        assertEquals("verified", app.conversations.messages(owner).last().message.text)
        val header = requireNotNull(app.conversations.header(owner))
        assertNotNull(app.nativeHistory.load(owner, requireNotNull(header.activeScope)))
    }

    @RootRequired @LinuxImageRequired @Test fun agentProbeRecordsArtifactsAndRecoveryLeavesOtherConversationRunning() = runBlocking {
        fixture { owner, connection ->
            val other = app.conversations.create("other conversation terminal")
            try {
                val record = app.terminals.startLinux(other, io.github.asagnc.aurora.terminal.PtyRequest(
                    listOf("/bin/sh", "-c", "printf ready; read value"), timeoutMs = 60_000))
                val session = requireNotNull(app.terminals.session(other, record.id))
                var turn = 0
                val transport = object : ModelTransport {
                    override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn = when (val step = turn++) {
                        0 -> call(step, "linux_environment", objectOf("action" to string("probe_root")))
                        1 -> {
                            assertEquals(JsonPrimitive(true), output(request)["workloadPassed"])
                            call(step, "linux_environment", objectOf("action" to string("recover_runs")))
                        }
                        2 -> {
                            assertEquals(0L, output(request).number("recovered"))
                            assertEquals(0L, output(request).number("retained"))
                            call(step, "linux_environment", objectOf("action" to string("status")))
                        }
                        else -> { assertEquals(0L, output(request).number("pendingRuns")); done() }
                    }
                }
                execute(owner, connection, transport)
                assertTrue(session.job.isActive)
                assertEquals("running", session.record.state)
                assertEquals(1, app.linux.runs.pendingCount(other))
                val probe = app.toolRuns.list(owner).first()
                assertNotNull(probe.artifactDirectory)
                assertEquals("linux_environment", probe.tool)
            } finally {
                app.terminals.forget(other)
                app.spaces.delete(other, app.tasks::quiesce)
            }
        }
    }

    @RootRequired @LinuxImageRequired @Test fun explicitAgentRecoveryAndPtyCompletionAcceptTheSameProof() = runBlocking {
        fixture { owner, connection ->
            var terminal = ""
            var turn = 0
            val transport = object : ModelTransport {
                override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn = when (val step = turn++) {
                    0 -> call(step, "terminal_start_linux", argv("printf ready; read value"))
                    1 -> {
                        terminal = requireNotNull(output(request).text("id"))
                        call(step, "linux_environment", objectOf("action" to string("recover_runs")))
                    }
                    else -> {
                        assertEquals(1L, output(request).number("recovered"))
                        assertEquals(0L, output(request).number("retained"))
                        done()
                    }
                }
            }
            execute(owner, connection, transport)
            val session = requireNotNull(app.terminals.session(owner, terminal))
            withTimeout(10_000) { session.job.join() }
            assertEquals("interrupted", session.record.state)
            assertEquals(0, app.linux.runs.pendingCount(owner))
        }
    }

    private fun argv(script: String) = objectOf("argv" to JsonArray(listOf("/bin/sh", "-c", script).map(::string)))
    private fun output(request: ModelRequest) = WireJson.parseToJsonElement(requireNotNull(request.history.last().text("content"))).jsonObject
    private fun call(step: Int, name: String, args: JsonObject) = ModelTurn("", emptyList(),
        listOf(ToolCall("linux-fixture-$step", name, args)), "tool_calls", Usage())
    private fun done() = ModelTurn("verified", emptyList(), emptyList(), "stop", Usage())

    private suspend fun fixture(run: suspend (String, Connection) -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraLinuxImageTest") == "true")
        app.ready.await()
        instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        app.root.connect()
        check(app.root.linuxTerminalClient != null)
        val owner = app.conversations.create("Agent Linux offline fixture")
        val connection = Connection(name = "offline fixture", baseUrl = "https://fixture.invalid/v1", protocol = Protocol.CHAT, model = "fixture")
        var original: Throwable? = null
        try {
            val revision = app.credentials.put(connection.id, "offline-fixture-not-a-real-key")
            run(owner, connection.copy(credentialRevision = revision))
        } catch (failure: Throwable) { original = failure; throw failure
        } finally { withContext(NonCancellable) { try {
            app.terminals.forget(owner)
            app.spaces.delete(owner, app.tasks::quiesce, app.browser::deleteOwner)
        } catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup
        } finally { app.credentials.remove(connection.id); app.root.disconnect() } } }
    }
}
