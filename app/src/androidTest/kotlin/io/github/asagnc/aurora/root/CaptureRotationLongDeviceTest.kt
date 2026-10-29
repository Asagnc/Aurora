package io.github.asagnc.aurora.root

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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

/** Owned loopback traffic only. Awake duration qualification is distinct from screen-off operation. */
@RunWith(AndroidJUnit4::class)
class CaptureRotationLongDeviceTest {
    @RootRequired @Test fun sustainedOwnedLoopbackProducesVerifiedSealedSegments() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraLongCaptureTest") == "true")
        val seconds = requireNotNull(args.getString("auroraCaptureSeconds")).toInt()
        require(seconds in 300..1800)
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        val activity = instrumentation.startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val keep = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val wasKept = activity.window.attributes.flags and keep != 0
        instrumentation.runOnMainSync { activity.window.addFlags(keep) }
        app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
        val owner = app.conversations.create("Aurora long capture qualification fixture")
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_LONG_CAPTURE_OWNER=$owner seconds=$seconds\n") })
        var original: Throwable? = null
        try { withTimeout((seconds + 120L) * 1000) { qualify(app, owner, seconds) } }
        catch (failure: Throwable) { original = failure; throw failure }
        finally { withContext(NonCancellable) {
            try {
                check(app.managedRuns.recover(owner) == 0) { "Unconfirmed long capture retained: $owner" }
                app.spaces.delete(owner, app.tasks::quiesce)
            } catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
            finally {
                app.root.disconnect()
                if (!wasKept) instrumentation.runOnMainSync { activity.window.clearFlags(keep) }
            }
        } }
    }

    private suspend fun qualify(app: AuroraApplication, owner: String, seconds: Int) = coroutineScope {
        val tool = CaptureRotationTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        val diagnostics = object : ToolExecutor by tool {
            override suspend fun execute(call: ToolCall): String = try { tool.execute(call) }
            catch (failure: Exception) { throw AssertionError("Owned capture failed before a complete result", failure) }
        }
        suspend fun invoke(args: JsonObject): JsonObject = app.tasks.run(owner) {
            val call = ToolCall(UUID.randomUUID().toString(), "packet_capture_rotate_root", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, diagnostics).execute(call)).jsonObject
        }
        val probe = invoke(objectOf("action" to string("probe")))
        assertEquals(probe.toString(), JsonPrimitive(true), probe["probeObserved"])
        val loopback = InetAddress.getByName("127.0.0.1")
        DatagramSocket(InetSocketAddress(loopback, 0)).use { receiver ->
            receiver.soTimeout = 3000
            val traffic = launch(Dispatchers.IO) { sendTraffic(receiver, loopback) }
            try {
                val started = SystemClock.elapsedRealtime()
                val result = invoke(objectOf("action" to string("start"), "interface" to string("lo"),
                    "filter" to string("udp and port ${receiver.localPort}"), "seconds" to JsonPrimitive(seconds),
                    "segmentBytes" to JsonPrimitive(131072), "bytes" to JsonPrimitive(8388608)))
                assertTrue("Capture ended before its requested duration: $result", SystemClock.elapsedRealtime() - started >= (seconds - 1L) * 1000)
                verify(app, owner, result)
            } finally { traffic.cancelAndJoin() }
        }
    }

    private suspend fun sendTraffic(receiver: DatagramSocket, loopback: InetAddress) {
        DatagramSocket(InetSocketAddress(loopback, 0)).use { sender ->
            val payload = ByteArray(512) { (it % 127).toByte() }
            val received = DatagramPacket(ByteArray(1024), 1024)
            while (currentCoroutineContext().isActive) {
                sender.send(DatagramPacket(payload, payload.size, loopback, receiver.localPort))
                receiver.receive(received)
                check(received.length == payload.size)
                delay(500)
            }
        }
    }

    private suspend fun verify(app: AuroraApplication, owner: String, result: JsonObject) {
        assertEquals(result.toString(), JsonPrimitive(true), result["workerSucceeded"])
        val artifact = requireNotNull(result.text("captureArtifact")) { result.toString() }
        val files = app.spaces.conversation(owner)
        val manifest = WireJson.decodeFromString<RootCaptureManifest>(files.readBinary(artifact, 65536).toString(Charsets.UTF_8))
        assertTrue("No segment rotation occurred", manifest.segments.size >= 2)
        assertTrue(manifest.segments.sumOf { it.packets } > 0)
        val final = requireNotNull(manifest.finalRecord)
        assertEquals("duration", final.reason); assertEquals(0, final.status)
        assertEquals(manifest.segments.size, final.segments)
        assertEquals(manifest.segments.sumOf { it.packets }, final.packets)
        assertEquals(manifest.segments.sumOf { it.bytes }, final.bytes)
        for (segment in manifest.segments) {
            val data = files.readBinary(artifact.substringBeforeLast('/') + "/" + segment.fileName, 262144)
            CaptureSealedPcap.verify(data.inputStream(), segment, CaptureRotationOptions(segmentBytes = 131072, bytes = 8388608))
        }
        assertEquals(0L, app.managedRuns.pendingCount(owner))
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "AURORA_LONG_CAPTURE_VERIFIED segments=${manifest.segments.size} packets=${manifest.segments.sumOf { it.packets }}\n")
        })
    }
}
