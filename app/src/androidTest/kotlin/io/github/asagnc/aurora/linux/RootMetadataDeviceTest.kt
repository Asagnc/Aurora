package io.github.asagnc.aurora.linux

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.LinuxRunRegistry
import io.github.asagnc.aurora.root.*
import io.github.asagnc.aurora.runtime.CommandRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit fixture IDs only; never sweeps unrelated historical runtime records. */
@RunWith(AndroidJUnit4::class)
class RootMetadataDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication

    @RootRequired @LinuxImageRequired @Test fun currentEpochRecordIsRetained() = runBlocking {
        fixture { owner, ticket ->
            assertEquals(0L, app.linux.metadata.collect(setOf(ticket.runId)).number("collected"))
            assertTrue(exists(owner, ticket))
        }
    }

    @RootRequired @LinuxImageRequired @Test fun stoppedOldEpochIsCollectedAndLateStartCannotRecreateIt() = runBlocking {
        fixture { owner, ticket ->
            restart(owner)
            assertEquals(1L, app.linux.metadata.collect(setOf(ticket.runId)).number("collected"))
            assertFalse(exists(owner, ticket))
            val reply = requireNotNull(app.root.linuxRunClient).invoke(ticket, recover = false)
            assertNotNull(reply["error"])
            assertFalse(exists(owner, ticket))
            val next = app.linux.runs.execute(owner, listOf("/bin/sh", "-c", "printf still-working"))
            assertEquals(0L, next["completion"]!!.jsonObject.number("exitCode"))
        }
    }

    @RootRequired @LinuxImageRequired @Test fun pendingIntentIsPreservedUntilExplicitStoppedProof() = runBlocking {
        fixture { owner, ticket ->
            LinuxRunRegistry(app.conversations).begin(ticket)
            restart(owner)
            assertEquals(0L, app.linux.metadata.collect(setOf(ticket.runId)).number("collected"))
            assertTrue(exists(owner, ticket))
            assertEquals(1, app.linux.runs.pendingCount(owner))
            assertEquals(0L, app.linux.runs.recover(owner).number("retained"))
            assertEquals(1L, app.linux.metadata.collect(setOf(ticket.runId)).number("collected"))
        }
    }

    @RootRequired @LinuxImageRequired @Test fun quarantinedPartialDeletionIsRevisitedByInventory() = runBlocking {
        fixture { owner, ticket ->
            restart(owner)
            val resources = app.filesDir.toPath().toRealPath().resolve("spaces/resources")
            val source = resources.resolve("linux-runtime/${ticket.runId}")
            val parent = resources.resolve("linux-runtime-gc")
            val partial = parent.resolve(ticket.runId)
            require(source.startsWith(resources) && partial.startsWith(resources))
            for (argv in listOf(listOf("/system/bin/mkdir", "-p", parent.toString()),
                listOf("/system/bin/chmod", "700", parent.toString()),
                listOf("/system/bin/mv", source.toString(), partial.toString()),
                listOf("/system/bin/rm", partial.resolve("ticket.json").toString()))) {
                val response = requireNotNull(app.root.commandClient).execute(owner, CommandRequest(argv))
                assertEquals(response, 0L, WireJson.parseToJsonElement(response).jsonObject.number("exitCode"))
            }
            assertEquals(1L, app.linux.metadata.collect(setOf(ticket.runId)).number("collected"))
            val check = requireNotNull(app.root.commandClient).execute(owner,
                CommandRequest(listOf("/system/bin/test", "-d", partial.toString())))
            assertEquals(1L, WireJson.parseToJsonElement(check).jsonObject.number("exitCode"))
        }
    }

    private suspend fun restart(owner: String) {
        check(InstrumentationRegistry.getArguments().getString("auroraRootMetadataDeathTest") == "true")
        val old = requireNotNull(app.root.linuxClient).identity
        check(app.conversations.header(owner) != null)
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("stream", "AURORA_METADATA_ROOT_DEATH_READY=${old.pid}:${old.startTicks}\n")
        })
        withTimeout(20_000) { app.root.state.first { it.phase == RootPhase.UNAVAILABLE } }
        app.root.connect()
        check(app.root.metadataClient != null)
        assertNotEquals(old, requireNotNull(app.root.linuxClient).identity)
    }
    private suspend fun exists(owner: String, ticket: LinuxRunTicket): Boolean {
        val path = app.filesDir.toPath().toRealPath().resolve("spaces/resources/linux-runtime/${ticket.runId}/ticket.json")
        val reply = requireNotNull(app.root.commandClient).execute(owner, CommandRequest(listOf("/system/bin/test", "-f", path.toString())))
        return WireJson.parseToJsonElement(reply).jsonObject.number("exitCode") == 0L
    }

    private suspend fun fixture(run: suspend (String, LinuxRunTicket) -> Unit) {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("auroraRootTest") == "true" && args.getString("auroraLinuxImageTest") == "true")
        app.ready.await(); app.root.connect()
        check(app.root.metadataClient != null)
        check(app.linux.runs.pendingCount() == 0) { "Root-service restart requires no pending Linux tasks" }
        val owner = app.conversations.create("metadata GC qualification")
        var failure: Throwable? = null
        try {
            val argv = listOf("/bin/sh", "-c", "printf metadata-fixture")
            val identity = requireNotNull(app.root.linuxClient).identity
            val result = app.linux.runs.execute(owner, argv)
            val ticket = LinuxRunTicket(requireNotNull(result.text("runId")), owner, requireNotNull(result.text("generation")),
                identity, android.os.Process.myUid(), argv)
            run(owner, ticket)
        } catch (caught: Throwable) { failure = caught; throw caught
        } finally { withContext(NonCancellable) { try {
            app.spaces.delete(owner, app.tasks::quiesce)
        } catch (cleanup: Throwable) { if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
        } finally { app.root.disconnect() } } }
    }
}
