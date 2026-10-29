package io.github.asagnc.aurora.root

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.RootManagedRunRegistry
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Cancels only after the owned ordinary App emitted a real injection event. */
@RunWith(AndroidJUnit4::class)
class FridaCancellationDeviceTest {
    @RootRequired @Test fun cancelLoadedOwnAppScriptUnloadsWithoutKillingTheTarget() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraFridaCancelTest") == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        FridaDeviceForeground(app).use {
            app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
            val owner = app.conversations.create("Aurora Frida cancellation qualification fixture")
            var failure: Throwable? = null
            try { withTimeout(120_000) { qualify(app, owner) } }
            catch (caught: Throwable) { failure = caught; throw caught }
            finally { withContext(NonCancellable) {
                try {
                    assertEquals("Unconfirmed cancellation fixture retained: $owner", 0, app.managedRuns.recover(owner))
                    app.spaces.delete(owner, app.tasks::quiesce)
                } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
                finally { app.root.disconnect() }
            } }
        }
    }

    private suspend fun qualify(app: AuroraApplication, owner: String) = coroutineScope {
        val tool = FridaTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        suspend fun invoke(args: JsonObject): JsonObject = app.tasks.run(owner) {
            val call = ToolCall(UUID.randomUUID().toString(), "frida", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
        }
        val probe = invoke(objectOf("action" to string("probe")))
        assertEquals(probe.toString(), JsonPrimitive(true), probe["probeSucceeded"])
        val source = "send({aurora:'owned_cancel_fixture',pid:Process.id});setInterval(()=>{},1000);"
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("cancel-owned.js", null, source)))
        val sha = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        val ticks = Files.newInputStream(Path.of("/proc/self/stat")).use {
            linuxProcessStartTicks(it.readNBytes(65537).toString(Charsets.UTF_8))
        }
        val task = async { invoke(objectOf("action" to string("attach"), "pid" to JsonPrimitive(Process.myPid()),
            "startTicks" to JsonPrimitive(ticks), "source" to string("conversation"), "path" to string("cancel-owned.js"),
            "scriptSha256" to string(sha), "observeMs" to JsonPrimitive(60_000))) }
        val ticket = try { awaitInjected(app, owner) } finally { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
        assertEquals(0, app.managedRuns.recover(owner))
        assertEquals(0L, app.managedRuns.pendingCount(owner))
        val log = app.spaces.commandRoot(owner).resolve("runs/${ticket.runId}/frida.log")
        val messages = Files.newInputStream(log).use { it.readNBytes(65537).toString(Charsets.UTF_8) }
        assertTrue(messages, messages.contains("\"reason\":\"cancelled\""))
        assertTrue(messages, messages.contains("\"cleanupConfirmed\":true"))
        assertTrue(messages, messages.contains("\"resourcesClear\":true"))
        assertTrue("Original App PID/start ticks must survive", Files.newInputStream(Path.of("/proc/self/stat")).use {
            linuxProcessStartTicks(it.readNBytes(65537).toString(Charsets.UTF_8)) == ticks
        })
    }

    private suspend fun awaitInjected(app: AuroraApplication, owner: String): RootManagedRunTicket = withTimeout(20_000) {
        while (true) {
            val ticket = RootManagedRunRegistry(app.conversations).pending(owner).singleOrNull()
            if (ticket != null) {
                require(ticket.target?.pid == Process.myPid() && ticket.action == "attach")
                val log = app.filesDir.toPath().toRealPath().resolve("spaces/resources/root-managed-runtime/${ticket.runId}/worker-events.log")
                // One extra Root command slot reads only this immutable ticket's
                // owned log; it cannot cancel or mutate the injection task.
                val encoded = requireNotNull(app.root.commandClient).execute(owner,
                    CommandRequest(listOf("/system/bin/grep", "-m", "1", "owned_cancel_fixture", log.toString()), timeoutMs = 1500))
                val result = WireJson.parseToJsonElement(encoded).jsonObject
                if (result.number("exitCode") == 0L) return@withTimeout ticket
            }
            delay(200)
        }
        @Suppress("UNREACHABLE_CODE") error("No owned injection event")
    }
}
