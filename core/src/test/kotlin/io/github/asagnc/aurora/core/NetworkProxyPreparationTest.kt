package io.github.asagnc.aurora.core

import java.nio.file.*
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue

class NetworkProxyPreparationTest {
    @Test fun compiledInputMatchesTheExistingReviewedProxyPin() {
        val repository = Path.of(requireNotNull(System.getProperty("aurora.repository")))
        val pin = WireJson.parseToJsonElement(Files.readAllBytes(repository.resolve("tools/network-proxy/catalog.json")).toString(Charsets.UTF_8)).jsonObject
        val component = NetworkProxyPreparation.component
        assertEquals(pin.text("sha256"), component.sha256); assertEquals(pin.number("bytes"), component.compressedBytes)
        assertEquals(pin.text("url"), component.url); assertEquals(pin.text("version"), component.version)
        assertEquals((pin["members"]!!.jsonObject["mitmdump"]!!.jsonObject).text("sha256"), NetworkProxyPreparation.selectedSha256)
        assertFailsWith<IllegalArgumentException> { component.copy(id = "jdk") }
    }

    @Test fun uncompiledSelectionIsRejectedBeforeCreatingStage() {
        val directory = Files.createTempDirectory("aurora-proxy-reject-")
        try {
            val selected = NetworkProxyPreparation.component.copy(sha256 = "0".repeat(64))
            assertFailsWith<IllegalArgumentException> {
                NetworkProxyPreparation.prepare(selected, directory.resolve("absent"), directory.resolve("stage")) { _, _ -> }
            }
            assertFalse(Files.exists(directory.resolve("stage")))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun actualFixedStandaloneImportOnPosixKeepsOnlyExactMitmdump() {
        val archive = System.getenv("AURORA_PROXY_IMPORT")
        assumeTrue(archive != null, "Explicit fixed proxy archive required; no Android runtime execution implied")
        val directory = Files.createTempDirectory("aurora-proxy-import-")
        try {
            val target = directory.resolve("mitmdump")
            NetworkProxyPreparation.prepare(NetworkProxyPreparation.component, Path.of(requireNotNull(archive)), target) { path, bits ->
                Files.setAttribute(path, "unix:mode", bits, LinkOption.NOFOLLOW_LINKS)
            }
            Files.list(target).use { assertEquals(listOf("mitmdump"), it.map { entry -> entry.fileName.toString() }.toList()) }
            verifyStaticReverseArchive(NetworkProxyPreparation.component.copy(compressedBytes = 32495056,
                sha256 = NetworkProxyPreparation.selectedSha256), target.resolve("mitmdump"))
            assertTrue(Files.isExecutable(target.resolve("mitmdump")))
        } finally { directory.toFile().deleteRecursively() }
    }
}
