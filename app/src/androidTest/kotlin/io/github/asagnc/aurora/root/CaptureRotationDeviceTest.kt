package io.github.asagnc.aurora.root

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Final opt-in Root qualification. Compiling this test does not execute it or operate a device. */
@RunWith(AndroidJUnit4::class)
class CaptureRotationDeviceTest {
    @RootRequired @Test fun explicitLoopbackProbeThenQuietCaptureUseTheActualLedgerAndRootSeals() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("auroraRootTest") == "true" && arguments.getString("auroraCaptureRotationTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        app.root.connect(); assertEquals(RootPhase.READY, app.root.state.value.phase)
        val owner = app.conversations.create("Aurora capture rotation qualification fixture")
        var failure: Throwable? = null
        try {
            val tool = CaptureRotationTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
            suspend fun invoke(arguments: JsonObject): JsonObject = app.tasks.run(owner) {
                val call = ToolCall(UUID.randomUUID().toString(), "packet_capture_rotate_root", arguments)
                WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
            }
            withTimeout(120_000) {
                val probe = invoke(objectOf("action" to string("probe")))
                assertEquals(probe.toString(), true, probe["probeObserved"]?.jsonPrimitive?.boolean)
                assertEquals(true, invoke(objectOf("action" to string("status")))["serviceProbePassed"]?.jsonPrimitive?.boolean)
                val capture = invoke(objectOf("action" to string("start"), "interface" to string("lo"), "filter" to string("less 1"),
                    "seconds" to JsonPrimitive(1), "segmentBytes" to JsonPrimitive(4096), "bytes" to JsonPrimitive(4096)))
                val artifact = requireNotNull(capture.text("captureArtifact"))
                val files = app.spaces.conversation(owner)
                val manifest = WireJson.decodeFromString<RootCaptureManifest>(files.readBinary(artifact, 65536).toString(Charsets.UTF_8))
                assertEquals(1, manifest.segments.size)
                val segment = manifest.segments.single(); assertEquals(24L, segment.bytes); assertEquals(0L, segment.packets)
                val data = files.readBinary(artifact.substringBeforeLast('/') + "/" + segment.fileName, 4096)
                CaptureSealedPcap.verify(data.inputStream(), segment, CaptureRotationOptions(segmentBytes = 4096, bytes = 4096))
                assertEquals(0L, app.managedRuns.pendingCount(owner))
            }
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally { withContext(NonCancellable) {
            try {
                check(app.managedRuns.recover(owner) == 0) { "Unconfirmed capture fixture retained: $owner" }
                app.spaces.delete(owner, app.tasks::quiesce)
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
        } }
    }
}
