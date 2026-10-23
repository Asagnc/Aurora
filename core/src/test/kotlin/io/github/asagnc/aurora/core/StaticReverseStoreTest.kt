package io.github.asagnc.aurora.core

import java.nio.file.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class StaticReverseStoreTest {
    private suspend fun fixture(block: suspend (Path) -> Unit) {
        val root = Files.createTempDirectory("aurora-tool-store-")
        try { block(root) } finally { root.toFile().deleteRecursively() }
    }
    private fun store(root: Path) = StaticReverseStore(root) { component, archive, target ->
        require(Files.readString(archive) == component.id)
        Files.createDirectory(target); Files.writeString(target.resolve("fixture"), component.id)
    }
    private suspend fun fetch(component: StaticReverseComponent, target: Path) { Files.writeString(target, component.id) }

    @Test fun failedDownloadRemovesAllStagingAndNeverAdvertisesInstalled() = runTest { fixture { root ->
        val store = store(root)
        assertFailsWith<java.io.IOException> { store.install { _, target ->
            Files.writeString(target, "partial"); throw java.io.IOException("fixture failure")
        } }
        assertTrue(store.installed().isEmpty())
        Files.list(root.resolve("staging")).use { assertEquals(0L, it.count()) }
    } }

    @Test fun cancelledInstallCleansStagingAndPropagatesCancellation() = runTest { fixture { root ->
        val store = store(root)
        assertFailsWith<CancellationException> { store.install { _, target ->
            Files.writeString(target, "partial"); throw CancellationException("fixture cancel")
        } }
        assertTrue(store.installed().isEmpty())
        Files.list(root.resolve("staging")).use { assertEquals(0L, it.count()) }
    } }

    @Test fun completeBundlePublishesOnceThenCanBeRemovedAndInstalledAgain() = runTest { fixture { root ->
        val store = store(root)
        val id = store.install(::fetch)
        assertEquals(listOf(id), store.installed())
        assertEquals(id, store.install { _, _ -> error("Installed bundle must not redownload") })
        Files.list(root.resolve("staging")).use { assertEquals(0L, it.count()) }
        assertTrue(store.remove(id)); assertFalse(store.remove(id)); assertTrue(store.installed().isEmpty())
        assertEquals(id, store.install(::fetch))
    } }

    @Test fun recoveryRemovesOnlyOwnedAbandonedStagesAndNeverPublishedBytes() = runTest { fixture { root ->
        val store = store(root); val id = store.install(::fetch)
        val known = Files.createDirectory(root.resolve("staging/" + UUID.randomUUID()))
        Files.writeString(known.resolve("owner"), "aurora-static-reverse-stage-v1")
        Files.writeString(known.resolve("partial"), "fixture")
        val unknown = Files.createDirectory(root.resolve("staging/" + UUID.randomUUID()))
        Files.writeString(unknown.resolve("keep"), "unknown")
        assertEquals(1, store.cleanup()); assertFalse(Files.exists(known)); assertTrue(Files.exists(unknown))
        assertEquals(listOf(id), store.installed())
        assertFails { store.remove("../versions") }
    } }

    @Test fun interruptedRemovalAndFinalEmptyStageAreRecoverable() = runTest { fixture { root ->
        val store = store(root); val id = store.install(::fetch)
        val retired = Files.createDirectory(root.resolve("staging/" + UUID.randomUUID()))
        Files.writeString(retired.resolve("owner"), "aurora-static-reverse-stage-v1")
        Files.move(root.resolve("versions/$id"), retired.resolve("removed"), StandardCopyOption.ATOMIC_MOVE)
        val empty = Files.createDirectory(root.resolve("staging/" + UUID.randomUUID()))
        assertEquals(2, store.cleanup())
        assertTrue(store.installed().isEmpty()); assertFalse(Files.exists(retired)); assertFalse(Files.exists(empty))
    } }

    @Test fun damagedKnownBundleCanBeRemovedBeforeReinstalling() = runTest { fixture { root ->
        val store = store(root); val id = store.install(::fetch)
        removeSkillTree(root.resolve("versions/$id/jadx"))
        assertTrue(store.installed().isEmpty())
        assertFails { store.install(::fetch) }
        assertTrue(store.remove(id))
        assertEquals(id, store.install(::fetch))
    } }

    @Test fun preparationFailureRemovesEarlierComponentsAsWellAsCurrentPartial() = runTest { fixture { root ->
        val store = StaticReverseStore(root) { component, _, target ->
            Files.createDirectory(target)
            if (component.id == "jadx") throw java.io.IOException("preparation failure")
        }
        assertFailsWith<java.io.IOException> { store.install(::fetch) }
        assertTrue(store.installed().isEmpty())
        Files.list(root.resolve("staging")).use { assertEquals(0L, it.count()) }
    } }
}
