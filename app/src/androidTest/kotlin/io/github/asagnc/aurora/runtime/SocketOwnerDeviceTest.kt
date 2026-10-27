package io.github.asagnc.aurora.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootPhase
import io.github.asagnc.aurora.root.RootRequired
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Final acceptance only: own loopback socket, no external traffic or network configuration changes. */
@RunWith(AndroidJUnit4::class)
class SocketOwnerDeviceTest {
    @RootRequired @Test fun ownLiveSocketUidThroughBinderLedger() = runBlocking {
        check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
        val owner = app.conversations.create("Owned socket snapshot qualification")
        app.spaces.conversation(owner)
        var confirmed = false
        try { withContext(Dispatchers.IO) {
            val tool = SocketOwnerTool(app, requireNotNull(app.root.commandClient), owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path) }
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                Socket("127.0.0.1", server.localPort).use { client -> server.accept().use {
                    // Android Java sockets use dual-stack AF_INET6 even for an
                    // IPv4 destination; SOCK_DIAG requires the actual family.
                    val args = objectOf("family" to string("ipv6"), "transport" to string("tcp"), "localAddress" to string("::ffff:127.0.0.1"),
                        "localPort" to JsonPrimitive(client.localPort), "remoteAddress" to string("::ffff:127.0.0.1"), "remotePort" to JsonPrimitive(server.localPort))
                    val call = ToolCall(UUID.randomUUID().toString(), "socket_owner_root", args)
                    val result = withTimeout(20000) { app.tasks.run(owner) {
                        WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
                    } }
                    assertNull("Fixture retained as $owner: $result", result["error"])
                    val snapshot = result["socketSnapshot"]!!.jsonObject
                    assertEquals(snapshot.toString(), "exact_socket_uid", snapshot.text("status"))
                    assertEquals(android.os.Process.myUid().toLong(), snapshot.array("matches").single().jsonObject.number("uid"))
                    assertEquals("socket_owner_root", app.toolRuns.list(owner).single().tool)
                } }
            }
            confirmed = true
        } } finally {
            if (confirmed) withContext(NonCancellable) { app.spaces.delete(owner, app.tasks::quiesce) }
        }
    }
}
