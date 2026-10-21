package io.github.asagnc.aurora.root

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootCommandDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @RootRequired @Test fun rootPtyHasRealUidZeroInteractiveInputAndExitStatus() = runBlocking { fixture { controller, owner, _ ->
        val endpoint = requireNotNull(controller.terminalClient).open(UUID.randomUUID().toString(), owner,
            io.github.asagnc.aurora.terminal.PtyRequest(listOf("/system/bin/sh", "-c",
                "test -t 0 || exit 8; id -u; read word; printf '<%s>' \"\u0024word\"; exit 9")))
        val output = async(Dispatchers.IO) { String(endpoint.output.readBytes()) }
        try {
            endpoint.write("root-pty\n".toByteArray())
            val result = withTimeout(5000) { endpoint.exit.await() }
            assertEquals(9, result.exitCode); assertEquals("exited", result.state)
            val text = withTimeout(5000) { output.await() }
            assertTrue(text.contains(Regex("(?m)^0\\r?\u0024"))); assertTrue(text.contains("<root-pty>"))
        } finally { endpoint.stop(); endpoint.closeStreams(); output.cancelAndJoin() }
    } }

    @RootRequired @Test fun disconnectStopsRootPtyAndReconnectDoesNotReplayIt() = runBlocking { fixture { controller, owner, _ ->
        val endpoint = requireNotNull(controller.terminalClient).open(UUID.randomUUID().toString(), owner,
            io.github.asagnc.aurora.terminal.PtyRequest(listOf("/system/bin/sh", "-c", "printf ready; sleep 60")))
        val ready = CompletableDeferred<Unit>()
        val output = async(Dispatchers.IO) { if (endpoint.output.read() >= 0) ready.complete(Unit); endpoint.output.readBytes() }
        try {
            withTimeout(5000) { ready.await() }
            controller.disconnect()
            withTimeout(5000) { output.await() }
            val old = withTimeout(5000) { runCatching { endpoint.exit.await() } }
            assertTrue(old.isFailure || old.getOrThrow().state != "exited")
            controller.connect()
            assertEquals(RootPhase.READY, controller.state.value.phase)
            val fresh = requireNotNull(controller.terminalClient).open(UUID.randomUUID().toString(), owner,
                io.github.asagnc.aurora.terminal.PtyRequest(listOf("/system/bin/id", "-u")))
            try {
                assertEquals("0", String(fresh.output.readBytes()).trim())
                assertEquals(0, withTimeout(5000) { fresh.exit.await() }.exitCode)
            } finally { fresh.stop(); fresh.closeStreams() }
        } finally { endpoint.stop(); endpoint.closeStreams(); output.cancelAndJoin() }
    } }

    private suspend fun fixture(block: suspend (RootController, String, Path) -> Unit) {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        val id = UUID.randomUUID().toString()
        val directory = context.filesDir.toPath().resolve("spaces/conversations").resolve(id)
        Files.createDirectories(directory)
        val controller = RootController(context)
        try {
            controller.connect()
            assertEquals(controller.state.value.detail, RootPhase.READY, controller.state.value.phase)
            block(controller, id, directory)
        } finally {
            controller.disconnect()
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @RootRequired @Test fun rootIdentityAndArtifactsRemainReadableByApplication() = runBlocking { fixture { controller, id, directory ->
        val result = WireJson.parseToJsonElement(requireNotNull(controller.commandClient)
            .execute(id, CommandRequest(listOf("/system/bin/id", "-u")))).jsonObject
        assertEquals(0L, result.number("uid"))
        assertEquals("android-root", result.text("backend"))
        assertEquals("0", result["stdout"]!!.jsonObject.text("text")!!.trim())
        Files.walk(directory).use { paths -> paths.forEach { path ->
            assertEquals("Ownership: $path", context.applicationInfo.uid, Os.lstat(path.toString()).st_uid)
            if (Files.isRegularFile(path)) Files.readAllBytes(path)
        } }
    } }

    @RootRequired @Test fun disconnectWaitsForCleanupAndReconnectCreatesFreshSession() = runBlocking { fixture { controller, id, directory ->
        val client = requireNotNull(controller.commandClient)
        val task = async(Dispatchers.IO) { client.execute(id, CommandRequest(listOf("/system/bin/sh", "-c",
            "trap 'printf flushed' TERM; echo ready > started; while :; do sleep 1; done"))) }
        withTimeout(10_000) { while (!Files.exists(directory.resolve("started"))) delay(20) }
        controller.disconnect()
        assertTrue(withTimeout(3000) { task.await() }.contains("command_outcome_uncertain"))
        val outputs = Files.walk(directory.resolve("runs")).use { paths ->
            paths.filter { it.fileName.toString() == "stdout.bin" }.toList()
        }
        assertEquals("flushed", String(Files.readAllBytes(outputs.single())))
        controller.connect()
        assertEquals(RootPhase.READY, controller.state.value.phase)
        val result = requireNotNull(controller.commandClient).execute(id, CommandRequest(listOf("/system/bin/true")))
        assertEquals(0L, WireJson.parseToJsonElement(result).jsonObject.number("exitCode"))
    } }

    @RootRequired @Test fun clientCancellationAndRootTimeoutWaitForNativeCleanup() = runBlocking { fixture { controller, id, directory ->
        val client = requireNotNull(controller.commandClient)
        val task = launch(Dispatchers.IO) { client.execute(id, CommandRequest(listOf("/system/bin/sh", "-c",
            "trap 'printf cancelled' TERM; echo ready > started; while :; do sleep 1; done"))) }
        withTimeout(10_000) { while (!Files.exists(directory.resolve("started"))) delay(20) }
        task.cancelAndJoin()
        val outputs = Files.walk(directory.resolve("runs")).use { paths ->
            paths.filter { it.fileName.toString() == "stdout.bin" }.toList()
        }
        assertEquals("cancelled", String(Files.readAllBytes(outputs.single())))
        assertEquals(context.applicationInfo.uid, Os.lstat(outputs.single().toString()).st_uid)
        val timed = WireJson.parseToJsonElement(client.execute(id, CommandRequest(listOf("/system/bin/sh", "-c",
            "trap '' TERM; while :; do sleep 1; done"), timeoutMs = 100))).jsonObject
        assertEquals("command_timeout", timed.text("error"))
        assertEquals(0L, timed.number("uid"))
        assertEquals(JsonPrimitive(true), timed["stopped"])
    } }
}
