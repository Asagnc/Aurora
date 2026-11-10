package io.github.asagnc.aurora.core

import kotlin.test.*

class PresentationCacheTest {
    @Test fun labelsAndCatalogueDoNotChangeRequestBytesOrReuseScope() {
        val original = Connection(name = "Original", baseUrl = "https://example.com/v1", protocol = Protocol.CHAT,
            model = "gpt-6.1-sol", credentialRevision = "revision", effort = "high")
        val presented = original.copy(name = "Renamed", models = listOf(AvailableModel("gpt-6.1-sol", "Friendly name"),
            AvailableModel("other-model", "Other name")))
        val native = WireHistory(original.id, original.model, original.protocol, original.effort,
            credentialRevision = original.credentialRevision, baseUrl = original.baseUrl, prefixFingerprint = "same-prefix")
        assertEquals(CacheImpact.UNCHANGED, CachePolicy.impact(original, presented))
        assertTrue(CachePolicy.canReuse(native, presented, "same-prefix"))
        val history = listOf(MessageProjection.message(Protocol.CHAT, "user", "same text"))
        assertEquals(RequestEncoder.encode(ModelRequest(original, "system", history, emptyList())),
            RequestEncoder.encode(ModelRequest(presented, "system", history, emptyList())))
        assertEquals(CacheImpact.NEW_SCOPE, CachePolicy.impact(presented, presented.copy(model = "other-model")))
        assertFalse(CachePolicy.canReuse(native, presented.copy(model = "other-model"), "same-prefix"))
    }
}
