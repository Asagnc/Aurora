package io.github.asagnc.aurora.core

import java.nio.file.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SkillPublicationCleanupTest {
    @Test fun cleanupFailureDoesNotReplaceOriginalCommitErrorAndJournalCanRetry() = runBlocking {
        val root = Files.createTempDirectory("aurora-skill-cleanup")
        try {
            val store = SkillContentStore(root.resolve("store")).also { it.initialize() }
            val extracted = Files.createDirectory(root.resolve("extracted"))
            val skill = Files.createDirectory(extracted.resolve("example"))
            Files.writeString(skill.resolve("SKILL.md"), "---\nname: example\ndescription: Read files\n---\nbody")
            val prepared = store.prepare(extracted, skill, Files.createDirectory(root.resolve("stage")), emptySet())
            var record: List<String>? = null
            var brokenIndex = true
            val journal = object : SkillPublicationJournal {
                override suspend fun begin(id: String, hashes: List<String>) { record = hashes }
                override suspend fun pending() = record?.let { listOf("install" to it) }.orEmpty()
                override suspend fun known(hash: String): Boolean {
                    if (brokenIndex) throw java.io.IOException("recovery read failed")
                    return false
                }
                override suspend fun clearOperation(id: String) { record = null }
            }
            val publication = SkillPublication(store, journal)
            val error = assertFailsWith<IllegalStateException> {
                publication.publish("install", listOf(prepared)) { error("commit failed") }
            }
            assertEquals("commit failed", error.message)
            // Coroutine stack-trace recovery can wrap the original throwable in its cause chain.
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { cause ->
                cause.suppressed.any { it.message == "recovery read failed" }
            })
            assertNotNull(record); assertTrue(Files.exists(store.directory(prepared.manifest.digest)))
            brokenIndex = false; publication.recover()
            assertNull(record); assertFalse(Files.exists(store.directory(prepared.manifest.digest)))
        } finally { removeSkillTree(root) }
    }
}
