package io.github.asagnc.aurora.linux

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinuxAgentEnvironmentTest {
    @Test fun lazyPinSurvivesSelectionChangeAndCancelledCloseReleasesIt() = runBlocking {
        fixture { registry, store ->
            val owner = store.create("pinned task")
            val first = publish(registry, "a")
            val environment = LinuxAgentEnvironment(registry, Mutex(), owner)
            assertEquals(first, environment.generation())
            publish(registry, "b")
            assertEquals(first, environment.generation())
            assertTrue(registry.unused().isEmpty())
            // close() is also safe when entered from a cancelled finally block.
            val cancelled = launch { try { awaitCancellation() } finally { environment.close() } }
            yield(); cancelled.cancelAndJoin()
            assertEquals(listOf(first), registry.unused())
            assertTrue(runCatching { environment.generation() }.isFailure)
            environment.close()
        }
    }

    @Test fun startupDropsProcessPinButPreservesUnconfirmedNativeRun() = runBlocking {
        fixture { registry, store ->
            val generation = publish(registry, "a")
            val owner = store.create("native task")
            registry.lease(owner, generation)
            val runs = LinuxRunRegistry(store)
            val ticket = LinuxRunTicket(UUID.randomUUID().toString(), owner, generation,
                LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                10000, listOf("/bin/sh"))
            runs.begin(ticket)
            assertEquals(1, registry.recoverProcessPins())
            assertEquals(listOf(ticket), runs.pending())
            assertEquals(0, registry.recoverProcessPins())
            assertTrue(runCatching { registry.release(ticket.runId) }.isFailure)
        }
    }

    @Test fun unusedEnvironmentDoesNotPinAndOrdinaryGenerationIsRejected() = runBlocking {
        fixture { registry, store ->
            val owner = store.create("lazy task")
            val environment = LinuxAgentEnvironment(registry, Mutex(), owner)
            val first = publish(registry, "a")
            publish(registry, "b", LinuxBackend.PROOT)
            assertEquals(listOf(first), registry.unused())
            assertTrue(runCatching { environment.generation() }.isFailure)
            environment.close()
        }
    }

    private suspend fun publish(registry: LinuxRegistry, hash: String, backend: LinuxBackend = LinuxBackend.CHROOT): String {
        val image = UbuntuBaseImages.current(LinuxArchitecture.ARM64).copy(sha256 = hash.repeat(64))
        val manifest = LinuxRootfsManifest(image, 1, 1, backend)
        val version = LinuxVersion(manifest.generationId(), manifest, 1)
        registry.begin(version.id, version.id); registry.publish(version.id, version)
        return version.id
    }
    private suspend fun fixture(run: suspend (LinuxRegistry, ConversationStore) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "agent-linux-pin-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try { val store = ConversationStore(database); run(LinuxRegistry(store), store) }
        finally { database.close(); context.deleteDatabase(name) }
    }
}
