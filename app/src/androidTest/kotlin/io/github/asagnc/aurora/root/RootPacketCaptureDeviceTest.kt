package io.github.asagnc.aurora.root

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandLogObserver
import io.github.asagnc.aurora.runtime.PacketInspectionTool
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Compiled on CI; requires explicit final real-device Root qualification. */
@RunWith(AndroidJUnit4::class)
class RootPacketCaptureDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication

    @RootRequired @Test fun exactHeaderBudgetPublishesOnlyThisRunAndRemainsReadable() = runBlocking { fixture { owner ->
        val id = UUID.randomUUID().toString()
        val request = PacketCaptureRequest.parse(objectOf("interface" to string("lo"), "bytes" to JsonPrimitive(24)))
        val reply = invoke(owner, id, request)
        assertNull(reply.toString(), reply["error"])
        val capture = reply.getValue("capture").jsonObject
        assertEquals("byte_budget", capture.text("reason"))
        assertEquals(0L, capture.number("packets"))
        val path = app.spaces.commandRoot(owner).resolve(requireNotNull(capture.text("path")))
        assertEquals(24L, Files.size(path))
        assertEquals(app.applicationInfo.uid, Os.lstat(path.toString()).st_uid)
        assertEquals(app.applicationInfo.uid, Os.lstat(path.resolveSibling("capture-status.json").toString()).st_uid)
        val decoded = PacketInspectionTool(app, app.spaces.commandRoot(owner), app.commands).execute(ToolCall("decode", "packet_inspect",
            objectOf("path" to string(requireNotNull(capture.text("path"))))))
        assertEquals(decoded, 0L, WireJson.parseToJsonElement(decoded).jsonObject.number("exitCode"))
    } }

    @RootRequired @Test fun idleDeadlineDoesNotDependOnReceivingPackets() = runBlocking { fixture { owner ->
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { socket ->
            val request = PacketCaptureRequest.parse(objectOf("interface" to string("lo"), "seconds" to JsonPrimitive(1),
                "filter" to string("udp dst port ${socket.localPort}")))
            val reply = withTimeout(15000) { invoke(owner, UUID.randomUUID().toString(), request) }
            assertNull(reply.toString(), reply["error"])
            assertEquals("duration", reply.getValue("capture").jsonObject.text("reason"))
            assertEquals(0L, reply.getValue("capture").jsonObject.number("packets"))
        }
    } }

    @RootRequired @Test fun controlledLoopbackPacketsArePublishedAndOfflineReadable() = runBlocking { fixture { owner -> coroutineScope {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { receiver ->
            val id = UUID.randomUUID().toString()
            val ready = CompletableDeferred<Unit>()
            val watch = launch { CommandLogObserver(app).observe(owner, "runs/$id", true).collect {
                if (it.stderr.text.contains("aurora_capture_ready")) ready.complete(Unit)
            } }
            val capture = async { invoke(owner, id, PacketCaptureRequest.parse(objectOf("interface" to string("lo"),
                "seconds" to JsonPrimitive(10), "packets" to JsonPrimitive(1),
                "filter" to string("udp dst port ${receiver.localPort}")))) }
            try {
                withTimeout(5000) { ready.await() }
                DatagramSocket().use { sender ->
                    val data = "aurora-controlled-loopback".toByteArray()
                    sender.send(DatagramPacket(data, data.size, InetAddress.getByName("127.0.0.1"), receiver.localPort))
                }
                val result = withTimeout(15000) { capture.await() }
                assertNull(result.toString(), result["error"])
                val detail = result.getValue("capture").jsonObject
                assertEquals("packet_budget", detail.text("reason"))
                assertEquals(1L, detail.number("packets"))
                assertTrue(Files.size(app.spaces.commandRoot(owner).resolve(requireNotNull(detail.text("path")))) > 40)
            } finally { capture.cancelAndJoin(); watch.cancelAndJoin() }
        }
    } } }

    @RootRequired @Test fun invalidFilterDoesNotClaimCaptureOrAffectOtherFiles() = runBlocking { fixture { owner ->
        val sentinel = app.spaces.commandRoot(owner).resolve("preserve")
        Files.writeString(sentinel, "unchanged")
        val reply = invoke(owner, UUID.randomUUID().toString(), PacketCaptureRequest.parse(objectOf("filter" to string("udp and ("))))
        assertNotNull(reply["error"])
        assertNull(reply["capture"])
        assertEquals("unchanged", Files.readString(sentinel))
    } }

    private suspend fun invoke(owner: String, id: String, request: PacketCaptureRequest) =
        WireJson.parseToJsonElement(requireNotNull(app.root.packetClient).execute(owner, id, request)).jsonObject

    private suspend fun fixture(block: suspend (String) -> Unit) {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        app.ready.await(); app.root.connect()
        check(app.root.packetClient != null)
        val owner = app.conversations.create("Root packet qualification")
        try { block(owner) } finally {
            withContext(NonCancellable) {
                try { app.spaces.delete(owner, app.tasks::quiesce) } finally { app.root.disconnect() }
            }
        }
    }
}
