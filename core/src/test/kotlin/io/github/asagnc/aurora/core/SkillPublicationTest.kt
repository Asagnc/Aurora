package io.github.asagnc.aurora.core

import java.nio.file.*
import kotlinx.coroutines.*
import kotlin.test.*

class SkillPublicationTest {
    private class Journal : SkillPublicationJournal {
        val records = linkedMapOf<String, List<String>>()
        val indexed = mutableSetOf<String>()
        var failReads = false
        override suspend fun begin(id: String, hashes: List<String>) { check(id !in records); records[id] = hashes }
        override suspend fun pending() = records.map { it.key to it.value }
        override suspend fun known(hash: String): Boolean {
            if (failReads) throw java.io.IOException("index unavailable")
            return hash in indexed
        }
        override suspend fun clearOperation(id: String) { records.remove(id) }
        fun commit(id: String, packages: List<PreparedSkill>) {
            indexed += packages.map { it.manifest.digest }; records.remove(id)
        }
    }
    private class Fixture(val root: Path) {
        val store = SkillContentStore(root.resolve("store")).also { it.initialize() }
        val journal = Journal()
        val publication = SkillPublication(store, journal)
        fun prepare(name: String): PreparedSkill {
            val extracted = Files.createDirectory(root.resolve("input-$name"))
            val skill = Files.createDirectory(extracted.resolve(name))
            Files.writeString(skill.resolve("SKILL.md"), "---\nname: $name\ndescription: Read files\n---\nInstructions.")
            return store.prepare(extracted, skill, Files.createDirectory(root.resolve("stage-$name")), emptySet())
        }
    }
    private fun fixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val root = Files.createTempDirectory("aurora-skill-publication")
        try { Fixture(root).block() } finally { removeSkillTree(root) }
    }

    @Test fun failedIndexCommitRemovesUnindexedPackagesAndPreservesExistingVersion() = fixture {
        val old = prepare("old"); val fresh = prepare("fresh")
        publication.publish("old", listOf(old)) { journal.commit("old", listOf(old)) }
        assertFailsWith<java.io.IOException> {
            publication.publish("new", listOf(old, fresh)) { throw java.io.IOException("index unavailable") }
        }
        assertEquals(old.manifest, store.manifest(old.manifest.digest))
        assertFalse(Files.exists(store.directory(fresh.manifest.digest)))
        assertTrue(journal.records.isEmpty())
    }
    @Test fun recoveryRemovesMoveBeforeIndexCrashAndKeepsCommittedContent() = fixture {
        val old = prepare("old"); val orphan = prepare("orphan")
        publication.publish("old", listOf(old)) { journal.commit("old", listOf(old)) }
        journal.begin("crashed", listOf(old.manifest.digest, orphan.manifest.digest))
        store.publish(orphan)
        publication.recover(); publication.recover()
        assertEquals(old.manifest, store.manifest(old.manifest.digest))
        assertFalse(Files.exists(store.directory(orphan.manifest.digest)))
        assertTrue(journal.records.isEmpty())
    }
    @Test fun failedSecondMoveRollsBackFirstMoveWithoutTouchingCorruptOccupiedVersion() = fixture {
        val first = prepare("first"); val second = prepare("second")
        val collision = store.directory(second.manifest.digest)
        Files.createDirectory(collision); Files.writeString(collision.resolve("manifest.json"), "invalid")
        // An indexed occupied version must be retained for diagnosis rather than silently replaced.
        journal.indexed += second.manifest.digest
        var committed = false
        assertFails {
            publication.publish("batch", listOf(first, second)) { committed = true }
        }
        assertFalse(committed); assertFalse(Files.exists(store.directory(first.manifest.digest)))
        assertEquals("invalid", Files.readString(collision.resolve("manifest.json")))
        assertTrue(journal.records.isEmpty())
    }
    @Test fun recoveryFailureLeavesJournalForRetry() = fixture {
        val hash = "f".repeat(64)
        journal.begin("pending", listOf(hash))
        journal.failReads = true
        assertFails { publication.recover() }
        assertTrue("pending" in journal.records)
        journal.failReads = false
        publication.recover(); assertTrue(journal.records.isEmpty())
    }
    @Test fun cancellationBeforePublicationCreatesNoJournalOrVisiblePackage() = fixture { coroutineScope {
        val prepared = prepare("cancelled")
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            publication.publish("cancelled", listOf(prepared)) { error("Must not commit") }
        }
        task.cancelAndJoin()
        assertTrue(journal.records.isEmpty())
        assertFalse(Files.exists(store.directory(prepared.manifest.digest)))
    } }
    @Test fun cancellationDuringPublicationFinishesIndexAndKeepsPublishedContent() = fixture { coroutineScope {
        val prepared = prepare("atomic")
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val task = launch {
            publication.publish("atomic", listOf(prepared)) {
                entered.complete(Unit); release.await(); journal.commit("atomic", listOf(prepared))
            }
        }
        entered.await(); task.cancel(); release.complete(Unit); task.join()
        assertTrue(task.isCancelled)
        assertEquals(prepared.manifest, store.manifest(prepared.manifest.digest))
        assertTrue(journal.records.isEmpty()); assertTrue(prepared.manifest.digest in journal.indexed)
    } }
}
