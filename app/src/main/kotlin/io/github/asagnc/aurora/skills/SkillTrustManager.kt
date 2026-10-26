package io.github.asagnc.aurora.skills

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SkillSourceRegistry
import io.github.asagnc.aurora.data.CredentialVault
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** User-only configuration writes share the install publication lock, including policy revalidation. */
internal class SkillTrustManager(private val registry: SkillSourceRegistry, private val credentials: CredentialVault, private val catalog: Mutex) {
    constructor(app: AuroraApplication, catalog: Mutex) : this(SkillSourceRegistry(app.conversations), app.credentials, catalog)
    suspend fun list(): List<SkillSourceProfile> = catalog.withLock { registry.profiles() }

    suspend fun save(profile: SkillSourceProfile, token: String?, removeCredential: Boolean = false) = catalog.withLock {
        profile.validate()
        require(!removeCredential || token.isNullOrBlank()) { "Choose credential replacement or removal" }
        val previous = registry.profiles().firstOrNull { it.id == profile.id }
        require(previous == null || previous.repository == profile.repository) { "Create a new profile for another repository" }
        require(profile.credentialId == null || profile.credentialId == previous?.credentialId)
        token?.takeIf { it.isNotBlank() }?.let { SkillGithubCredential(profile.repository, it) }
        val newId = token?.takeIf { it.isNotBlank() }?.let { UUID.randomUUID().toString() }
        withContext(NonCancellable) {
            if (newId != null) { registry.ownCredential(newId); credentials.put(newId, requireNotNull(token)) }
            registry.put(profile.copy(credentialId = newId ?: previous?.credentialId?.takeUnless { removeCredential }))
            recoverLocked()
        }
    }

    suspend fun revoke(id: String) = catalog.withLock {
        val profile = requireNotNull(registry.profiles().firstOrNull { it.id == id })
        withContext(NonCancellable) { registry.put(profile.copy(credentialId = null, revoked = true)); recoverLocked() }
    }

    suspend fun recover() = catalog.withLock { recoverLocked() }
    private suspend fun recoverLocked() {
        for (id in registry.orphanCredentials()) { credentials.remove(id); registry.forgetCredential(id) }
    }

    suspend fun select(source: SkillSource, explicit: String?): SkillSourceProfile? = catalog.withLock {
        val profiles = registry.profiles()
        val id = explicit ?: source.profileId
        val profile = if (id != null) requireNotNull(profiles.firstOrNull { it.id == id }) { "Source policy unavailable" }
            else profiles.firstOrNull { runCatching { it.matches(source) }.getOrDefault(false) }
        if (profile != null) {
            require(!profile.revoked) { "Source policy revoked" }
            require(!source.input.startsWith("https://") || profile.matches(source)) { "Source policy repository mismatch" }
        }
        profile
    }

    suspend fun credential(profile: SkillSourceProfile?): SkillGithubCredential? {
        val id = profile?.credentialId ?: return null
        val value = requireNotNull(credentials.get(id)) { "Source credential unavailable" }
        return SkillGithubCredential(profile.repository, value.token)
    }

    /** Called while the same catalog lock is held immediately before publication. */
    suspend fun requireCurrent(profile: SkillSourceProfile?, source: SkillSource) {
        if (profile != null) require(registry.profiles().firstOrNull { it.id == profile.id } == profile && !profile.revoked) {
            "Source policy changed during installation"
        }
        else require(registry.profiles().none { runCatching { it.matches(source) }.getOrDefault(false) }) {
            "Source policy added during installation"
        }
    }

    suspend fun permitted(source: SkillSource): Boolean = skillSourcePermitted(source, registry.profiles())
    suspend fun currentPolicies(): List<SkillSourceProfile> = registry.profiles()
}
