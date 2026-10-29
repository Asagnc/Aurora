package io.github.asagnc.aurora.root

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit final-device qualification only; invokes production tools directly, without a model. */
@RunWith(AndroidJUnit4::class)
class FridaManagedDeviceTest {
    @RootRequired @Test fun nativeAndArtSelfProbesThenSelectedAttachUseTheRealLedgerAndArtifacts() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        check(InstrumentationRegistry.getArguments().getString("auroraFridaTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await()
        check(!withContext(Dispatchers.Main) { app.agentTasks.busy }) { "Stop active Agent work before device qualification" }
        FridaDeviceForeground(app).use {
            app.root.connect()
            assertEquals(app.root.state.value.detail, RootPhase.READY, app.root.state.value.phase)
            val owner = app.conversations.create("Aurora Frida qualification fixture")
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "AURORA_FRIDA_OWNER=$owner\n") })
            var failure: Throwable? = null
            try {
                withTimeout(240_000) { qualify(app, owner) }
            } catch (caught: Throwable) { failure = caught; throw caught }
            finally {
                withContext(NonCancellable) {
                    try {
                        check(app.managedRuns.recover(owner) == 0) { "Unconfirmed fixture retained: $owner" }
                        if (failure == null || InstrumentationRegistry.getArguments().getString("auroraRetainFridaFailure") != "true")
                            app.spaces.delete(owner, app.tasks::quiesce)
                    } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
                    finally { app.root.disconnect() }
                }
            }
        }
    }

    private suspend fun qualify(app: AuroraApplication, owner: String) {
        val tool = FridaTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        suspend fun invoke(args: JsonObject): JsonObject = app.tasks.run(owner) {
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("stream", "AURORA_FRIDA_STAGE=${args.text("action")} java=${args["java"]}\n")
            })
            val call = ToolCall(UUID.randomUUID().toString(), "frida", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
        }
        val native = invoke(objectOf("action" to string("probe")))
        assertEquals(native.toString(), JsonPrimitive(true), native["probeSucceeded"])
        val root = app.spaces.commandRoot(owner)
        assertTrue(String(Files.readAllBytes(root.resolve(requireNotNull(native.text("artifact"))))).contains("native_hook_observed"))
        assertEquals(0L, app.managedRuns.pendingCount(owner))
        val connection = requireNotNull(app.root.fridaConnection)
        val script = "send({aurora:'owned-selected-script',pid:Process.id});"
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("frida-hook.js", null, script)))
        val sha = MessageDigest.getInstance("SHA-256").digest(script.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val attach = invoke(objectOf("action" to string("attach"), "pid" to JsonPrimitive(connection.identity.pid),
            "startTicks" to JsonPrimitive(connection.identity.startTicks), "source" to string("conversation"),
            "path" to string("frida-hook.js"), "scriptSha256" to string(sha), "observeMs" to JsonPrimitive(10_000)))
        assertEquals(attach.toString(), JsonPrimitive(true), attach["workerSucceeded"])
        assertTrue(String(Files.readAllBytes(root.resolve(requireNotNull(attach.text("artifact"))))).contains("owned-selected-script"))
        val java = invoke(objectOf("action" to string("probe"), "java" to JsonPrimitive(true)))
        assertEquals(java.toString(), JsonPrimitive(true), java["probeSucceeded"])
        assertTrue(String(Files.readAllBytes(root.resolve(requireNotNull(java.text("artifact"))))).contains("java_call_observed"))
        val status = invoke(objectOf("action" to string("status")))
        assertEquals(JsonPrimitive(true), status["nativeProbePassed"])
        assertEquals(JsonPrimitive(true), status["javaProbePassed"])
        assertEquals(0L, app.managedRuns.pendingCount(owner))
    }
}
