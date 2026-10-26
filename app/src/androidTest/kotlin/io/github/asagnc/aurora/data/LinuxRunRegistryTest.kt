package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinuxRunRegistryTest {
    private suspend fun fixture(block: suspend (ConversationStore, LinuxRunRegistry, LinuxRunTicket) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linux-run-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(database)
            val owner = store.create("persistent run")
            val manifest = LinuxRootfsManifest(UbuntuBaseImages.current(LinuxArchitecture.ARM64), 1, 1, LinuxBackend.CHROOT)
            val version = LinuxVersion(manifest.generationId(), manifest, 1)
            LinuxRegistry(store).apply { begin("publish", version.id); publish("publish", version) }
            val ticket = LinuxRunTicket(UUID.randomUUID().toString(), owner, version.id,
                LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString()),
                context.applicationInfo.uid, listOf("/bin/sh", "-c", "exit 23"))
            block(store, LinuxRunRegistry(store), ticket)
        } finally { database.close(); context.deleteDatabase(name) }
    }
    private fun confirmation(ticket: LinuxRunTicket) = objectOf("protocol" to JsonPrimitive(1),
        "runId" to string(ticket.runId), "generation" to string(ticket.generation), "stopped" to JsonPrimitive(true))
    @Test fun unconfirmedOrMismatchedExitRetainsTaskAndLease() = runBlocking { fixture { store, runs, ticket ->
        runs.begin(ticket)
        for (response in listOf(objectOf("exitCode" to JsonPrimitive(0)), confirmation(ticket.copy(runId = UUID.randomUUID().toString())))) {
            try { runs.finish(ticket, response); fail("Unconfirmed lease released") } catch (_: IllegalArgumentException) { }
            assertEquals(listOf(ticket), runs.pending())
        }
        try { LinuxRegistry(store).release(ticket.runId); fail("Run record lost its lease") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        coroutineScope {
            List(2) { async(Dispatchers.IO) { runs.finish(ticket, confirmation(ticket)) } }.awaitAll()
        }
        assertTrue(runs.pending().isEmpty())
        assertEquals(0L, store.read { db -> android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM linux_leases", null) })
    } }
    @Test fun persistedToolBundleReferenceRequiresMatchingExitProof() = runBlocking { fixture { store, runs, original ->
        val ticket = original.copy(toolBundle = "a".repeat(64))
        runs.begin(ticket)
        val reopened = LinuxRunRegistry(store)
        assertTrue(reopened.usesToolBundle(requireNotNull(ticket.toolBundle)))
        assertFalse(reopened.usesToolBundle("b".repeat(64)))
        try { reopened.finish(ticket, confirmation(ticket)); fail("Missing bundle proof released lease") }
        catch (_: IllegalArgumentException) { }
        assertTrue(reopened.usesToolBundle(requireNotNull(ticket.toolBundle)))
        reopened.finish(ticket, JsonObject(confirmation(ticket) + ("toolBundle" to string(requireNotNull(ticket.toolBundle)))))
        assertFalse(reopened.usesToolBundle(requireNotNull(ticket.toolBundle)))
    } }

    @Test fun duplicateOwnerRollsBackSecondLeaseAtomically() = runBlocking { fixture { store, runs, ticket ->
        runs.begin(ticket)
        try { runs.begin(ticket.copy(runId = UUID.randomUUID().toString())); fail("Concurrent workspace run admitted") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(listOf(ticket), runs.pending())
        assertEquals(1L, store.read { db -> android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM linux_leases", null) })
    } }
    @Test fun ownerFilteredRecoverySelectionDoesNotIncludeAnotherConversation() = runBlocking { fixture { store, runs, ticket ->
        val other = store.create("another active conversation")
        val another = ticket.copy(runId = UUID.randomUUID().toString(), owner = other)
        runs.begin(ticket); runs.begin(another)
        assertEquals(setOf(ticket.runId, another.runId), runs.pendingIds(listOf(ticket.runId, another.runId, "unknown")))
        assertTrue(runs.pendingIds(emptyList()).isEmpty())
        assertEquals(2L, runs.count())
        assertEquals(listOf(ticket), runs.pending(ticket.owner))
        assertEquals(listOf(another), runs.pending(other))
        assertEquals(1L, runs.count(ticket.owner))
        assertEquals(0L, runs.count("unknown"))
        assertTrue(runs.pending("unknown").isEmpty())
        runs.finish(ticket, confirmation(ticket))
        assertEquals(listOf(another), runs.pending())
        assertEquals(setOf(another.runId), runs.pendingIds(listOf(ticket.runId, another.runId)))
        assertEquals(1L, runs.count(other))
    } }
    @Test fun deletingConversationCannotAcquireRunOrLease() = runBlocking { fixture { store, runs, ticket ->
        store.write { it.update("conversations", values("deleting" to 1), "id=?", arrayOf(ticket.owner)) }
        try { runs.begin(ticket); fail("Deleting conversation admitted") } catch (_: IllegalArgumentException) { }
        assertTrue(runs.pending().isEmpty())
        assertEquals(0L, store.read { db -> android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM linux_leases", null) })
    } }
    @Test fun versionFiveMigrationPreservesHistoryAndExistingLease() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linux-run-migration-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(database)
            val owner = store.create("keep")
            store.appendMessage(owner, ChatMessage(role = "user", text = "preserved"))
            val manifest = LinuxRootfsManifest(UbuntuBaseImages.current(LinuxArchitecture.ARM64), 1, 1)
            val version = LinuxVersion(manifest.generationId(), manifest, 1)
            val registry = LinuxRegistry(store)
            registry.begin("publish", version.id); registry.publish("publish", version)
            val lease = registry.lease(owner, version.id)
            database.writableDatabase.apply { execSQL("DROP TABLE linux_runs"); this.version = 5 }
            database.close()
            val upgraded = AuroraDatabase(context, name)
            try {
                val next = ConversationStore(upgraded)
                assertEquals("preserved", next.messages(owner).single().message.text)
                assertTrue(LinuxRunRegistry(next).pending().isEmpty())
                assertEquals(1L, next.read { db -> android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM linux_leases WHERE token=?", arrayOf(lease)) })
                assertEquals(10, upgraded.readableDatabase.version)
            } finally { upgraded.close() }
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
