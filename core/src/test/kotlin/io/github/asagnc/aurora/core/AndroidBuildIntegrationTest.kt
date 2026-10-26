package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes

class AndroidBuildIntegrationTest {
    private val repository get() = Path.of(requireNotNull(System.getProperty("aurora.repository")))
    private fun metadata() = AndroidBuildTools.metadataSha256.keys.associateWith {
        Files.readAllBytes(repository.resolve("tools/apk-build/sdk-metadata/$it"))
            .toString(Charsets.UTF_8).replace("\r\n", "\n").toByteArray(Charsets.UTF_8)
    }

    @Test fun productionLargeFixedDownloadWhenExplicitlyQualified() = runTest(timeout = 5.minutes) {
        assumeTrue(System.getenv("AURORA_ANDROID_BUILD_DOWNLOAD") == "1", "Explicit fixed archive network qualification required")
        val directory = Files.createTempDirectory("aurora-large-fixed-download-")
        try {
            val component = AndroidBuildTools.components.single { it.id == "gradle" }
            val target = directory.resolve("gradle.archive")
            SkillNetwork().downloadTool(component, target)
            assertEquals(component.compressedBytes, Files.size(target))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun largerToolBudgetCannotBeUsedForUncompiledInputsOrOrdinarySkills() = runTest {
        val directory = Files.createTempDirectory("aurora-tool-download-boundary-")
        try {
            val component = AndroidBuildTools.components.first().copy(sha256 = "0".repeat(64))
            assertFailsWith<IllegalArgumentException> { SkillNetwork().downloadTool(component, directory.resolve("untrusted")) }
            assertFailsWith<IllegalArgumentException> { SkillNetwork().download("https://example.invalid/archive", directory.resolve("ordinary"), 129L * 1024 * 1024) }
            assertFalse(Files.exists(directory.resolve("untrusted"))); assertFalse(Files.exists(directory.resolve("ordinary")))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun argvRejectsTraversalOptionsAndShellAndKeepsLiteralTasks() {
        assertEquals(listOf(AndroidBuildRuntime.launcherPath, "/mnt/projects/owned", ":app:assembleDebug"),
            AndroidBuildRequest("projects/owned", listOf(":app:assembleDebug")).argv)
        for (project in listOf("/etc", "../other", "a/../b", "x;id", "a//b", "a/./b"))
            assertFailsWith<IllegalArgumentException> { AndroidBuildRequest(project, listOf("assembleDebug")) }
        for (task in listOf("--init-script", "assembleDebug;id", "a b", "-Dkey=value", "", ":"))
            assertFailsWith<IllegalArgumentException> { AndroidBuildRequest("owned", listOf(task)) }
    }

    @Test fun streamedFixedInputRejectsTamperingAndNeverOverwrites() = runTest {
        val root = Files.createTempDirectory("aurora-sdk-copy-")
        try {
            val bytes = ByteArray(150000) { (it % 251).toByte() }
            Files.write(root.resolve("selected"), bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val component = StaticReverseComponent("android-host", "fixture", "", hash, bytes.size.toLong(), true)
            val target = root.resolve("stage")
            OwnedFiles(root).copyVerifiedArchive("selected", target, component)
            assertContentEquals(bytes, Files.readAllBytes(target))
            assertFails { OwnedFiles(root).copyVerifiedArchive("selected", target, component) }
            assertContentEquals(bytes, Files.readAllBytes(target)); Files.delete(target)
            bytes[0] = 90; Files.write(root.resolve("selected"), bytes)
            assertFails { OwnedFiles(root).copyVerifiedArchive("selected", target, component) }
            assertFalse(Files.exists(target))
            assertFails { OwnedFiles(root).copyVerifiedArchive("../selected", target, component) }
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun fixedMetadataAndRuntimeScriptsHaveStableIdentity() {
        for ((name, bytes) in metadata()) {
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(AndroidBuildTools.metadataSha256.getValue(name), hash)
        }
        assertTrue(AndroidBuildRuntime.probe.contains("Machine: EM_AARCH64"))
        assertTrue(AndroidBuildRuntime.probe.contains("count == 0"))
        assertNotEquals(StaticReverseTools.bundleId, AndroidBuildTools.bundleId)
    }

    @Test fun qualificationRequiresCurrentBundleAndCompleteExitProof() {
        val context = StaticReverseProbeContext(0, "owned", "generation")
        val completion = objectOf("exitCode" to JsonPrimitive(0), "scopeRemoved" to JsonPrimitive(true),
            "interrupted" to JsonPrimitive(false), "timedOut" to JsonPrimitive(false))
        val proof = objectOf("protocol" to JsonPrimitive(1), "runId" to string("owned"), "generation" to string("generation"),
            "toolBundle" to string(AndroidBuildTools.bundleId), "stopped" to JsonPrimitive(true), "completion" to completion,
            "result" to objectOf("stdout" to objectOf("text" to string("build output\n${AndroidBuildRuntime.probeMarker}\n"))))
        assertTrue(androidBuildProbePassed(context, proof))
        val noNewline = objectOf("stdout" to objectOf("text" to string("SDK progress...${AndroidBuildRuntime.probeMarker}\n")))
        assertFalse(androidBuildProbePassed(context, JsonObject(proof + ("result" to noNewline))))
        for ((key, value) in listOf("runId" to string("other"), "toolBundle" to string(StaticReverseTools.bundleId),
            "stopped" to JsonPrimitive(false), "generation" to string("old"), "result" to JsonNull))
            assertFalse(androidBuildProbePassed(context, JsonObject(proof + (key to value))))
        for ((key, value) in listOf("scopeRemoved" to JsonPrimitive(false), "exitCode" to JsonPrimitive(1),
            "timedOut" to JsonPrimitive(true), "interrupted" to JsonPrimitive(true)))
            assertFalse(androidBuildProbePassed(context, JsonObject(proof + ("completion" to JsonObject(completion + (key to value))))))
    }

    @Test fun productionInstallerPreparesActualQualifiedInputsWhenExplicitlySupplied() {
        val selected = System.getenv("AURORA_ANDROID_BUILD_INPUTS")
        assumeTrue(selected != null, "Explicit Linux ARM64 qualification inputs required")
        val inputs = Path.of(requireNotNull(selected)).toRealPath()
        val stage = Files.createDirectory(inputs.resolve("prepared"))
        for (component in AndroidBuildTools.components) {
            AndroidBuildPreparation.prepare(component, inputs.resolve("${component.id}.archive"), stage.resolve(component.id)) { path, bits ->
                Files.setAttribute(path, "unix:mode", bits, LinkOption.NOFOLLOW_LINKS)
            }
        }
        AndroidBuildPreparation.finish(stage, metadata()) { path, bits -> Files.setAttribute(path, "unix:mode", bits, LinkOption.NOFOLLOW_LINKS) }
        assertTrue(Files.isExecutable(stage.resolve("android-host/bin/aurora-apk-probe")))
        assertEquals(AndroidBuildRuntime.rejection, Files.readAllBytes(stage.resolve("sdk/build-tools/36.0.0/aapt")).toString(Charsets.UTF_8))
        assertTrue(Files.isRegularFile(stage.resolve("sdk/ndk/30.0.16248370/package.xml")))
    }
}
