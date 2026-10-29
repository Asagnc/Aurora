package io.github.asagnc.aurora.root

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.RootManagedRunRegistry
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A killed worker cannot attest unload. Unknown must block deletion until the
 * explicitly owned resumed target exits; an empty cgroup alone is insufficient. */
@RunWith(AndroidJUnit4::class)
class FridaWorkerDeathDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val fixture = "io.github.asagnc.aurora.fridafixture"

    @RootRequired @Test fun killedWorkerRetainsUnknownWhileOwnedTargetLivesThenRecoversOnExit() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraFridaWorkerDeathTest") == "true")
        val app = instrumentation.targetContext.applicationContext as AuroraApplication
        app.ready.await(); check(!withContext(Dispatchers.Main) { app.agentTasks.busy })
        FridaDeviceForeground(app).use {
            app.root.connect(); check(app.root.state.value.phase == RootPhase.READY)
            val owner = app.conversations.create("Aurora Frida worker death qualification fixture")
            instrumentation.sendStatus(2, Bundle().apply { putString("stream", "AURORA_FRIDA_WORKER_DEATH_OWNER=$owner\n") })
            var failure: Throwable? = null
            try { withTimeout(120_000) { qualify(app, owner) } }
            catch (caught: Throwable) { failure = caught; throw caught }
            finally { withContext(NonCancellable) {
                stopFixture()
                try {
                    assertEquals("Unconfirmed worker death fixture retained: $owner", 0, app.managedRuns.recover(owner))
                    if (failure == null || args.getString("auroraRetainFridaFailure") != "true")
                        app.spaces.delete(owner, app.tasks::quiesce)
                } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup }
                finally { app.root.disconnect() }
            } }
        }
    }

    private suspend fun qualify(app: AuroraApplication, owner: String) = supervisorScope {
        val tools = FridaDeviceTools(app, owner)
        val probe = tools.frida(objectOf("action" to string("probe")))
        assertEquals(probe.toString(), JsonPrimitive(true), probe["probeSucceeded"])
        val source = "send({aurora:'owned_worker_death_fixture',pid:Process.id});setInterval(()=>{},1000);"
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("owned-worker-death.js", null, source)))
        val sha = MessageDigest.getInstance("SHA-256").digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
        val task = async { tools.frida(objectOf("action" to string("spawn"), "package" to string(fixture),
            "source" to string("conversation"), "path" to string("owned-worker-death.js"), "scriptSha256" to string(sha),
            "observeMs" to JsonPrimitive(60_000))) }
        try {
            val ticket = awaitResumedInjection(app, owner, tools)
            val worker = admission(app, ticket, tools).worker
            val target = capturedTarget(app, ticket, tools)
            require(!task.isCompleted)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", "AURORA_FRIDA_WORKER_DEATH_READY=${worker.pid}:${worker.startTicks}\n")
            })
            val outcome = withTimeout(30_000) { task.await() }
            assertEquals(outcome.toString(), JsonPrimitive(false), outcome["workerSucceeded"])
            val client = requireNotNull(app.root.managedClient)
            val unknown = client.recover(ticket)
            assertFalse(unknown.toString(), unknown.confirmed)
            assertEquals("managed_outcome_uncertain", unknown.error)
            val alive = tools.root(listOf("/system/bin/cat", "/proc/${target.pid}/stat"))
            assertEquals(alive.toString(), 0L, alive.number("exitCode"))
            assertEquals(target.startTicks, linuxProcessStartTicks(requireNotNull(alive["stdout"]?.jsonObject?.text("text"))))
            assertEquals(1L, app.managedRuns.pendingCount(owner))
            val deletion = runCatching { app.spaces.delete(owner, app.tasks::quiesce) }
            assertTrue("Unknown injection must block deletion", deletion.isFailure)
            assertNotNull(app.conversations.header(owner))
            stopFixture()
            val exited = client.recover(ticket)
            assertTrue(exited.toString(), exited.confirmed)
            assertNull("Target exit must not invent unload proof", exited.cleanup)
            assertTrue(requireNotNull(exited.targetExit).matches(ticket))
            assertEquals(0, app.managedRuns.recover(owner))
            assertEquals(0L, app.managedRuns.pendingCount(owner))
        } finally { task.cancelAndJoin() }
    }

    private suspend fun admission(app: AuroraApplication, ticket: RootManagedRunTicket, tools: FridaDeviceTools): RootManagedAdmission {
        val path = app.filesDir.toPath().toRealPath().resolve("spaces/resources/root-managed-runtime/${ticket.runId}/managed.ready")
        val reply = tools.root(listOf("/system/bin/base64", path.toString()))
        assertEquals(reply.toString(), 0L, reply.number("exitCode"))
        val bytes = Base64.decode(requireNotNull(reply["stdout"]?.jsonObject?.text("text")), Base64.DEFAULT)
        return RootManagedAdmission.decode(ticket, bytes)
    }

    private suspend fun capturedTarget(app: AuroraApplication, ticket: RootManagedRunTicket, tools: FridaDeviceTools): RootTargetIdentity {
        val state = app.filesDir.toPath().toRealPath().resolve("spaces/resources/root-managed-runtime/${ticket.runId}")
        assertEquals(1L, tools.root(listOf("/system/bin/test", "-e", "$state/spawn.4")).number("exitCode"))
        val phases = (1..5).map { phase ->
            if (phase == 4) null else {
                val reply = tools.root(listOf("/system/bin/base64", "$state/spawn.$phase"))
                assertEquals(reply.toString(), 0L, reply.number("exitCode"))
                Base64.decode(requireNotNull(reply["stdout"]?.jsonObject?.text("text")), Base64.DEFAULT)
            }
        }
        val observed = RootSpawnState.decode(ticket, phases)
        assertTrue(observed.resumed)
        return requireNotNull(observed.target)
    }

    private suspend fun awaitResumedInjection(app: AuroraApplication, owner: String, tools: FridaDeviceTools): RootManagedRunTicket = withTimeout(20_000) {
        while (true) {
            val ticket = RootManagedRunRegistry(app.conversations).pending(owner).singleOrNull()
            if (ticket != null) {
                require(ticket.action == "spawn" && ticket.spawnPackage == fixture)
                val state = app.filesDir.toPath().toRealPath().resolve("spaces/resources/root-managed-runtime/${ticket.runId}")
                if (tools.root(listOf("/system/bin/test", "-f", "$state/spawn.5")).number("exitCode") == 0L &&
                    tools.root(listOf("/system/bin/grep", "-m", "1", "owned_worker_death_fixture", "$state/worker-events.log")).number("exitCode") == 0L)
                    return@withTimeout ticket
            }
            delay(200)
        }
        @Suppress("UNREACHABLE_CODE") error("No resumed owned target")
    }

    private fun stopFixture() {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("am force-stop $fixture")).use { it.readNBytes(4096) }
    }
}
