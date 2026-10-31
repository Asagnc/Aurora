package io.github.asagnc.aurora.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.json.*
import kotlin.test.*

class AndroidDependencyCacheTest {
    @Test fun compiledProbeStillMatchesTheQualifiedSeedContract() {
        assertEquals(AndroidDependencyCache.seedProbeSha256, sha(AndroidBuildRuntime.probe.toByteArray(Charsets.UTF_8)))
    }

    private fun fixture(action: (Path, Path, (String) -> String) -> Unit) {
        val root = Files.createTempDirectory("aurora-public-cache-")
        try {
            val file = root.resolve("modules-2/files-2.1/example.public/owned/1/abc/owned.jar")
            Files.createDirectories(file.parent); Files.write(file, byteArrayOf(1, 2, 3))
            val write: (String) -> String = { name ->
                val bytes = objectOf("format" to JsonPrimitive(1), "gradleVersion" to string("9.8.2"),
                    "seed" to objectOf("nativeSdkSha256" to string(AndroidDependencyCache.nativeBaseSha256),
                        "probeSha256" to string(AndroidDependencyCache.seedProbeSha256)),
                    "fileCount" to JsonPrimitive(1), "bytes" to JsonPrimitive(3),
                    "files" to objectOf(name to objectOf("bytes" to JsonPrimitive(3),
                        "sha256" to string(sha(byteArrayOf(1, 2, 3)))))).toString().toByteArray()
                Files.write(root.resolve("manifest.json"), bytes); sha(bytes)
            }
            action(root, file, write)
        } finally { removeSkillTree(root) }
    }

    @Test fun compiledManifestPinAndEachActualArtifactAreRequired() = fixture { root, file, write ->
        val pin = write(root.relativize(file).joinToString("/"))
        assertFailsWith<IllegalArgumentException> { AndroidDependencyCache.verify(root) }
        AndroidDependencyCache.verify(root, pin)
        Files.write(file, byteArrayOf(3, 2, 1))
        assertFailsWith<IllegalArgumentException> { AndroidDependencyCache.verify(root, pin) }
        assertContentEquals(byteArrayOf(3, 2, 1), Files.readAllBytes(file))
    }

    @Test fun extraUninventoriedConfigurationIsRejected() = fixture { root, file, write ->
        val pin = write(root.relativize(file).joinToString("/"))
        Files.write(root.resolve("gradle.properties"), "owned inert private marker".toByteArray())
        assertFailsWith<IllegalArgumentException> { AndroidDependencyCache.verify(root, pin) }
    }

    @Test fun traversalAbsoluteAndAlternateSeparatorsCannotSelectExternalFiles() = fixture { root, _, write ->
        for (name in listOf("../outside", "/outside", "modules-2/../manifest.json", "modules-2//file", "modules-2/a\\b")) {
            val pin = write(name)
            assertFailsWith<IllegalArgumentException> { AndroidDependencyCache.verify(root, pin) }
        }
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
