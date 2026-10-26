package io.github.asagnc.aurora.core

import java.nio.file.*
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.*
import kotlin.test.*

class NetworkProxyRuntimeTest {
    private fun profiles(): Map<String, ByteArray> {
        val repository = Path.of(requireNotNull(System.getProperty("aurora.repository")))
        return NetworkProxyRuntime.profileSha256.keys.associateWith { name ->
            val relative = if (name == "mitmproxy-LICENSE") name else "runtime/$name"
            Files.readAllBytes(repository.resolve("tools/network-proxy/$relative"))
        }
    }

    @Test fun fixedProfilesMatchCompiledPinsAndNeverOverwritePublishedInput() {
        val directory = Files.createTempDirectory("aurora-proxy-profiles-")
        try {
            val target = Files.createDirectory(directory.resolve("mitmdump"))
            val bytes = profiles()
            NetworkProxyRuntime.publishProfiles(directory, bytes) { _, _ -> }
            for ((name, data) in bytes) assertContentEquals(data, Files.readAllBytes(target.resolve(name)))
            assertFailsWith<FileAlreadyExistsException> { NetworkProxyRuntime.publishProfiles(directory, bytes) { _, _ -> } }
            assertEquals(bytes.size.toLong(), Files.list(target).use { it.count() })
            assertNotEquals(AndroidBuildTools.bundleId, NetworkProxyRuntime.bundleId)
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun modifiedAndExtraScriptsAreRejectedBeforePublication() {
        val directory = Files.createTempDirectory("aurora-proxy-profile-reject-")
        try {
            val target = Files.createDirectory(directory.resolve("mitmdump")); val original = profiles()
            for (bytes in listOf(original + (original.keys.first() to "unreviewed".toByteArray()), original + ("extra.py" to byteArrayOf(1)))) {
                assertFailsWith<IllegalArgumentException> { NetworkProxyRuntime.publishProfiles(directory, bytes) { _, _ -> } }
                assertEquals(0L, Files.list(target).use { it.count() })
            }
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun finalProbeBindsExactSessionConfigAndRealStoredCompletion() {
        val options = NetworkProxyRuntime.probeOptions(18080); val encoded = options.encoded()
        assertTrue(encoded.length < 60000)
        val hash = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(encoded)).joinToString("") { "%02x".format(it.toInt() and 255) }
        val final = objectOf("protocol" to JsonPrimitive(1), "id" to string("owned"), "port" to JsonPrimitive(18080),
            "probe" to JsonPrimitive(true), "configSha256" to string(hash), "reason" to string("probe_complete"),
            "seenFlows" to JsonPrimitive(3), "completedFlows" to JsonPrimitive(3), "events" to JsonPrimitive(4),
            "storedBytes" to JsonPrimitive(6000), "storedBytesExcludesFinalRecord" to JsonPrimitive(true))
        assertTrue(NetworkProxyRuntime.finalProbePassed("owned", options, final))
        for ((key, value) in listOf("id" to string("other"), "configSha256" to string("0".repeat(64)), "probe" to JsonPrimitive(false),
            "reason" to string("artifact_failure"), "completedFlows" to JsonPrimitive(2), "events" to JsonPrimitive(1025), "storedBytes" to JsonPrimitive(options.maxStoredBytes)))
            assertFalse(NetworkProxyRuntime.finalProbePassed("owned", options, JsonObject(final + (key to value))))
    }

    @Test fun stdoutMarkerCannotReplaceDomainExitOrAcceptDuplicateCompletion() {
        val context = StaticReverseProbeContext(0, "owned", "generation")
        val done = objectOf("exitCode" to JsonPrimitive(0), "scopeRemoved" to JsonPrimitive(true), "interrupted" to JsonPrimitive(false), "timedOut" to JsonPrimitive(false))
        val marker = NetworkProxyRuntime.probeMarker
        val proof = objectOf("protocol" to JsonPrimitive(1), "runId" to string("owned"), "generation" to string("generation"),
            "toolBundle" to string(NetworkProxyRuntime.bundleId), "stopped" to JsonPrimitive(true), "completion" to done,
            "result" to objectOf("stdout" to objectOf("text" to string("startup\n$marker\n"))))
        assertTrue(NetworkProxyRuntime.probePassed(context, proof))
        assertFalse(NetworkProxyRuntime.probePassed(context, JsonObject(proof + ("completion" to JsonObject(done + ("scopeRemoved" to JsonPrimitive(false)))))))
        for (text in listOf("SDK progress$marker\n", "$marker\n$marker\n")) {
            val result = objectOf("stdout" to objectOf("text" to string(text)))
            assertFalse(NetworkProxyRuntime.probePassed(context, JsonObject(proof + ("result" to result))))
        }
    }
}
