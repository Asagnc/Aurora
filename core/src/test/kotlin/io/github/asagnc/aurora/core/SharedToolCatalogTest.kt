package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SharedToolCatalogTest {
    private fun catalog(finish: (Path) -> Unit = { root ->
        for (name in listOf("jdk", "gradle", "sdk")) Files.createDirectory(root.resolve(name))
    }) = SharedToolCatalog("android-build-v1", "fixture-v1", listOf(StaticReverseComponent("android-host", "fixture",
        "https://github.com/owned/fixture", "a".repeat(64), 1)), 800L * 1024 * 1024, finish)

    private suspend fun fixture(block: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory("aurora-shared-catalog-")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }

    @Test fun legacyIdentityAndLayoutRemainCompatible() = runTest { fixture { root ->
        assertEquals(StaticReverseTools.bundleId, StaticReverseTools.catalog.bundleId)
        for (name in listOf("jre", "jadx", "apktool")) Files.createDirectory(root.resolve(name))
        assertTrue(SharedToolLayout.complete(root))
        Files.writeString(root.resolve("family"), "unknown-family")
        assertFalse(SharedToolLayout.complete(root))
    } }

    @Test fun theSameAtomicStorePublishesRecognizedFamiliesAndKeepsDurableLeaseBarrier() = runTest { fixture { root ->
        var used = false
        val store = StaticReverseStore(root, { used }) { _, _, target -> Files.createDirectory(target) }
        val selected = catalog()
        val id = store.install(selected) { _, target -> Files.write(target, byteArrayOf(1)) }
        assertEquals(selected.bundleId, id); assertEquals(listOf(id), store.installed())
        store.reserve(id) { used = true }
        assertFailsWith<IllegalStateException> { store.remove(id) }
        used = false; assertTrue(store.remove(id))
    } }

    @Test fun failedOrIncompleteFinalLayoutNeverPublishesAndCleansOnlyOwnedStage() = runTest { fixture { root ->
        val store = StaticReverseStore(root) { _, _, target -> Files.createDirectory(target) }
        for (selected in listOf(catalog { }, catalog { throw java.io.IOException("fixture finalization failure") })) {
            assertFails { store.install(selected) { _, target -> Files.write(target, byteArrayOf(1)) } }
            assertTrue(store.installed().isEmpty())
            Files.list(root.resolve("staging")).use { assertEquals(0L, it.count()) }
        }
    } }

    @Test fun identitiesSeparateFamiliesRevisionsAndFixedInputBytes() {
        val original = catalog()
        assertNotEquals(original.bundleId, original.copy(revision = "fixture-v2").bundleId)
        assertNotEquals(original.bundleId, original.copy(family = "network-proxy-v1").bundleId)
        assertNotEquals(original.bundleId, original.copy(components = original.components.map { it.copy(sha256 = "b".repeat(64)) }).bundleId)
        assertFailsWith<IllegalArgumentException> { original.components.single().copy(url = "https://github.com/user/repo?token=secret") }
        assertFailsWith<IllegalArgumentException> { original.components.single().copy(url = "https://untrusted.invalid/tool") }
    }
}
