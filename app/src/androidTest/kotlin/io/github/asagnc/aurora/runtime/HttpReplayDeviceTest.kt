package io.github.asagnc.aurora.runtime

import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Owned loopback only. Compiled now; executed with the concentrated device suite later. */
@RunWith(AndroidJUnit4::class)
class HttpReplayDeviceTest {
    @Test fun binaryResponseUsesRealLedgerAndConversationArtifact() = runBlocking {
        fixture { app, owner ->
            WireOrigin(false).use { origin ->
                val root = app.spaces.commandRoot(owner)
                val tool = HttpReplayTool(root) { call, path -> app.toolRuns.attachArtifact(owner, call, path) }
                val result = app.tasks.run(owner) {
                    WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call(origin.url))).jsonObject
                }
                assertEquals(result.toString(), 200L, result.number("status"))
                assertArrayEquals(byteArrayOf(0, 1, -1, 9), withContext(Dispatchers.IO) {
                    Files.readAllBytes(root.resolve(requireNotNull(result.text("bodyPath"))))
                })
                val records = app.toolRuns.list(owner)
                assertEquals(1, records.size); assertEquals("completed", records.single().state)
                assertEquals(result.text("artifactDirectory"), records.single().artifactDirectory)
            }
        }
    }

    @Test fun conversationDeletionCancelsActualHttpWriterBeforeRemovingFiles() = runBlocking {
        fixture { app, owner ->
            WireOrigin(true).use { origin ->
                val root = app.spaces.commandRoot(owner)
                val tool = HttpReplayTool(root) { call, path -> app.toolRuns.attachArtifact(owner, call, path) }
                val active = async { app.tasks.run(owner) { app.toolRuns.recording(owner, tool).execute(call(origin.url)) } }
                assertTrue(withContext(Dispatchers.IO) { origin.started.await(5, TimeUnit.SECONDS) })
                withTimeout(5000) { app.spaces.delete(owner, app.tasks::quiesce) }
                active.join(); assertTrue(active.isCancelled)
                assertFalse(withContext(Dispatchers.IO) { Files.exists(root) })
                assertTrue(withContext(Dispatchers.IO) { origin.stopped.await(3, TimeUnit.SECONDS) })
            }
        }
    }

    private fun call(url: String) = ToolCall(UUID.randomUUID().toString(), "http_request", objectOf(
        "url" to string(url), "method" to string("GET"), "route" to string("direct"), "timeoutMs" to JsonPrimitive(10000)))

    private suspend fun fixture(block: suspend (AuroraApplication, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Android 17 local-network acceptance needs explicit OS permission first", Build.VERSION.SDK_INT < 37 ||
            context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") == PackageManager.PERMISSION_GRANTED)
        val app = context.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        val owner = app.conversations.create("Owned HTTP qualification")
        try { block(app, owner) } finally { withContext(NonCancellable) { app.spaces.delete(owner, app.tasks::quiesce) } }
    }

    private class WireOrigin(private val slow: Boolean) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        private val closed = AtomicBoolean()
        private val client = AtomicReference<Socket?>()
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val url = "http://127.0.0.1:${server.localPort}/owned"
        private val worker = thread(isDaemon = true, name = "owned-http-device-fixture") {
            try { server.accept().use { socket ->
                client.set(socket); socket.soTimeout = 15000
                if (closed.get()) return@use
                val reader = socket.getInputStream().bufferedReader()
                var budget = 0
                while (true) {
                    val line = reader.readLine() ?: error("Missing request headers")
                    budget += line.length; check(budget <= 32768)
                    if (line.isEmpty()) break
                }
                val output = socket.getOutputStream()
                if (slow) {
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nConnection: close\r\n\r\nx".toByteArray())
                    output.flush(); started.countDown()
                    check(socket.getInputStream().read() == -1)
                } else {
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(byteArrayOf(0, 1, -1, 9)); output.flush(); started.countDown()
                }
            } } catch (_: IOException) { } finally { stopped.countDown() }
        }
        override fun close() {
            closed.set(true); server.close(); client.get()?.close(); worker.join(3000)
            check(!worker.isAlive) { "Owned HTTP fixture did not stop" }
        }
    }
}
