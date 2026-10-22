package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCommandTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runner = NativeCommandRunner(context)

    private suspend fun fixture(block: suspend (Path) -> Unit) {
        val root = Files.createDirectory(context.cacheDir.toPath().resolve("command-test-${UUID.randomUUID()}"))
        try { assertTrue("Packaged executable must be available", runner.available); block(root) }
        finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun literalArgumentsAndRealExitStatus() = runBlocking { fixture { root ->
        val literal = "hello world;$(touch injected)"
        val result = runner.execute(root, CommandRequest(listOf("/system/bin/printf", "%s", literal)))
        assertEquals(literal, result["stdout"]!!.jsonObject.text("text"))
        assertFalse(Files.exists(root.resolve("injected")))
        assertEquals(0L, result.number("exitCode"))
        val unicode = runner.execute(root, CommandRequest(listOf("/system/bin/printf", "%s", "a".repeat(1023) + "星x"), outputLimit = 1024))
        assertEquals("a".repeat(1023), unicode["stdout"]!!.jsonObject.text("text"))
        assertEquals(JsonPrimitive(true), unicode["stdout"]!!.jsonObject["previewTruncated"])
        val failed = runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c",
            "printf '%s' \"\$AURORA_TEST\"; printf problem >&2; exit 7"), environment = mapOf("AURORA_TEST" to "precise value")))
        assertEquals("command_exit", failed.text("error"))
        assertEquals(7L, failed.number("exitCode"))
        assertEquals("precise value", failed["stdout"]!!.jsonObject.text("text"))
        assertEquals("problem", failed["stderr"]!!.jsonObject.text("text"))
    } }

    @Test fun clippedInvalidUtf8RemainsBinaryAndArtifactIsExact() = runBlocking { fixture { root ->
        val bytes = "a".repeat(1023).toByteArray() + byteArrayOf(0xff.toByte()) + "tail".toByteArray()
        Files.write(root.resolve("binary-input"), bytes)
        val result = runner.execute(root, CommandRequest(listOf("/system/bin/cat", "binary-input"), outputLimit = 1024))
        assertEquals(0L, result.number("exitCode"))
        val output = result["stdout"]!!.jsonObject
        assertEquals(JsonNull, output["text"])
        assertEquals(JsonPrimitive(true), output["previewTruncated"])
        assertEquals(bytes.size.toLong(), output.number("storedBytes"))
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve(requireNotNull(output.text("artifact")))))
    } }

    @Test fun largeOutputIsDrainedAndArtifactBudgetIsExplicit() = runBlocking { fixture { root ->
        val result = runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c",
            "head -c 9437184 /dev/zero; head -c 9437184 /dev/zero >&2"), outputLimit = 1024))
        assertEquals(0L, result.number("exitCode"))
        assertEquals(JsonPrimitive(true), result["captureComplete"])
        for (name in listOf("stdout", "stderr")) {
            val output = result[name]!!.jsonObject
            assertEquals(9437184L, output.number("bytes"))
            assertEquals(JsonNull, output["text"])
            assertEquals(8388608L, output.number("storedBytes"))
            assertEquals(JsonPrimitive(true), output["artifactTruncated"])
            assertEquals(JsonPrimitive(true), output["previewTruncated"])
            assertEquals(8388608L, Files.size(root.resolve(output.text("artifact")!!)))
        }
    } }

    @Test fun timeoutStopsWholeOrdinaryProcessGroup() = runBlocking { fixture { root ->
        val result = runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c", CHILD), timeoutMs = 1000))
        assertEquals("command_timeout", result.text("error"))
        assertEquals(JsonPrimitive(true), result["timedOut"])
        assertTerminated(root.resolve("child.pid"))
        val stubborn = runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c", "trap '' TERM; $CHILD"), timeoutMs = 1000))
        assertEquals("command_timeout", stubborn.text("error"))
        assertTerminated(root.resolve("child.pid"))
    } }

    @Test fun conversationQuiesceCancelsAndWaitsForCleanup() = runBlocking { fixture { root ->
        val tasks = OwnedTasks()
        val script = "trap 'printf flushed > stopped.txt; exit 0' TERM; $CHILD"
        val command = launch(Dispatchers.IO) { tasks.run("owner") { runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c", script))) } }
        withTimeout(5000) { while (!Files.exists(root.resolve("child.pid"))) delay(10) }
        tasks.quiesce("owner")
        assertTrue(command.isCompleted && command.isCancelled)
        assertTerminated(root.resolve("child.pid"))
        assertEquals("flushed", String(Files.readAllBytes(root.resolve("stopped.txt")), Charsets.UTF_8))
    } }

    @Test fun normalExitDoesNotLeaveBackgroundChildRunning() = runBlocking { fixture { root ->
        val result = runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c", "sleep 60 & echo \$! > child.pid; exit 0")))
        assertEquals(0L, result.number("exitCode"))
        assertTerminated(root.resolve("child.pid"))
    } }

    @Test fun missingProgramAndEscapingWorkingDirectoryAreNotSuccess() = runBlocking { fixture { root ->
        val result = runner.execute(root, CommandRequest(listOf("/system/bin/aurora-nonexistent-test")))
        assertEquals(127L, result.number("exitCode"))
        assertEquals("command_exit", result.text("error"))
        val interrupted = runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c", "kill -9 \$PPID")))
        assertEquals("command_outcome_uncertain", interrupted.text("error"))
        val stopped = runner.execute(root, CommandRequest(listOf("/system/bin/sh", "-c", "kill -TERM \$PPID; sleep 60"), timeoutMs = 1000))
        assertEquals("command_stopped", stopped.text("error"))
        assertEquals(JsonPrimitive(true), stopped["stopped"])
        assertTrue(runCatching { runner.execute(root, CommandRequest(listOf("/system/bin/true"), "../")) }.isFailure)
        assertTrue(runCatching { runner.execute(root, CommandRequest(listOf("/system/bin/true", "bad\u0000argument"))) }.isFailure)
        assertTrue(runCatching { runner.execute(root, CommandRequest(listOf("/system/bin/true"),
            environment = (0..8).associate { "AURORA_$it" to "x".repeat(8192) })) }.isFailure)
    } }

    @Test fun supervisorStopsItsGroupWhenLaunchingParentExits() = runBlocking { fixture { root ->
        val artifacts = Files.createDirectory(root.resolve("orphan-check"))
        val executable = java.io.File(context.applicationInfo.nativeLibraryDir, "libaurora_runner.so")
        val script = "\"\$1\" \"\$2\" -- /system/bin/sh -c 'sleep 60 & echo \$! > child.pid; wait' & " +
            "while [ ! -f child.pid ]; do sleep 0.01; done; exit 0"
        val parent = ProcessBuilder("/system/bin/sh", "-c", script, "fixture", executable.absolutePath, artifacts.toString())
            .directory(root.toFile()).redirectErrorStream(true).redirectOutput(root.resolve("parent.log").toFile()).start()
        try {
            withTimeout(5000) { runInterruptible(Dispatchers.IO) { parent.waitFor() } }
            withTimeout(5000) { while (!Files.exists(artifacts.resolve("result.json"))) delay(10) }
            val record = WireJson.parseToJsonElement(String(Files.readAllBytes(artifacts.resolve("result.json")), Charsets.UTF_8)).jsonObject
            assertEquals(JsonPrimitive(true), record["stopped"])
            assertTerminated(root.resolve("child.pid"))
        } finally { if (parent.isAlive) parent.destroyForcibly(); parent.waitFor() }
    } }

    @Test fun agentUsesNativeToolAndReceivesActualArtifactResult() = runBlocking { fixture { root ->
        val tools = CommandToolExecutor(runner, root)
        var requests = 0
        val transport = object : ModelTransport {
            override suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn {
                if (requests++ == 0) return ModelTurn("", emptyList(), listOf(ToolCall("native-call", "command_execute",
                    objectOf("argv" to JsonArray(listOf("/system/bin/sh", "-c", "printf actual > generated.txt; cat generated.txt").map(::string))))), "tool_calls", Usage())
                assertTrue(request.history.last().toString().contains("actual"))
                assertTrue(request.history.last().toString().contains("exitCode"))
                return ModelTurn("verified", emptyList(), emptyList(), "stop", Usage())
            }
        }
        val connection = Connection(name = "fixture", baseUrl = "https://example.invalid", protocol = Protocol.CHAT, model = "fixture")
        val result = AgentLoop(transport, tools).run(ModelRequest(connection, "fixture", emptyList(), tools.definitions), {}, {})
        assertEquals("verified", result.text)
        assertEquals(2, requests)
        assertEquals("actual", String(Files.readAllBytes(root.resolve("generated.txt")), Charsets.UTF_8))
    } }

    private suspend fun assertTerminated(path: Path) {
        val pid = String(Files.readAllBytes(path), Charsets.UTF_8).trim().toInt()
        withTimeout(5000) {
            while (runCatching {
                val stat = String(Files.readAllBytes(Path.of("/proc/$pid/stat")), Charsets.UTF_8)
                stat.substringAfterLast(") ").first() != 'Z'
            }.getOrDefault(false)) delay(10)
        }
    }

    companion object { private const val CHILD = "sleep 60 & echo \$! > child.pid; wait" }
}
