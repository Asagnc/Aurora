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
class SkillSourceRegistryTest {
    @Test fun reverifiedIdenticalContentKeepsCurrentEvidenceWhenSelectingThatVersion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val name = "skill-same-hash-${UUID.randomUUID()}.db"
        try {
            AuroraDatabase(context, name).use { helper ->
                val registry = SkillRegistry(ConversationStore(helper))
                val original = SkillEntry("owned", "a".repeat(64), true, SkillSource("https://github.com/example/skills"), SkillMetadata("owned", "description"), 7)
                registry.begin("first", listOf(original.hash)); registry.finish("first", listOf(original))
                val proof = SkillVerification("github:example/skills", "b".repeat(64), 12, "c".repeat(64), "owned inert persistence fixture")
                val reverified = original.copy(source = original.source.copy(profileId = UUID.randomUUID().toString(), verification = proof))
                registry.begin("second", listOf(original.hash)); registry.finish("second", listOf(reverified))
                assertEquals(proof, registry.versions(original.id).single().source.verification)
                registry.select(original.id, original.hash, reverified.source)
                assertEquals(proof, registry.entries().single().source.verification)
                assertNull(original.source.verification)
            }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun platformEd25519AndExistingKeyStoreVaultAreQualifiedIndependently() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString(); val vault = CredentialVault(context)
        val directory = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "skill-signature-")
        try {
            val revision = vault.put(id, "owned-device-fixture-token")
            assertEquals(revision, vault.get(id)!!.revision); assertEquals("owned-device-fixture-token", vault.get(id)!!.token)
            val public = "302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
            val encoded = java.util.Base64.getEncoder().encodeToString(public.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            val profile = SkillSourceProfile(id, "example/skills", publicKey = encoded, signatureUrl = "https://example.com/owned.sig")
            val archive = directory.resolve("owned"); java.nio.file.Files.write(archive, "owned skill archive bytes\n".toByteArray())
            val proof = SkillSignatures.verify(archive, profile, "aurora-skill-ed25519-v1\nwDk1GqajP3hQJLkOrjdoIXCZMoj1/PELFtiF/oYsGQhFHXNxR6AF1EpruW+QGxbj/B3VUyyQ0iQI5mWb8iRHCQ==\n")
            assertEquals(26L, proof.archiveBytes)
        } finally { vault.remove(id); removeSkillTree(directory) }
    }

    @Test fun versionNineMigrationPreservesSkillsPinsMessagesAndPendingCapture() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "skill-source-migration-${UUID.randomUUID()}.db"
        val original = AuroraDatabase(context, name)
        try {
            val store = ConversationStore(original); val owner = store.create("owned migration")
            store.appendMessage(owner, ChatMessage(role = "user", text = "keep"))
            val skill = SkillEntry("owned", "a".repeat(64), true, SkillSource("https://example.com/skill.zip"), SkillMetadata("owned", "description"), 7)
            val skills = SkillRegistry(store); skills.begin("install", listOf(skill.hash)); skills.finish("install", listOf(skill)); skills.pin(owner, listOf(skill))
            original.writableDatabase.apply {
                execSQL("INSERT INTO root_managed_runs(id,owner,kind,ticket) VALUES (?,?,?,?)", arrayOf("capture", owner, "capture", "opaque retained ticket"))
                execSQL("DROP TABLE skill_sources"); execSQL("DROP TABLE skill_source_secrets"); version = 9
            }
            original.close()
            AuroraDatabase(context, name).use { upgraded ->
                assertEquals(10, upgraded.readableDatabase.version)
                val next = ConversationStore(upgraded)
                assertEquals("keep", next.messages(owner).single().message.text)
                assertEquals(listOf(skill), SkillRegistry(next).entries())
                assertTrue(SkillRegistry(next).garbage().isEmpty())
                assertTrue(SkillSourceRegistry(next).profiles().isEmpty())
                assertEquals("opaque retained ticket", next.read { db -> db.rawQuery("SELECT ticket FROM root_managed_runs WHERE id='capture'", null).use { assertTrue(it.moveToFirst()); it.getString(0) } })
            }
        } finally { original.close(); context.deleteDatabase(name) }
    }

    @Test fun onlyRecordedUnreferencedSkillSecretsAreRecoveryCandidatesAndRepositoryIsUnique() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "skill-source-ownership-${UUID.randomUUID()}.db"
        try {
            AuroraDatabase(context, name).use { helper ->
                val registry = SkillSourceRegistry(ConversationStore(helper))
                val retained = UUID.randomUUID().toString(); val orphan = UUID.randomUUID().toString()
                registry.ownCredential(retained); registry.ownCredential(orphan)
                val profile = SkillSourceProfile(UUID.randomUUID().toString(), "example/skills", retained)
                registry.put(profile)
                assertEquals(listOf(orphan), registry.orphanCredentials())
                try { registry.put(profile.copy(id = UUID.randomUUID().toString())); fail("Duplicate repository accepted") }
                catch (_: android.database.sqlite.SQLiteConstraintException) { }
                assertEquals(listOf(profile), registry.profiles())
                registry.put(profile.copy(credentialId = null, revoked = true))
                assertEquals(setOf(retained, orphan), registry.orphanCredentials().toSet())
            }
        } finally { context.deleteDatabase(name) }
    }
}
