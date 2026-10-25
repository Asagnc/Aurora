package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootPhase
import io.github.asagnc.aurora.root.RootRequired
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit final-device gate. One experimental EtherType frame on loopback only; no route or interface changes. */
@RunWith(AndroidJUnit4::class)
class PacketInjectionDeviceTest {
    @RootRequired @Test fun selectedOwnedLoopbackFrameUsesActualBinderWorkerAndLedger() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("auroraRootTest") == "true" && arguments.getString("auroraPacketInjectionTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
        val owner = app.conversations.create("Owned loopback injection qualification")
        var confirmed = false
        try {
            val frame = ByteArray(60)
            frame[12] = 0x88.toByte(); frame[13] = 0xb5.toByte()
            "aurora-owned-loopback".toByteArray().copyInto(frame, 14)
            val tool = PacketInjectionTool(requireNotNull(app.root.packetClient), owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path) }
            val call = ToolCall(UUID.randomUUID().toString(), "packet_inject_root", objectOf("interface" to string("lo"),
                "frameBase64" to string(Base64.getEncoder().encodeToString(frame)), "packets" to JsonPrimitive(1), "bytes" to JsonPrimitive(60)))
            val result = withTimeout(30000) { app.tasks.run(owner) {
                WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
            } }
            assertNull("Fixture retained as $owner: $result", result["error"])
            val injection = result["injection"]!!.jsonObject
            assertEquals(JsonPrimitive(true), injection["complete"])
            assertEquals(1L, injection.number("acceptedPackets")); assertEquals(60L, injection.number("acceptedBytes"))
            assertEquals("packet_inject_root", app.toolRuns.list(owner).single().tool)
            confirmed = true
        } finally {
            // Preserve the diagnostic owner if Root disappearance prevents an actual outcome proof.
            if (confirmed) withContext(NonCancellable) { app.spaces.delete(owner, app.tasks::quiesce) }
        }
    }
}
