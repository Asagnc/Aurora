package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillRegistryTest {
    @Test fun versionThreeMigrationKeepsMessagesAndCreatesSkillTables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "skill-migration-${UUID.randomUUID()}.db"
        val original = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(original)
            val owner = store.create("preserved")
            store.appendMessage(owner, ChatMessage(role = "user", text = "keep"))
            original.writableDatabase.apply {
                listOf("skill_pins", "skill_releases", "skills", "skill_versions", "skill_operations").forEach { execSQL("DROP TABLE $it") }
                version = 3
            }
            original.close()
            val migrated = AuroraDatabase(context, name)
            try {
                val next = ConversationStore(migrated)
                assertEquals("keep", next.messages(owner).single().message.text)
                assertTrue(SkillRegistry(next).entries().isEmpty())
                assertEquals(5, migrated.readableDatabase.version)
            } finally { migrated.close() }
        } finally { original.close(); context.deleteDatabase(name) }
    }

    @Test fun updateRetainsPinnedVersionAndDeletionReleasesOnlyConversationReference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "skill-pins-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val conversations = ConversationStore(database)
            val registry = SkillRegistry(conversations)
            val owner = conversations.create("owns reference")
            val first = SkillEntry("workflow", "a".repeat(64), true, SkillSource("https://example.com/skill.zip"), SkillMetadata("example", "Read files"), 10)
            registry.begin("install-one", listOf(first.hash)); registry.finish("install-one", listOf(first))
            registry.pin(owner, listOf(first))
            val second = first.copy(hash = "b".repeat(64), bytes = 20)
            registry.begin("install-two", listOf(second.hash)); registry.finish("install-two", listOf(second))
            assertEquals(second.hash, registry.entries().single().hash)
            assertEquals(2, registry.versions(first.id).size)
            assertTrue(registry.garbage().isEmpty())
            assertEquals(listOf(owner), registry.remove(first.id))
            conversations.write { it.delete("conversations", "id=?", arrayOf(owner)) }
            assertEquals(first.hash, registry.garbage().single().first)
            assertEquals(second.hash, registry.entries().single().hash)
            registry.remove(first.id)
            assertEquals(2, registry.garbage().size)
            registry.reserveRemoval("cleanup", first.hash)
            assertFalse(registry.known(first.hash))
            assertEquals(listOf(first.hash), registry.pending().single().second)
            registry.clearOperation("cleanup")
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
