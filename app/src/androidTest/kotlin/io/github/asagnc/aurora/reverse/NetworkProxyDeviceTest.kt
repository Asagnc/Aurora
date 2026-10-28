package io.github.asagnc.aurora.reverse

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.*
import io.github.asagnc.aurora.root.RootRequired
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit final device matrix, using only a preinstalled bundle and owned loopback fixture. Never auto-downloads. */
@RunWith(AndroidJUnit4::class)
class NetworkProxyDeviceTest {
    @RootRequired @LinuxImageRequired @Test fun probePtyPublicCaAndExactStopShareThePersistentBundleLease() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("auroraRootTest") == "true" && arguments.getString("auroraLinuxImageTest") == "true" &&
            arguments.getString("auroraProxyTest") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        check(!app.terminals.hasActive) { "Finish existing terminal tasks before the exclusive fixture" }
        app.root.connect(); check(app.root.linuxExecution != null)
        check(app.linux.runs.pendingCount() == 0) { "Recover existing tasks before the exclusive proxy qualification" }
        val installed = app.sharedToolchain.status(app.linux.registry.current(), "network-proxy")
        check(installed.array("installed").contains(string(NetworkProxyRuntime.bundleId))) { "Explicitly install the fixed bundle before offline qualification" }
        val owner = app.conversations.create("Aurora owned proxy qualification")
        val environment = LinuxAgentEnvironment(app.linux, owner)
        var failure: Throwable? = null
        try {
            val prepare: suspend (String, String) -> Unit = { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
            val tools = ToolRouter(listOf(SharedToolchainTool(app.sharedToolchain, owner, prepare, environment), NetworkProxyTool(app.sharedToolchain, owner, prepare, environment)))
            suspend fun invoke(name: String, args: JsonObject): JsonObject = app.tasks.run(owner) {
                val call = ToolCall(UUID.randomUUID().toString(), name, args)
                WireJson.parseToJsonElement(app.toolRuns.recording(owner, tools).execute(call)).jsonObject
            }
            withTimeout(360000) {
                val port = arguments.getString("auroraProxyPort")?.toInt() ?: 18080
                val probe = invoke("shared_toolchain", objectOf("action" to string("probe"), "family" to string("network-proxy"), "probePort" to JsonPrimitive(port)))
                assertEquals(probe.toString(), JsonPrimitive(true), probe["qualified"])
                assertEquals(probe.toString(), JsonPrimitive(true), probe["cleanup"]?.jsonObject?.get("removed"))
                assertEquals(JsonPrimitive(false), probe.getValue("cleanup").jsonObject["pending"])
                val started = invoke("network_proxy", objectOf("action" to string("start"), "options" to objectOf("port" to JsonPrimitive(port), "durationSeconds" to JsonPrimitive(30))))
                assertEquals(started.toString(), JsonPrimitive(true), started["running"])
                assertTrue(app.terminals.hasActive)
                val session = requireNotNull(started.text("session"))
                assertTrue(app.linux.runs.usesToolBundle(NetworkProxyRuntime.bundleId))
                val exported = invoke("network_proxy", objectOf("action" to string("export_ca"), "session" to string(session)))
                assertEquals(exported.toString(), JsonPrimitive(true), exported["publicOnly"])
                val bytes = app.spaces.conversation(owner).readBinary(requireNotNull(exported.text("path")), 16384)
                assertEquals(exported.text("sha256"), publicProxyCaSha256(bytes))
                val stopped = invoke("network_proxy", objectOf("action" to string("stop"), "session" to string(session)))
                assertEquals(stopped.toString(), JsonPrimitive(false), stopped.getValue("linuxRecovery").jsonObject["pending"])
                assertEquals(stopped.toString(), JsonPrimitive(false), stopped.getValue("cleanup").jsonObject["pending"])
                assertEquals(0, app.linux.runs.pendingCount(owner)); assertFalse(app.linux.runs.usesToolBundle(NetworkProxyRuntime.bundleId))
                assertFalse(app.terminals.hasActive)
                assertNotNull(app.toolRuns.list(owner).single { it.tool == "network_proxy" && it.artifactDirectory != null }.artifactDirectory)
            }
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally { withContext(NonCancellable) {
            try { app.terminals.forget(owner); app.linux.runs.recover(owner); environment.close(); app.spaces.delete(owner, app.tasks::quiesce) }
            catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
            finally { app.root.disconnect() }
        } }
    }
}
