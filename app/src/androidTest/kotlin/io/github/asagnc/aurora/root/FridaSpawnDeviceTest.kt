package io.github.asagnc.aurora.root

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import android.os.Bundle
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only the separately installed owned fixture is launched. No model or unrelated app is used. */
@RunWith(AndroidJUnit4::class)
class FridaSpawnDeviceTest {
    @RootRequired @Test fun earlyArtHookInExplicitOwnedPackageUsesManagedSpawnProofs() = runOwnedCase(::qualify)

    @RootRequired @Test fun scriptErrorBeforeResumeRetiresOnlyTheOwnedCapturedTarget() = runOwnedCase(::rejectBeforeResume)

    private fun runOwnedCase(block: suspend (AuroraApplication, String) -> Unit) = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        for (name in listOf("auroraRootTest", "auroraFridaTest", "auroraSpawnTest")) check(arguments.getString(name) == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await()
        check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        val fixture = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm path io.github.asagnc.aurora.fridafixture")).use { String(it.readNBytes(4096), Charsets.UTF_8) }
        check(fixture.startsWith("package:")) { "Install the owned fixture APK during final device acceptance first" }
        app.root.connect()
        check(app.root.state.value.phase == RootPhase.READY)
        val owner = app.conversations.create("Aurora owned spawn qualification")
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_FRIDA_FIXTURE_OWNER=$owner\n") })
        var failure: Throwable? = null
        try {
            withTimeout(240_000) { block(app, owner) }
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally {
            withContext(NonCancellable) {
                // Explicit fixture-only stop is test cleanup, never an exit proof for an unknown spawn.
                var cleanupFailure = runCatching {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                        "am force-stop io.github.asagnc.aurora.fridafixture")).use { it.readNBytes(4096) }
                }.exceptionOrNull()
                try {
                    check(app.managedRuns.recover(owner) == 0) { "Unconfirmed spawn fixture retained: $owner" }
                    if (failure == null || arguments.getString("auroraRetainFridaFailure") != "true")
                        app.spaces.delete(owner, app.tasks::quiesce)
                } catch (cleanup: Throwable) {
                    if (cleanupFailure != null) cleanupFailure.addSuppressed(cleanup) else cleanupFailure = cleanup
                }
                cleanupFailure?.let { if (failure != null) failure.addSuppressed(it) else throw it }
            }
        }
    }

    private suspend fun rejectBeforeResume(app: AuroraApplication, owner: String) {
        val tool = FridaTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        suspend fun invoke(args: JsonObject): JsonObject = app.tasks.run(owner) {
            val call = ToolCall(UUID.randomUUID().toString(), "frida", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
        }
        val probe = invoke(objectOf("action" to string("probe"), "java" to JsonPrimitive(true)))
        assertEquals(probe.toString(), JsonPrimitive(true), probe["probeSucceeded"])
        val source = "throw new Error('owned_before_resume_failure');"
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("spawn-error.js", null, source)))
        val sha = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        val result = invoke(objectOf("action" to string("spawn"), "package" to string("io.github.asagnc.aurora.fridafixture"),
            "source" to string("conversation"), "path" to string("spawn-error.js"), "scriptSha256" to string(sha),
            "java" to JsonPrimitive(true), "observeMs" to JsonPrimitive(20_000)))
        assertEquals(result.toString(), JsonPrimitive(false), result["workerSucceeded"])
        assertEquals(result.toString(), 0L, app.managedRuns.pendingCount(owner))
        val cleanup = requireNotNull(result["cleanup"]).jsonObject
        assertNotNull(result.toString(), cleanup["spawnTarget"])
        val log = app.spaces.commandRoot(owner).resolve(requireNotNull(result.text("artifact")))
        val messages = String(Files.readAllBytes(log))
        assertTrue(messages, messages.contains("owned_before_resume_failure"))
        assertFalse(messages, messages.contains("\"kind\":\"loaded\""))
    }

    private suspend fun qualify(app: AuroraApplication, owner: String) {
        val tool = FridaTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        suspend fun invoke(args: JsonObject): JsonObject = app.tasks.run(owner) {
            val call = ToolCall(UUID.randomUUID().toString(), "frida", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
        }
        val probe = invoke(objectOf("action" to string("probe"), "java" to JsonPrimitive(true)))
        assertEquals(probe.toString(), JsonPrimitive(true), probe["probeSucceeded"])
        val script = """
            Java.perform(() => {
              send({aurora:'owned_java_callback',pid:Process.id});
              const fixture = Java.use('io.github.asagnc.aurora.fridafixture.MainActivity');
              fixture.marker.implementation = function () { send({aurora:'owned_early_art_hook',pid:Process.id}); return 'hooked'; };
              send({aurora:'owned_hook_registered',pid:Process.id});
            });
        """.trimIndent()
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("spawn-hook.js", null, script)))
        val sha = MessageDigest.getInstance("SHA-256").digest(script.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val result = invoke(objectOf("action" to string("spawn"), "package" to string("io.github.asagnc.aurora.fridafixture"),
            "source" to string("conversation"), "path" to string("spawn-hook.js"), "scriptSha256" to string(sha),
            "java" to JsonPrimitive(true), "observeMs" to JsonPrimitive(20_000)))
        assertEquals(result.toString(), JsonPrimitive(true), result["workerSucceeded"])
        assertEquals(0L, app.managedRuns.pendingCount(owner))
        val log = app.spaces.commandRoot(owner).resolve(requireNotNull(result.text("artifact")))
        val messages = String(Files.readAllBytes(log))
        assertTrue(messages, messages.contains("owned_hook_registered"))
        assertTrue(messages, messages.contains("owned_early_art_hook"))
        assertNotNull(result["cleanup"]?.jsonObject?.get("spawnTarget"))
    }
}
