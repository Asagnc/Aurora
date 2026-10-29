package io.github.asagnc.aurora.root

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.MainActivity
import io.github.asagnc.aurora.core.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit maximum segment/storage configuration, using only owned loopback UDP. */
@RunWith(AndroidJUnit4::class)
class CaptureRotationBudgetDeviceTest {
    @RootRequired @Test fun maximumSegmentCountWith512MiBBudgetPublishesVerifiedSegments() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraCaptureBudgetTest") == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        check(app.filesDir.usableSpace >= 1536L * 1024 * 1024) { "Need 1.5GiB free for owned Root/App copies" }
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasKept = activity.window.attributes.flags and flag != 0
        instrumentation.runOnMainSync { activity.window.addFlags(flag) }
        app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
        val owner = app.conversations.create("Aurora capture budget qualification fixture")
        var failure: Throwable? = null
        try { withTimeout(200_000) { qualify(app, owner) } }
        catch (caught: Throwable) { failure = caught; throw caught }
        finally { withContext(NonCancellable) {
            try {
                assertEquals("Unconfirmed budget fixture retained: $owner", 0, app.managedRuns.recover(owner))
                app.spaces.delete(owner, app.tasks::quiesce)
            } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
            finally {
                app.root.disconnect()
                if (!wasKept) instrumentation.runOnMainSync { activity.window.clearFlags(flag) }
            }
        } }
    }

    private suspend fun qualify(app: AuroraApplication, owner: String) = coroutineScope {
        val tool = CaptureRotationTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        suspend fun invoke(args: JsonObject): JsonObject = app.tasks.run(owner) {
            val call = ToolCall(UUID.randomUUID().toString(), "packet_capture_rotate_root", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
        }
        val probe = invoke(objectOf("action" to string("probe")))
        assertEquals(probe.toString(), JsonPrimitive(true), probe["probeObserved"])
        val loopback = InetAddress.getByName("127.0.0.1")
        DatagramSocket(InetSocketAddress(loopback, 0)).use { receiver ->
            receiver.soTimeout = 2000
            val traffic = launch(Dispatchers.IO) { traffic(receiver, loopback) }
            try {
                val result = invoke(objectOf("action" to string("start"), "interface" to string("lo"),
                    "filter" to string("udp and port ${receiver.localPort}"), "seconds" to JsonPrimitive(120),
                    "segmentBytes" to JsonPrimitive(8388608), "bytes" to JsonPrimitive(536870912), "segments" to JsonPrimitive(64)))
                verify(app, owner, result)
            } finally { traffic.cancelAndJoin() }
        }
    }

    private suspend fun traffic(receiver: DatagramSocket, loopback: InetAddress) {
        DatagramSocket(InetSocketAddress(loopback, 0)).use { sender ->
            val data = ByteArray(60000) { (it % 127).toByte() }
            val packet = DatagramPacket(data, data.size, loopback, receiver.localPort)
            val incoming = DatagramPacket(ByteArray(65536), 65536)
            while (currentCoroutineContext().isActive) {
                sender.send(packet); incoming.length = 65536; receiver.receive(incoming)
                check(incoming.length == data.size)
                delay(1)
            }
        }
    }

    private suspend fun verify(app: AuroraApplication, owner: String, result: JsonObject) {
        assertEquals(result.toString(), JsonPrimitive(true), result["workerSucceeded"])
        val artifact = requireNotNull(result.text("captureArtifact")) { result.toString() }
        val files = app.spaces.conversation(owner)
        val manifest = WireJson.decodeFromString<RootCaptureManifest>(files.readBinary(artifact, 65536).toString(Charsets.UTF_8))
        val options = CaptureRotationOptions(segmentBytes = 8388608, bytes = 536870912)
        assertEquals(64, manifest.segments.size)
        val bytes = manifest.segments.sumOf { it.bytes }
        assertTrue("Expected near-limit capture: $bytes", bytes in 500_000_000L..536870912L)
        val final = requireNotNull(manifest.finalRecord)
        assertEquals(0, final.status); assertEquals("storage_budget", final.reason)
        assertEquals(64, final.segments); assertEquals(bytes, final.bytes)
        assertEquals(manifest.segments.sumOf { it.packets }, final.packets)
        for (segment in manifest.segments) {
            val data = files.readBinary(artifact.substringBeforeLast('/') + "/" + segment.fileName, 8388608)
            CaptureSealedPcap.verify(data.inputStream(), segment, options)
        }
        assertEquals(0L, app.managedRuns.pendingCount(owner))
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "AURORA_CAPTURE_BUDGET_VERIFIED segments=64 bytes=$bytes packets=${final.packets}\n")
        })
    }
}
