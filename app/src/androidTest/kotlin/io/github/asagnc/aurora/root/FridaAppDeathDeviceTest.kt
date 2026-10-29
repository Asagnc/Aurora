package io.github.asagnc.aurora.root

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.RootManagedRunRegistry
import io.github.asagnc.aurora.runtime.CommandRequest
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit two-phase App death. Prepare intentionally crashes instrumentation;
 * only verify can pass. The sole external target is Aurora's installed fixture. */
@RunWith(AndroidJUnit4::class)
class FridaAppDeathDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as AuroraApplication
    private val checkpoint get() = app.cacheDir.toPath().resolve("frida-owned-app-death-ticket.json")
    private val fixture = "io.github.asagnc.aurora.fridafixture"
    private val title = "Aurora Frida App death qualification fixture"

    @RootRequired @Test fun prepareLoadedResumedOwnedTargetThenKillOnlyThisAppProcess() = runBlocking<Unit> {
        requirePhase("prepare")
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        require(!Files.exists(checkpoint)) { "Verify the previous owned death checkpoint first" }
        FridaDeviceForeground(app).use { supervisorScope {
            app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
            val owner = app.conversations.create(title)
            val tools = OwnedTools(app, owner)
            val probe = tools.frida(objectOf("action" to string("probe")))
            assertEquals(probe.toString(), JsonPrimitive(true), probe["probeSucceeded"])
            val source = "send({aurora:'owned_app_death_fixture',pid:Process.id});setInterval(()=>{},1000);"
            app.spaces.conversation(owner).editBatch(listOf(FileEdit("owned-app-death.js", null, source)))
            val sha = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
            val task = async { tools.frida(objectOf("action" to string("spawn"), "package" to string(fixture),
                "source" to string("conversation"), "path" to string("owned-app-death.js"), "scriptSha256" to string(sha),
                "observeMs" to JsonPrimitive(60_000))) }
            val ticket = awaitResumedInjection(owner, tools)
            require(!task.isCompleted && ticket.spawnPackage == fixture)
            Files.write(checkpoint, WireJson.encodeToString(ticket).toByteArray(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_FRIDA_APP_DEATH_PREPARED=$owner\n") })
            Process.killProcess(Process.myPid())
            delay(1000)
            error("Intentional owned App death did not occur")
        } }
    }

    @RootRequired @Test fun verifyOldTicketRecoversWithoutReplayingOrKillingResumedTarget() = runBlocking<Unit> {
        requirePhase("verify")
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        assertEquals("Startup must not reconnect or replay injection", RootPhase.ORDINARY, app.root.state.value.phase)
        require(Files.isRegularFile(checkpoint, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(checkpoint))
        val bytes = Files.newInputStream(checkpoint).use { it.readNBytes(8193) }
        require(bytes.size in 1..8192)
        val ticket = WireJson.decodeFromString<RootManagedRunTicket>(bytes.toString(Charsets.UTF_8)).also { it.validate() }
        require(ticket.appUid == Process.myUid() && ticket.action == "spawn" && ticket.spawnPackage == fixture)
        require(app.conversations.header(ticket.owner)?.title == title)
        assertEquals(ticket.digest(), RootManagedRunRegistry(app.conversations).pending(ticket.owner).single().digest())
        var failure: Throwable? = null
        FridaDeviceForeground(app).use { try {
            app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
            val connection = requireNotNull(app.root.fridaConnection)
            val result = connection.client.recover(ticket)
            assertTrue(result.toString(), result.confirmed)
            val proof = requireNotNull(result.cleanup) { result.toString() }
            assertTrue(ticket.confirms(proof))
            val target = requireNotNull(proof.spawnTarget)
            val stat = OwnedTools(app, ticket.owner).root(listOf("/system/bin/cat", "/proc/${target.pid}/stat"))
            assertEquals(stat.toString(), 0L, stat.number("exitCode"))
            assertEquals(target.startTicks, linuxProcessStartTicks(requireNotNull(stat["stdout"]?.jsonObject?.text("text"))))
            val log = app.spaces.commandRoot(ticket.owner).resolve(requireNotNull(result.artifact))
            val text = Files.newInputStream(log).use { it.readNBytes(65537).toString(Charsets.UTF_8) }
            assertEquals("Selected script must execute once", 1, Regex("owned_app_death_fixture").findAll(text).count())
            assertTrue(text, text.contains("\"cleanupConfirmed\":true") && text.contains("\"resourcesClear\":true"))
            assertEquals(0, app.managedRuns.recover(ticket.owner))
            assertEquals(0L, app.managedRuns.pendingCount(ticket.owner))
        } catch (caught: Throwable) { failure = caught; throw caught }
        finally { withContext(NonCancellable) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am force-stop $fixture")).use { it.readNBytes(4096) }
            try {
                if (failure == null) {
                    app.spaces.delete(ticket.owner, app.tasks::quiesce)
                    Files.delete(checkpoint)
                }
            } finally { app.root.disconnect() }
        } } }
    }

    private fun requirePhase(phase: String) {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraFridaAppDeathPhase") == phase)
    }

    private suspend fun awaitResumedInjection(owner: String, tools: OwnedTools): RootManagedRunTicket = withTimeout(20_000) {
        while (true) {
            val ticket = RootManagedRunRegistry(app.conversations).pending(owner).singleOrNull()
            if (ticket != null) {
                require(ticket.action == "spawn" && ticket.spawnPackage == fixture)
                val state = app.filesDir.toPath().toRealPath().resolve("spaces/resources/root-managed-runtime/${ticket.runId}")
                val resumed = tools.root(listOf("/system/bin/test", "-f", "$state/spawn.5"))
                if (resumed.number("exitCode") == 0L) {
                    val observed = tools.root(listOf("/system/bin/grep", "-m", "1", "owned_app_death_fixture", "$state/worker-events.log"))
                    if (observed.number("exitCode") == 0L) return@withTimeout ticket
                }
            }
            delay(200)
        }
        @Suppress("UNREACHABLE_CODE") error("No resumed owned target")
    }

    private class OwnedTools(private val app: AuroraApplication, private val owner: String) {
        private val tool = FridaTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
        suspend fun frida(args: JsonObject): JsonObject = app.tasks.run(owner) {
            val call = ToolCall(UUID.randomUUID().toString(), "frida", args)
            WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
        }
        suspend fun root(argv: List<String>): JsonObject = WireJson.parseToJsonElement(
            requireNotNull(app.root.commandClient).execute(owner, CommandRequest(argv, timeoutMs = 1500))).jsonObject
    }
}
