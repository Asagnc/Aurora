package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SkillSignaturesTest {
    // RFC8032 test-key public half. Signature independently generated with Python cryptography, never a release key.
    private val publicKey = Base64.getEncoder().encodeToString(hex("302a300506032b6570032100d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"))
    private val profile get() = SkillSourceProfile("00000000-0000-0000-0000-000000000001", "example/skills",
        publicKey = publicKey, signatureUrl = "https://example.com/owned.sig")
    private val signature = "wDk1GqajP3hQJLkOrjdoIXCZMoj1/PELFtiF/oYsGQhFHXNxR6AF1EpruW+QGxbj/B3VUyyQ0iQI5mWb8iRHCQ=="
    private val sidecar get() = "aurora-skill-ed25519-v1\n$signature\n"
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun fixture(block: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("aurora-skill-signature")
        try { val file = root.resolve("archive"); Files.write(file, "owned skill archive bytes\n".toByteArray()); block(file) }
        finally { removeSkillTree(root) }
    }
    @Test fun independentGoldenDeclarationAndSignatureVerify() = fixture { file ->
        val proof = SkillSignatures.verify(file, profile, sidecar)
        assertEquals(proof, SkillSignatures.verify(file, profile.copy(signatureUrl = ""), sidecar))
        assertEquals("6f6c357ea242eadbc1bbfb4d213649b11087e151e6b78398acf310457d2efdfd", proof.archiveSha256)
        assertEquals(26, proof.archiveBytes)
        assertEquals("Aurora Skill Archive v1\nsource:github:example/skills\nsha256:${proof.archiveSha256}\nbytes:26\n",
            SkillSignatures.declaration(profile.scope, proof.archiveSha256, 26).toString(Charsets.US_ASCII))
    }
    @Test fun changedBytesScopeKeyAndNoncanonicalSidecarCannotVerify() = fixture { file ->
        assertFails { SkillSignatures.verify(file, profile.copy(repository = "other/skills"), sidecar) }
        val otherKey = Base64.getEncoder().encodeToString(hex("302a300506032b65700321003d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"))
        assertFails { SkillSignatures.verify(file, profile.copy(publicKey = otherKey), sidecar) }
        for (invalid in listOf(sidecar + "\n", sidecar.replace("v1", "v2"), sidecar.replace("\n", "\r\n"), sidecar.replace("wDk", "ADk"), "x".repeat(1025))) {
            assertFails { SkillSignatures.verify(file, profile, invalid) }
        }
        Files.write(file, "changed archive bytes\n".toByteArray())
        assertFails { SkillSignatures.verify(file, profile, sidecar) }
    }
    @Test fun policyRejectsUnverifiedRevokedWrongKeyAndWrongRepositoryAcquisitions() = fixture { file ->
        val source = SkillSource("https://github.com/example/skills")
        assertTrue(skillSourcePermitted(source, emptyList()))
        assertFalse(skillSourcePermitted(source, listOf(profile)))
        val verified = source.copy(profileId = profile.id, verification = SkillSignatures.verify(file, profile, sidecar))
        assertTrue(skillSourcePermitted(verified, listOf(profile)))
        assertFalse(skillSourcePermitted(verified, emptyList()))
        assertFalse(skillSourcePermitted(verified, listOf(profile.copy(revoked = true))))
        assertFalse(skillSourcePermitted(verified.copy(input = "https://github.com/other/repo"), listOf(profile)))
        assertFalse(skillSourcePermitted(verified.copy(verification = verified.verification!!.copy(scope = "github:other/repo")), listOf(profile)))
        assertFalse(profile.matches(SkillSource("https://github.com/example/skills-other")))
        assertTrue(profile.matches(SkillSource("https://github.com/example/skills/releases/download/v1/skills.zip")))
        assertFails { profile.copy(publicKey = "untrusted").validate() }
    }
    @Test fun historicalLocalVersionCannotDropTheCurrentWorkflowSignaturePolicy() = fixture { file ->
        val current = SkillSource("https://github.com/example/skills", profileId = profile.id,
            verification = SkillSignatures.verify(file, profile, sidecar))
        val local = SkillSource("content://owned-fixture/archive")
        assertFails { skillVersionSource(current, local, listOf(profile)) }
        assertFails { skillVersionSource(current, current, listOf(profile.copy(revoked = true))) }
        assertEquals(profile.id, skillVersionSource(current, local.copy(verification = current.verification), listOf(profile)).profileId)
    }
    @Test fun selectedSidecarTransportIsBoundedAsciiAndClosesItsOwnedStream() = runBlocking<Unit> {
        var closed = false
        val input = object : java.io.ByteArrayInputStream(sidecar.toByteArray()) { override fun close() { closed = true; super.close() } }
        assertEquals(sidecar, SkillSignatureTransfer.read({ input })); assertTrue(closed)
        assertFails { SkillSignatureTransfer.read({ ByteArray(1025) { 65 }.inputStream() }) }
        assertFails { SkillSignatureTransfer.read({ byteArrayOf(-1).inputStream() }) }
    }
}
