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
class LinuxRegistryTest {
    @Test fun rootLeaseProtectsItsGenerationIndependentlyOfOrdinaryBackend() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "root-leases-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(database); val registry = LinuxRegistry(store)
            val owner = store.create("root task")
            val image = UbuntuBaseImages.current(LinuxArchitecture.ARM64)
            val ordinary = LinuxRootfsManifest(image, 1, 1)
            val privileged = LinuxRootfsManifest(image, 1, 1, LinuxBackend.CHROOT)
            for (manifest in listOf(ordinary, privileged)) {
                val version = LinuxVersion(manifest.generationId(), manifest, 1)
                registry.begin(version.id, version.id); registry.publish(version.id, version)
            }
            val lease = registry.lease(owner, privileged.generationId())
            registry.select(ordinary.generationId())
            assertTrue(registry.unused().isEmpty())
            try { registry.reserveRemoval("blocked-root", privileged.generationId()); fail("Root lease ignored") }
            catch (_: IllegalArgumentException) {}
            registry.release(lease)
            assertEquals(listOf(privileged.generationId()), registry.unused())
            registry.reserveRemoval("root-removal", privileged.generationId())
            assertEquals(listOf("root-removal" to privileged.generationId()), registry.pending())
            assertEquals(ordinary.generationId(), registry.current())
        } finally { database.close(); context.deleteDatabase(name) }
    }
    @Test fun versionFourMigrationPreservesConversationsAndSkills() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linux-migration-${UUID.randomUUID()}.db"
        val old = AuroraDatabase(context, name)
        try {
            val conversations = ConversationStore(old)
            val owner = conversations.create("keep history")
            conversations.appendMessage(owner, ChatMessage(role = "user", text = "saved"))
            val skills = SkillRegistry(conversations)
            val skill = SkillEntry("skill", "a".repeat(64), true, SkillSource("https://example.org/skill.zip"), SkillMetadata("example", "Read files"), 1)
            skills.begin("skill", listOf(skill.hash)); skills.finish("skill", listOf(skill))
            old.writableDatabase.apply {
                listOf("linux_leases", "linux_settings", "linux_versions", "linux_operations").forEach { execSQL("DROP TABLE $it") }
                version = 4
            }
            old.close()
            val upgraded = AuroraDatabase(context, name)
            try {
                val store = ConversationStore(upgraded)
                assertEquals("saved", store.messages(owner).single().message.text)
                assertEquals(skill.hash, SkillRegistry(store).entries().single().hash)
                assertTrue(LinuxRegistry(store).versions().isEmpty())
                assertEquals(5, upgraded.readableDatabase.version)
            } finally { upgraded.close() }
        } finally { old.close(); context.deleteDatabase(name) }
    }
    @Test fun activeLeaseProtectsOldVersionAndRemovalHasRecoveryRecord() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linux-leases-${UUID.randomUUID()}.db"
        val database = AuroraDatabase(context, name)
        try {
            val conversations = ConversationStore(database); val registry = LinuxRegistry(conversations)
            val owner = conversations.create("run")
            val image = UbuntuBaseImages.current(LinuxArchitecture.ARM64)
            val first = LinuxVersion(image.sha256, LinuxRootfsManifest(image, 1, 10), 1)
            registry.begin("first", first.id); registry.publish("first", first)
            val lease = registry.lease(owner, first.id)
            val next = first.copy(id = "b".repeat(64), manifest = first.manifest.copy(image = image.copy(sha256 = "b".repeat(64))), installedAt = 2)
            registry.begin("next", next.id); registry.publish("next", next)
            assertTrue(registry.unused().isEmpty())
            try { registry.reserveRemoval("blocked", first.id); fail("Active lease removed") } catch (_: IllegalArgumentException) { }
            registry.release(lease)
            assertEquals(listOf(first.id), registry.unused())
            registry.reserveRemoval("cleanup", first.id)
            assertEquals(listOf("cleanup" to first.id), registry.pending())
            assertEquals(next.id, registry.current())
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
