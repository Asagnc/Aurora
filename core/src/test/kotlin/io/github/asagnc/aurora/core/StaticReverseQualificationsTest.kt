package io.github.asagnc.aurora.core

import kotlin.test.*

class StaticReverseQualificationsTest {
    private val identity = LinuxRootIdentity(1, 2, "boot", "epoch")
    private fun scope(generation: String = "first", bundle: String = "bundle") =
        StaticReverseQualifications.Scope(generation, identity, bundle)

    @Test fun anotherGenerationOrRootIdentityDoesNotEraseStartupEvidence() {
        val qualifications = StaticReverseQualifications()
        val first = scope()
        assertTrue(qualifications.complete(qualifications.begin(first)))
        val second = scope("second")
        val incomplete = qualifications.begin(second)
        assertTrue(qualifications.ready(first))
        assertFalse(qualifications.ready(second))
        assertTrue(qualifications.complete(incomplete))
        assertTrue(qualifications.ready(first))
        assertTrue(qualifications.ready(second))
        assertFalse(qualifications.ready(first.copy(identity = identity.copy(epoch = "reconnected"))))
        assertFalse(qualifications.ready(first.copy(bundle = "new-bundle")))
    }

    @Test fun supersededAttemptCannotRestoreEvidenceAfterRetryOrRemoval() {
        val qualifications = StaticReverseQualifications()
        val key = scope()
        val old = qualifications.begin(key)
        val retry = qualifications.begin(key)
        assertFalse(qualifications.complete(old))
        assertFalse(qualifications.ready(key))
        assertTrue(qualifications.complete(retry))
        qualifications.invalidate(key.bundle)
        assertFalse(qualifications.ready(key))
        assertFalse(qualifications.complete(retry))
        val reinstalled = qualifications.begin(key)
        assertFalse(qualifications.complete(retry))
        assertFalse(qualifications.ready(key))
        assertTrue(qualifications.complete(reinstalled))
    }

    @Test fun removalTargetsOneBundleAndCacheEvictionRequiresFreshProof() {
        val qualifications = StaticReverseQualifications()
        val kept = scope(bundle = "kept")
        qualifications.complete(qualifications.begin(kept))
        val removed = qualifications.begin(scope(bundle = "removed"))
        qualifications.invalidate("removed")
        assertTrue(qualifications.ready(kept))
        assertFalse(qualifications.complete(removed))
        val oldest = qualifications.begin(scope("oldest"))
        qualifications.complete(oldest)
        repeat(32) { qualifications.complete(qualifications.begin(scope("generation-$it"))) }
        assertFalse(qualifications.ready(oldest.scope))
        assertFalse(qualifications.complete(oldest))
        assertTrue(qualifications.ready(scope("generation-31")))
    }
}
