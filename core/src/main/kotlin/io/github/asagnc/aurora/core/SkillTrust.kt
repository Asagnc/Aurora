package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

/** User-managed policy. The secret is in the existing vault; only its owned UUID is indexed here. */
@Serializable data class SkillSourceProfile(val id: String, val repository: String, val credentialId: String? = null,
    val publicKey: String = "", val signatureUrl: String = "", val revoked: Boolean = false) {
    val scope: String get() = "github:" + repository
    fun validate() {
        require(UUID.fromString(id).toString() == id)
        require(repository.matches(Regex("[a-z0-9_.-]+/[a-z0-9_.-]+")) && repository.length <= 200)
        credentialId?.let { require(UUID.fromString(it).toString() == it) }
        require(publicKey.isNotBlank() || signatureUrl.isBlank())
        if (publicKey.isNotBlank()) SkillSignatures.key(publicKey)
        if (signatureUrl.isNotBlank()) {
            val url = signatureUrl.toHttpUrl()
            require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null && url.query == null)
            require(signatureUrl.length <= 4096)
        }
    }
    fun matches(source: SkillSource): Boolean {
        val url = source.input.toHttpUrl()
        skillGithubLocation(url, source) // Validate repository/tree syntax; release/archive ZIPs keep their literal URL.
        val parts = url.pathSegments
        return url.host == "github.com" && parts.size >= 2 && url.isHttps && url.port == 443 && url.query == null &&
            url.username.isEmpty() && url.password.isEmpty() && url.fragment == null &&
            "${parts[0]}/${parts[1].removeSuffix(".git")}".lowercase(java.util.Locale.ROOT) == repository
    }
}

@Serializable data class SkillVerification(val scope: String, val archiveSha256: String, val archiveBytes: Long,
    val keySha256: String, val signature: String)

/** Existing running snapshots keep their version; these checks govern new acquisitions. */
fun skillSourcePermitted(source: SkillSource, profiles: List<SkillSourceProfile>): Boolean {
    val profile = source.profileId?.let { id -> profiles.firstOrNull { it.id == id } ?: return false }
        ?: profiles.firstOrNull { runCatching { it.matches(source) }.getOrDefault(false) } ?: return true
    if (profile.revoked) return false
    if (source.input.startsWith("https://") && !runCatching { profile.matches(source) }.getOrDefault(false)) return false
    if (profile.publicKey.isBlank()) return true
    val proof = source.verification ?: return false
    return proof.scope == profile.scope && proof.keySha256 == SkillSignatures.fingerprint(profile.publicKey)
}

/** A historical local/unsigned version cannot shed the workflow's current source binding. */
fun skillVersionSource(current: SkillSource, selected: SkillSource, profiles: List<SkillSourceProfile>): SkillSource {
    val bound = current.profileId ?: profiles.firstOrNull { runCatching { it.matches(current) }.getOrDefault(false) }?.id
    val source = selected.copy(profileId = bound ?: selected.profileId)
    require(skillSourcePermitted(source, profiles)) { "Source policy blocks this workflow version" }
    return source
}

/** Format v1 signs the bounded canonical declaration, not implementation-specific serialized JSON. */
object SkillSignatures {
    private val spkiPrefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    fun key(encoded: String): java.security.PublicKey {
        val bytes = decode(encoded, 44)
        require(bytes.copyOfRange(0, 12).contentEquals(spkiPrefix)) { "Expected Ed25519 SPKI" }
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(bytes))
    }
    fun fingerprint(encoded: String): String { key(encoded); return skillDigest(decode(encoded, 44)) }
    fun declaration(scope: String, digest: String, bytes: Long): ByteArray {
        require(scope.matches(Regex("github:[a-z0-9_.-]+/[a-z0-9_.-]+")) && scope.length <= 207)
        require(digest.matches(Regex("[a-f0-9]{64}")) && bytes in 1..64L * 1024 * 1024)
        return "Aurora Skill Archive v1\nsource:$scope\nsha256:$digest\nbytes:$bytes\n".toByteArray(Charsets.US_ASCII)
    }
    fun verify(archive: Path, profile: SkillSourceProfile, sidecar: String): SkillVerification {
        profile.validate(); require(profile.publicKey.isNotBlank())
        require(sidecar.length <= 1024 && sidecar.startsWith("aurora-skill-ed25519-v1\n") && sidecar.endsWith("\n"))
        val signature = sidecar.removePrefix("aurora-skill-ed25519-v1\n").removeSuffix("\n")
        val (digest, size) = digest(archive)
        val verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key(profile.publicKey))
        verifier.update(declaration(profile.scope, digest, size))
        require(verifier.verify(decode(signature, 64))) { "Skill signature rejected" }
        return SkillVerification(profile.scope, digest, size, fingerprint(profile.publicKey), signature)
    }
    private fun digest(archive: Path): Pair<String, Long> {
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS))
        val digest = MessageDigest.getInstance("SHA-256"); var size = 0L
        Files.newInputStream(archive, LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Skill verification cancelled")
                val count = input.read(buffer); if (count < 0) break
                size += count; require(size <= 64L * 1024 * 1024); digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } to size
    }
    private fun decode(value: String, size: Int): ByteArray {
        require(value.length <= 128)
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size == size && Base64.getEncoder().encodeToString(bytes) == value)
        return bytes
    }
}
