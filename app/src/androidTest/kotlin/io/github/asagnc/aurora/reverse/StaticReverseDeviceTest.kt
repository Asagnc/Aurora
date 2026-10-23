package io.github.asagnc.aurora.reverse

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.*
import io.github.asagnc.aurora.root.RootRequired
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit offline qualification of already-installed official tools, never an automatic download. */
@RunWith(AndroidJUnit4::class)
class StaticReverseDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @RootRequired @LinuxImageRequired @Test fun startupQualificationAndReadOnlyToolMount() = runBlocking { fixture { app, owner, generation ->
        assertEquals(JsonPrimitive(true), app.sharedToolchain.status(generation)["runtimeReady"])
        val sentinel = "/opt/.aurora-readonly-${UUID.randomUUID()}"
        val result = tools(app, owner, generation, listOf("/bin/sh", "-c",
            "test -f /opt/jadx/lib/jadx-1.5.6-all.jar || exit 11; if touch '$sentinel' 2>/dev/null; then rm -f '$sentinel'; exit 12; fi"))
        assertExit(result, 0)
    } }

    @RootRequired @LinuxImageRequired @Test fun jadxDecompilesOwnedSmallDexAndPublishesReadableOutput() = runBlocking { fixture { app, owner, generation ->
        val source = app.spaces.commandRoot(owner).resolve("owned-probe.dex")
        instrumentation.context.assets.open("reverse/aurora-probe.dex").use { input -> Files.copy(input, source) }
        stage(app, owner, generation, source.toString(), "owned-probe.dex")
        val result = analyze(app, owner, generation, analysis("jadx", "java", "owned-probe.dex"))
        assertEquals(result.toString(), JsonPrimitive(true), result["success"])
        val output = requireNotNull(result.text("output")).removePrefix(".aurora-linux/project/")
        val check = app.linux.runs.execute(owner, listOf("/bin/grep", "-F", "aurora-reverse-fixture",
            "/mnt/$output/sources/io/github/asagnc/aurora/fixture/AuroraReverseProbe.java"), generation = generation)
        assertExit(check, 0)
        val selected = analyze(app, owner, generation, JsonObject(analysis("jadx", "java", "owned-probe.dex") +
            ("className" to string("io.github.asagnc.aurora.fixture.AuroraReverseProbe"))))
        assertEquals(selected.toString(), JsonPrimitive(true), selected["success"])
        val selectedPath = requireNotNull(selected.text("selectedSource")).removePrefix(".aurora-linux/project/")
        assertExit(app.linux.runs.execute(owner, listOf("/bin/grep", "-F", "aurora-reverse-fixture", "/mnt/$selectedPath"),
            generation = generation), 0)
        assertEquals(0, app.linux.runs.pendingCount(owner))
    } }

    @RootRequired @LinuxImageRequired @Test fun apktoolDecodesOwnedApkManifest() = runBlocking { fixture { app, owner, generation ->
        stage(app, owner, generation, instrumentation.targetContext.applicationInfo.sourceDir, "aurora.apk")
        val result = analyze(app, owner, generation, analysis("apktool", "manifest", "aurora.apk"))
        assertEquals(result.toString(), JsonPrimitive(true), result["success"])
        val output = requireNotNull(result.text("output")).removePrefix(".aurora-linux/project/")
        assertExit(app.linux.runs.execute(owner, listOf("/bin/grep", "-F", "io.github.asagnc.aurora",
            "/mnt/$output/AndroidManifest.xml"), generation = generation), 0)
    } }

    @RootRequired @LinuxImageRequired @Test fun missingInputReturnsFailureAndReleasesTaskLease() = runBlocking { fixture { app, owner, generation ->
        val result = analyze(app, owner, generation, analysis("jadx", "java", "missing.dex"))
        assertEquals(result.toString(), JsonPrimitive(false), result["success"])
        assertEquals("exited", result.text("state"))
        assertTrue(requireNotNull(result.number("exitCode")) > 0)
        assertEquals(0, app.linux.runs.pendingCount(owner))
        assertFalse(app.linux.runs.usesToolBundle(StaticReverseTools.bundleId))
    } }

    @RootRequired @LinuxImageRequired @Test fun cancellingMountedTaskRecoversItsPersistentBundleLease() = runBlocking { fixture { app, owner, generation ->
        coroutineScope {
            val task = async { tools(app, owner, generation, listOf("/bin/sleep", "30")) }
            try {
                withTimeout(10_000) { while (app.linux.runs.pendingCount(owner) == 0) { check(!task.isCompleted); delay(50) } }
                assertTrue(app.linux.runs.usesToolBundle(StaticReverseTools.bundleId))
            } finally { task.cancelAndJoin() }
            assertTrue(task.isCancelled)
        }
        assertEquals(0, app.linux.runs.pendingCount(owner))
        assertFalse(app.linux.runs.usesToolBundle(StaticReverseTools.bundleId))
        assertTrue(app.sharedToolchain.status(generation).array("installed").contains(string(StaticReverseTools.bundleId)))
    } }

    private suspend fun stage(app: AuroraApplication, owner: String, generation: String, source: String, name: String) {
        assertExit(app.linux.runs.execute(owner, listOf("/bin/true"), generation = generation), 0)
        val target = app.spaces.commandRoot(owner).resolve(".aurora-linux/project/$name")
        val reply = requireNotNull(app.root.commandClient).execute(owner, CommandRequest(listOf("/system/bin/cp", source, target.toString())))
        val result = WireJson.parseToJsonElement(reply).jsonObject
        assertEquals(result.toString(), 0L, result.number("exitCode"))
        assertNull(result.toString(), result["error"])
    }

    private fun analysis(tool: String, mode: String, path: String) = objectOf(
        "tool" to string(tool), "mode" to string(mode), "path" to string(path), "timeoutMs" to JsonPrimitive(120000))

    private suspend fun analyze(app: AuroraApplication, owner: String, generation: String, arguments: JsonObject): JsonObject {
        val executor = StaticReverseAnalysisTool({ id, argv, timeout ->
            app.sharedToolchain.analyze(owner, LinuxToolRun(argv, StaticReverseTools.bundleId, timeout, id), generation)
        }, { call, directory -> app.toolRuns.attachArtifact(owner, call, directory) })
        return WireJson.parseToJsonElement(app.toolRuns.recording(owner, executor).execute(
            ToolCall(UUID.randomUUID().toString(), "reverse_analyze", arguments))).jsonObject
    }

    private suspend fun probe(app: AuroraApplication, owner: String, environment: LinuxAgentEnvironment): JsonObject {
        val executor = SharedToolchainTool(app.sharedToolchain, owner,
            { call, directory -> app.toolRuns.attachArtifact(owner, call, directory) }, environment)
        val result = WireJson.parseToJsonElement(app.toolRuns.recording(owner, executor).execute(
            ToolCall("qualification-probe", "shared_toolchain", objectOf("action" to string("probe"))))).jsonObject
        assertEquals(3, result.array("logDirectories").size)
        assertEquals(result.array("logDirectories").first().jsonPrimitive.content,
            app.toolRuns.list(owner).single { it.tool == "shared_toolchain" }.artifactDirectory)
        return result
    }

    private suspend fun tools(app: AuroraApplication, owner: String, generation: String, argv: List<String>) =
        app.sharedToolchain.analyze(owner, LinuxToolRun(argv, StaticReverseTools.bundleId, 60000, UUID.randomUUID().toString()), generation)

    private fun assertExit(proof: JsonObject, code: Long) {
        assertEquals(proof.toString(), JsonPrimitive(true), proof["stopped"])
        val completion = proof.getValue("completion").jsonObject
        assertEquals(proof.toString(), code, completion.number("exitCode"))
        assertEquals(JsonPrimitive(true), completion["scopeRemoved"])
        assertEquals(JsonPrimitive(false), completion["interrupted"])
        assertEquals(JsonPrimitive(false), completion["timedOut"])
    }

    private suspend fun fixture(block: suspend (AuroraApplication, String, String) -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraLinuxImageTest") == "true" &&
            args.getString("auroraReverseTest") == "true") { "Explicit Root/Linux/reverse qualification flags required" }
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); app.root.connect()
        val environment: LinuxAgentEnvironment
        var owner: String? = null
        var original: Throwable? = null
        try {
            check(app.root.linuxExecution != null && app.root.commandClient != null) { "Reconnect current Root service" }
            check(app.linux.runs.pendingCount() == 0) { "Recover pending tasks before exclusive qualification" }
            check(app.sharedToolchain.status(app.linux.registry.current()).array("installed").contains(string(StaticReverseTools.bundleId))) {
                "Explicitly install the pinned tool bundle before offline qualification"
            }
            val id = app.conversations.create("Static reverse qualification"); owner = id
            environment = LinuxAgentEnvironment(app.linux, id)
            try {
                val generation = environment.generation()
                assertEquals(JsonPrimitive(true), probe(app, id, environment)["qualified"])
                block(app, id, generation)
            } finally { environment.close() }
        } catch (failure: Throwable) { original = failure; throw failure }
        finally { withContext(NonCancellable) {
            try { owner?.let { app.spaces.delete(it, app.tasks::quiesce) } }
            catch (cleanup: Throwable) { if (original != null) original.addSuppressed(cleanup) else throw cleanup }
            finally { app.root.disconnect() }
        } }
    }
}
