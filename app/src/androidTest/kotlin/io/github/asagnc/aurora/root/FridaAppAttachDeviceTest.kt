package io.github.asagnc.aurora.root

import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only this instrumentation's ordinary App process; never an unrelated application. */
@RunWith(AndroidJUnit4::class)
class FridaAppAttachDeviceTest {
    @RootRequired @Test fun nativeScriptAttachesToTheOwnedOrdinaryAppProcess() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraFridaAppAttachTest") == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
        val owner = app.conversations.create("Aurora ordinary App attach qualification fixture")
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_APP_ATTACH_OWNER=$owner\n") })
        var original: Throwable? = null
        try { withTimeout(120_000) { qualify(app, owner) } }
        catch (failure: Throwable) { original = failure; throw failure }
        finally { withContext(NonCancellable) {
            try {
                check(app.managedRuns.recover(owner) == 0) { "Unconfirmed ordinary App attach retained: $owner" }
                if (original == null || args.getString("auroraRetainFridaFailure") != "true")
                    app.spaces.delete(owner, app.tasks::quiesce)
            } catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
            finally { app.root.disconnect() }
        } }
    }

    private suspend fun qualify(app: AuroraApplication, owner: String) {
        val tool = FridaTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        suspend fun invoke(args: JsonObject): JsonObject = app.tasks.run(owner) {
            val call = ToolCall(UUID.randomUUID().toString(), "frida", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
        }
        val probe = invoke(objectOf("action" to string("probe")))
        assertEquals(probe.toString(), JsonPrimitive(true), probe["probeSucceeded"])
        val source = "send({aurora:'owned_ordinary_app_attach',pid:Process.id});"
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("owned-app.js", null, source)))
        val sha = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        val ticks = Files.newInputStream(Path.of("/proc/self/stat")).use {
            linuxProcessStartTicks(it.readNBytes(65537).toString(Charsets.UTF_8))
        }
        val result = invoke(objectOf("action" to string("attach"), "pid" to JsonPrimitive(Process.myPid()),
            "startTicks" to JsonPrimitive(ticks), "source" to string("conversation"), "path" to string("owned-app.js"),
            "scriptSha256" to string(sha), "observeMs" to JsonPrimitive(2000)))
        assertEquals(result.toString(), JsonPrimitive(true), result["workerSucceeded"])
        val log = app.spaces.commandRoot(owner).resolve(requireNotNull(result.text("artifact")))
        val data = Files.newInputStream(log).use { it.readNBytes(65537) }
        require(data.size <= 65536)
        assertTrue(data.toString(Charsets.UTF_8).contains("owned_ordinary_app_attach"))
        assertEquals(0L, app.managedRuns.pendingCount(owner))
    }
}
