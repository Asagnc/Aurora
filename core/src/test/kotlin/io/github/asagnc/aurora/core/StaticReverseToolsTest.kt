package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest
import kotlin.test.*

class StaticReverseToolsTest {
    private fun fixture(bytes: ByteArray) = StaticReverseComponent("apktool", "fixture", "https://github.com/example/fixture",
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, bytes.size.toLong())

    @Test fun compiledBundlePinsAllComponentsAndIncludesRuntimeWithoutChangingLinuxImage() {
        assertEquals(setOf("jre", "jadx", "apktool"), StaticReverseTools.components.map { it.id }.toSet())
        assertEquals(149086879L, StaticReverseTools.compressedBytes)
        assertTrue(StaticReverseTools.bundleId.matches(Regex("[a-f0-9]{64}")))
        assertEquals(3, StaticReverseTools.components.map { it.sha256 }.toSet().size)
    }

    @Test fun verifierRejectsSameSizeTamperingAndTruncation() {
        val path = Files.createTempFile("aurora-tool-hash-", ".bin")
        try {
            val bytes = byteArrayOf(1, 2, 3); val component = fixture(bytes)
            Files.write(path, bytes); verifyStaticReverseArchive(component, path)
            Files.write(path, byteArrayOf(1, 2, 4)); assertFails { verifyStaticReverseArchive(component, path) }
            Files.write(path, byteArrayOf(1)); assertFails { verifyStaticReverseArchive(component, path) }
        } finally { Files.deleteIfExists(path) }
    }

    @Test fun unknownComponentsAndExistingTargetsCannotBePrepared() {
        val root = Files.createTempDirectory("aurora-tool-staging-")
        try {
            val archive = root.resolve("archive"); Files.write(archive, byteArrayOf(1))
            assertFails { StaticReversePreparation.prepare(fixture(byteArrayOf(1)), archive, root.resolve("prepared")) }
            assertFalse(Files.exists(root.resolve("prepared")))
            assertFails { StaticReversePreparation.prepare(StaticReverseTools.components.last(), archive, root) }
            assertContentEquals(byteArrayOf(1), Files.readAllBytes(archive))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun officialSamplesMatchCatalogueAndPrepareActualCliClosureWhenSupplied() {
        val supplied = System.getenv("AURORA_REVERSE_SAMPLES")
        org.junit.jupiter.api.Assumptions.assumeTrue(supplied != null, "Explicit official tool samples required")
        val root = Path.of(requireNotNull(supplied))
        val target = Files.createTempDirectory("aurora-official-tools-")
        try {
            for (component in StaticReverseTools.components) {
                val archive = root.resolve(component.url.substringAfterLast('/'))
                verifyStaticReverseArchive(component, archive)
                if (component.id == "jre") {
                    val plan = RootfsArchive().scan(archive)
                    assertTrue(plan.all { it.path == "jdk-27+35-jre" || it.path.startsWith("jdk-27+35-jre/") })
                    assertTrue(plan.any { it.path == "jdk-27+35-jre/bin/java" && it.kind == "file" })
                } else StaticReversePreparation.prepare(component, archive, target.resolve(component.id))
            }
            assertTrue(Files.size(target.resolve("jadx/lib/jadx-1.5.6-all.jar")) > 0)
            assertTrue(Files.size(target.resolve("apktool/apktool.jar")) > 0)
        } finally { target.toFile().deleteRecursively() }
    }

    @Test fun interruptedVerificationPropagatesWithoutPublishing() {
        val path = Files.createTempFile("aurora-tool-interrupt-", ".bin")
        try {
            Files.write(path, byteArrayOf(1)); Thread.currentThread().interrupt()
            assertFailsWith<InterruptedException> { verifyStaticReverseArchive(fixture(byteArrayOf(1)), path) }
        } finally { Thread.interrupted(); Files.deleteIfExists(path) }
    }
}
