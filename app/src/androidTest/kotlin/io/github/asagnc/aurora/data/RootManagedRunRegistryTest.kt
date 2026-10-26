package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootManagedRunRegistryTest {
    @Test fun capturePublicationFailureKeepsTheSameRecoverableIntentAfterResourceExit() = runBlocking { fixture { _, store, old ->
        val ticket = old.copy(kind = "capture", action = "capture", protocol = 3, target = null, observeMs = 1000,
            script = RootScriptSnapshot("capture-rotation-v1", "b".repeat(64), 227))
        val registry = RootManagedRunRegistry(store); registry.begin(ticket)
        val proof = RootManagedExitProof(ticket.runId, ticket.identity, ticket.digest(), false, false, false, true, true, 3)
        val result = RootManagedResult(ticket.runId, ticket.digest(), cleanup = proof, exitCode = 0, workerStatus = 0,
            artifactError = "capture_publication_unavailable")
        registry.finish(ticket, result)
        assertEquals(listOf(ticket), registry.pending(ticket.owner))
        registry.finish(ticket, result.copy(artifactError = null, captureArtifact = "runs/${ticket.runId}/segments/capture-manifest.json"))
        assertEquals(0L, registry.count(ticket.owner))
    } }

    @Test fun versionEightMigrationPreservesExactPendingFridaAndAddsCaptureWithoutAnotherTable() = runBlocking { fixture { db, store, ticket ->
        RootManagedRunRegistry(store).begin(ticket)
        val encoded = store.read { source -> source.rawQuery("SELECT ticket FROM root_managed_runs WHERE id=?", arrayOf(ticket.runId)).use {
            assertTrue(it.moveToFirst()); it.getString(0)
        } }
        db.writableDatabase.apply {
            beginTransaction()
            try {
                execSQL("ALTER TABLE root_managed_runs RENAME TO fixture_v9")
                execSQL("DROP INDEX root_managed_owner")
                execSQL("""CREATE TABLE root_managed_runs(id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES conversations(id),
                    kind TEXT NOT NULL CHECK(kind='frida'), ticket TEXT NOT NULL, tool_run_id TEXT)""")
                execSQL("INSERT INTO root_managed_runs SELECT * FROM fixture_v9")
                execSQL("DROP TABLE fixture_v9")
                execSQL("CREATE INDEX root_managed_owner ON root_managed_runs(owner,id)")
                version = 8; setTransactionSuccessful()
            } finally { endTransaction() }
        }
        db.close()
        AuroraDatabase(context, db.databaseName).use { upgraded ->
            assertEquals(10, upgraded.readableDatabase.version)
            val migrated = ConversationStore(upgraded); val registry = RootManagedRunRegistry(migrated)
            assertEquals(listOf(ticket), registry.pending(ticket.owner))
            assertEquals(encoded, migrated.read { source -> source.rawQuery("SELECT ticket FROM root_managed_runs WHERE id=?", arrayOf(ticket.runId)).use {
                assertTrue(it.moveToFirst()); it.getString(0)
            } })
            val capture = ticket.copy(runId = UUID.randomUUID().toString(), kind = "capture", action = "capture", protocol = 3,
                target = null, observeMs = 1000, script = RootScriptSnapshot("capture-rotation-v1", "c".repeat(64), 227))
            registry.begin(capture); assertEquals(2L, registry.count(ticket.owner))
        }
    } }

    @Test fun pendingRecoveryPagesRemainOwnerScopedAndBounded() = runBlocking { fixture { _, store, ticket ->
        val runs = RootManagedRunRegistry(store)
        val tickets = (1..7).map { ticket.copy(runId = "00000000-0000-0000-0000-%012d".format(it)) }
        tickets.forEach { runs.begin(it) }
        runs.begin(ticket.copy(owner = store.create("other")))
        assertEquals(tickets.take(4), runs.pending(ticket.owner, 4))
        assertEquals(tickets.drop(4), runs.pending(ticket.owner, 4, tickets[3].runId))
        assertEquals(7L, runs.count(ticket.owner))
        try { runs.pending(ticket.owner, 65); fail("Unbounded page accepted") }
        catch (_: IllegalArgumentException) { }
    } }
    @Test fun binderTransportWithoutProofRetainsIntentAndExactCleanupReleasesIt() = runBlocking { fixture { _, store, ticket ->
        val runs = RootManagedRunRegistry(store)
        runs.begin(ticket)
        val result = RootManagedResult(ticket.runId, ticket.digest(), exitCode = 0, workerStatus = 0)
        try { runs.finish(ticket, result); fail("Transport exit cleared injection intent") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1L, runs.count(ticket.owner))
        try { runs.finish(ticket, result.copy(cleanup = proof(ticket.copy(appBuild = "wrong"))))
            fail("Wrong ticket cleared injection intent") }
        catch (_: IllegalArgumentException) { }
        assertEquals(1L, runs.count(ticket.owner))
        runs.finish(ticket, result.copy(cleanup = proof(ticket), exitCode = 1, workerStatus = 1,
            error = "managed_execution_failed"))
        assertEquals(0L, runs.count(ticket.owner))
    } }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun ticket(owner: String) = RootManagedRunTicket(UUID.randomUUID().toString(), owner,
        LinuxRootIdentity(123, 456, UUID.randomUUID().toString(), UUID.randomUUID().toString()),
        context.applicationInfo.uid, "fixture", "a".repeat(64), RootTargetIdentity(789, 1000),
        RootScriptSnapshot("fixture", "b".repeat(64), 42))
    private fun proof(ticket: RootManagedRunTicket) = RootManagedExitProof(ticket.runId, ticket.identity,
        ticket.digest(), true, true, true, true, true)
    private fun retirement(ticket: RootManagedRunTicket): RootManagedRetirementProof {
        val stopped = ByteBuffer.allocate(123).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("ARI1\n${ticket.runId}\n${ticket.digest()}\n".toByteArray(Charsets.US_ASCII))
            put(8); put(2); put(2); put(0); putInt(-1); putInt(4); putInt(0)
        }.array()
        return requireNotNull(RootManagedNativeState.decode(ticket, null, null, stopped).neverDispatchedProof())
    }
    private fun targetExited(ticket: RootManagedRunTicket): RootManagedTargetExitProof {
        val ready = ByteBuffer.allocate(164).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("ARW1${ticket.runId}${ticket.digest()}".toByteArray(Charsets.US_ASCII))
            putInt(234); putLong(2340); putInt(235); putLong(2350); put(ticket.identity.bootId.toByteArray(Charsets.US_ASCII))
        }.array()
        val stopped = ByteBuffer.allocate(123).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("ARI1\n${ticket.runId}\n${ticket.digest()}\n".toByteArray(Charsets.US_ASCII))
            put(15); put(1); put(1); put(0); putInt(-1); putInt(4); putInt(0)
        }.array()
        return requireNotNull(RootManagedNativeState.decode(ticket, ready, null, stopped)
            .targetExitProof(ticket.identity.bootId, null))
    }
    private suspend fun fixture(block: suspend (AuroraDatabase, ConversationStore, RootManagedRunTicket) -> Unit) {
        val name = "managed-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try { val store = ConversationStore(database); block(database, store, ticket(store.create("managed"))) }
        finally { database.close(); context.deleteDatabase(name) }
    }
    @Test fun unknownInjectionSurvivesReopenAndBlocksDatabaseAndFileDeletion() = runBlocking { fixture { db, store, ticket ->
        RootManagedRunRegistry(store).begin(ticket)
        db.close()
        AuroraDatabase(context, db.databaseName).use { reopened ->
            val next = ConversationStore(reopened); val runs = RootManagedRunRegistry(next)
            assertEquals(listOf(ticket), runs.pending(ticket.owner))
            val spaces = SpaceStore(context, next)
            val files = spaces.commandRoot(ticket.owner)
            java.nio.file.Files.write(files.resolve("keep.txt"), "keep".toByteArray())
            try { spaces.delete(ticket.owner, {}); fail("Unconfirmed task files removed") }
            catch (_: RootManagedRunPendingException) { }
            assertTrue(java.nio.file.Files.exists(files.resolve("keep.txt")))
            try { next.write { it.delete("conversations", "id=?", arrayOf(ticket.owner)) }; fail("Intent cascaded") }
            catch (_: android.database.sqlite.SQLiteConstraintException) { }
            try { runs.finish(ticket, proof(ticket).copy(scriptUnloaded = false)); fail("Unknown injection forgotten") }
            catch (_: IllegalArgumentException) { }
            assertEquals(1L, runs.count(ticket.owner))
            runs.finish(ticket, proof(ticket)); runs.finish(ticket, proof(ticket))
            spaces.delete(ticket.owner, {})
            assertFalse(java.nio.file.Files.exists(files))
        }
    } }
    @Test fun retiredBeforeDispatchReleasesOnlyItsExactIntentAndAllowsDeletion() = runBlocking { fixture { _, store, ticket ->
        val runs = RootManagedRunRegistry(store)
        val second = ticket.copy(runId = UUID.randomUUID().toString())
        runs.begin(ticket); runs.begin(second)
        try { runs.finish(ticket, retirement(second)); fail("Foreign retirement cleared an intent") }
        catch (_: IllegalArgumentException) { }
        assertEquals(2L, runs.count(ticket.owner))
        runs.finish(ticket, retirement(ticket)); runs.finish(ticket, retirement(ticket))
        assertEquals(listOf(second), runs.pending(ticket.owner))
        runs.finish(second, retirement(second))
        SpaceStore(context, store).delete(ticket.owner, {})
        assertTrue(store.list().none { it.id == ticket.owner })
    } }
    @Test fun targetExitRecoveryClearsOnlyExactTicketDespiteMissingWorkerReport() = runBlocking { fixture { _, store, ticket ->
        val runs = RootManagedRunRegistry(store)
        val second = ticket.copy(runId = UUID.randomUUID().toString())
        runs.begin(ticket); runs.begin(second)
        try { runs.finish(ticket, targetExited(second)); fail("Foreign target exit cleared an intent") }
        catch (_: IllegalArgumentException) { }
        assertEquals(2L, runs.count(ticket.owner))
        runs.finish(ticket, targetExited(ticket)); runs.finish(ticket, targetExited(ticket))
        assertEquals(listOf(second), runs.pending(ticket.owner))
        runs.finish(second, targetExited(second))
        SpaceStore(context, store).delete(ticket.owner, {})
        assertNull(store.header(ticket.owner))
    } }
    @Test fun ownerScopedHistoryLinkAndIntentTransaction() = runBlocking { fixture { _, store, ticket ->
        val runs = RootManagedRunRegistry(store)
        val other = store.create("other"); val second = ticket.copy(runId = UUID.randomUUID().toString(), owner = other)
        val toolId = ToolRunStore(store).start(other, ToolCall("call", "fixture", objectOf()))
        try { runs.begin(ticket, toolId); fail("Cross owner tool reference accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0L, runs.count(ticket.owner))
        runs.begin(ticket); runs.begin(second, toolId)
        assertEquals(listOf(ticket), runs.pending(ticket.owner))
        assertEquals(listOf(second), runs.pending(other))
        store.write { it.delete("tool_runs", "id=?", arrayOf(toolId)) }
        assertEquals(1L, runs.count(other))
        runs.finish(ticket, proof(ticket))
        assertEquals(listOf(second), runs.pending(other))
        store.write { it.update("conversations", values("deleting" to 1), "id=?", arrayOf(ticket.owner)) }
        try { runs.begin(ticket); fail("Deleting owner admitted") } catch (_: IllegalArgumentException) { }
        assertEquals(0L, runs.count(ticket.owner))
        store.write { it.update("conversations", values("deleting" to 1), "id=?", arrayOf(other)) }
        assertEquals(1, SpaceStore(context, store).recoverDeletes())
        assertEquals(listOf(second), runs.pending(other))
    } }
    @Test fun duplicateOrCorruptIntentFailsClosed() = runBlocking { fixture { _, store, ticket ->
        val runs = RootManagedRunRegistry(store); runs.begin(ticket)
        try { runs.begin(ticket.copy(observeMs = 2)); fail("Existing intent overwritten") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(listOf(ticket), runs.pending(ticket.owner))
        val changed = ticket.copy(observeMs = 2)
        try { runs.finish(changed, proof(changed)); fail("Changed ticket cleared intent") }
        catch (_: IllegalArgumentException) { }
        store.write { it.update("root_managed_runs", values("kind" to "frida", "ticket" to "{}"), "id=?", arrayOf(ticket.runId)) }
        try { runs.pending(ticket.owner); fail("Corrupt record silently ignored") } catch (_: kotlinx.serialization.SerializationException) { }
        assertEquals(1L, runs.count(ticket.owner))
    } }
    @Test fun versionSevenMigrationPreservesLinuxRunAndLease() = runBlocking { fixture { db, store, ticket ->
        val manifest = LinuxRootfsManifest(UbuntuBaseImages.current(LinuxArchitecture.ARM64), 1, 1, LinuxBackend.CHROOT)
        val version = LinuxVersion(manifest.generationId(), manifest, 1)
        LinuxRegistry(store).apply { begin("publish", version.id); publish("publish", version) }
        val linux = LinuxRunTicket(UUID.randomUUID().toString(), ticket.owner, version.id, ticket.identity,
            context.applicationInfo.uid, listOf("/bin/true"))
        LinuxRunRegistry(store).begin(linux)
        db.writableDatabase.apply { execSQL("DROP TABLE root_managed_runs"); this.version = 7 }
        db.close()
        AuroraDatabase(context, db.databaseName).use { next ->
            val migrated = ConversationStore(next)
            assertEquals(listOf(linux), LinuxRunRegistry(migrated).pending(ticket.owner))
            assertEquals(1L, migrated.read { android.database.DatabaseUtils.longForQuery(it,
                "SELECT COUNT(*) FROM linux_leases WHERE token=?", arrayOf(linux.runId)) })
            RootManagedRunRegistry(migrated).begin(ticket)
            assertEquals(listOf(ticket), RootManagedRunRegistry(migrated).pending(ticket.owner))
        }
    } }
    @Test fun allHistoricalVersionsMigrateWithoutLosingHistory() = runBlocking {
        for (version in 1..7) {
            val name = "managed-migration-$version-${UUID.randomUUID()}.db"
            try {
                val owner = AuroraDatabase(context, name).use { original ->
                    val store = ConversationStore(original); val owner = store.create("preserved")
                    store.appendMessage(owner, ChatMessage(role = "user", text = "keep"))
                    original.writableDatabase.apply {
                        execSQL("DROP TABLE root_managed_runs")
                        execSQL("DROP INDEX browser_download_pending")
                        if (version < 2) execSQL("DROP TABLE agent_runs")
                        if (version < 3) {
                            execSQL("ALTER TABLE tool_runs DROP COLUMN artifact_directory")
                            execSQL("ALTER TABLE tool_runs DROP COLUMN presentation")
                        }
                        if (version < 6) listOf("linux_runs", "linux_leases", "linux_settings", "linux_versions", "linux_operations",
                            "skill_pins", "skill_releases", "skills", "skill_versions", "skill_operations").forEach { execSQL("DROP TABLE $it") }
                        this.version = version
                    }
                    owner
                }
                AuroraDatabase(context, name).use { upgraded ->
                    val store = ConversationStore(upgraded); val runs = RootManagedRunRegistry(store)
                    assertEquals(10, upgraded.readableDatabase.version)
                    assertEquals("keep", store.messages(owner).single().message.text)
                    val ticket = ticket(owner); runs.begin(ticket)
                    assertEquals(listOf(ticket), runs.pending(owner))
                    val plan = store.read { db -> db.rawQuery("EXPLAIN QUERY PLAN SELECT ticket FROM root_managed_runs WHERE owner=? ORDER BY id", arrayOf(owner)).use {
                        buildList { while (it.moveToNext()) add(it.getString(3)) }.joinToString(" ")
                    } }
                    assertTrue(plan, plan.contains("root_managed_owner"))
                }
            } finally { context.deleteDatabase(name) }
        }
    }
}
