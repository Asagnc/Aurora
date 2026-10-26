package io.github.asagnc.aurora.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.skills.SkillTrustManager
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillTrustRecoveryTest {
    @Test fun policyRevalidationAndRecoveryOnlyTouchTheirOwnSecrets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "skill-trust-recovery-${UUID.randomUUID()}.db"
        val unrelated = UUID.randomUUID().toString(); val orphan = UUID.randomUUID().toString()
        val vault = CredentialVault(context)
        var retained: String? = null
        try {
            AuroraDatabase(context, name).use { database ->
                val registry = SkillSourceRegistry(ConversationStore(database)); val catalog = Mutex()
                val manager = SkillTrustManager(registry, vault, catalog)
                val profile = SkillSourceProfile(UUID.randomUUID().toString(), "example/skills")
                manager.save(profile, "owned-source-token")
                val captured = manager.list().single(); retained = captured.credentialId
                val source = SkillSource("https://github.com/example/skills")
                assertEquals(captured, manager.select(source, null))
                catalog.withLock { manager.requireCurrent(captured, source) }
                vault.put(unrelated, "owned-unrelated-fixture")
                registry.ownCredential(orphan); vault.put(orphan, "owned-orphan-fixture")
                manager.recover()
                assertNull(vault.get(orphan)); assertNotNull(vault.get(unrelated)); assertNotNull(vault.get(retained!!))
                manager.revoke(captured.id)
                assertNull(vault.get(retained!!)); assertTrue(manager.list().single().revoked)
                try { manager.select(source, null); fail("Revoked source selected") } catch (_: IllegalArgumentException) { }
                try { catalog.withLock { manager.requireCurrent(captured, source) }; fail("Stale policy accepted") } catch (_: IllegalArgumentException) { }
                try { catalog.withLock { manager.requireCurrent(null, source) }; fail("New policy bypassed") } catch (_: IllegalArgumentException) { }
            }
        } finally {
            retained?.let { vault.remove(it) }; vault.remove(unrelated); vault.remove(orphan); context.deleteDatabase(name)
        }
    }
}
