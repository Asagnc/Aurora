package io.github.asagnc.aurora.core

import java.util.Base64
import kotlin.test.*
import kotlinx.serialization.decodeFromString

class NetworkProxyRulesTest {
    private val rule = NetworkProxyRule("response", "owned.example", headers = mapOf("X-Owned" to "changed", "X-Remove" to null), bodyBase64 = "b3duZWQ=")

    @Test fun boundedLiteralRuleRoundTripsAndUsesCanonicalBinaryReplacement() {
        val options = NetworkProxyOptions(18080, listOf(rule))
        val actual = WireJson.decodeFromString<NetworkProxyOptions>(Base64.getDecoder().decode(options.encoded()).toString(Charsets.UTF_8))
        assertEquals(options, actual)
        assertContentEquals("owned".toByteArray(), NetworkProxyRule.decodeReplacement(requireNotNull(rule.bodyBase64)))
        NetworkProxyOptions(18080, listOf(rule.copy(stage = "websocket", headers = emptyMap(), direction = "server"))).validate()
        NetworkProxyOptions(18080, listOf(rule.copy(bodyBase64 = ""))).validate()
    }

    @Test fun framingSmugglingAmbiguousMatchesAndScriptInputsAreRejected() {
        for (invalid in listOf(rule.copy(headers = mapOf("Content-Length" to "5")), rule.copy(headers = mapOf("Host" to "other.example")),
            rule.copy(headers = mapOf("X-Owned" to "a\r\nb: c")), rule.copy(headers = mapOf("X-Owned\n" to "a")),
            rule.copy(headers = mapOf("X-Owned" to "a", "x-owned" to "b")), rule.copy(host = "*.example"),
            rule.copy(host = "owned.example:443"), rule.copy(host = "owned.example/"), rule.copy(host = "Owned.Example"),
            rule.copy(host = "a..example"), rule.copy(pathPrefix = "relative"), rule.copy(pathPrefix = "/x\n"),
            rule.copy(stage = "python"), rule.copy(method = "GET;id"), rule.copy(direction = "server"),
            rule.copy(stage = "websocket", headers = emptyMap(), direction = null), rule.copy(bodyBase64 = "b3duZWQ"))) {
            assertFailsWith<IllegalArgumentException> { invalid.validate() }
        }
    }

    @Test fun allHardBudgetsAndInertEmptyRulesAreEnforced() {
        for (invalid in listOf(NetworkProxyOptions(0), NetworkProxyOptions(65536), NetworkProxyOptions(18080, protocol = 2),
            NetworkProxyOptions(18080, durationSeconds = 241), NetworkProxyOptions(18080, maxFlows = 513),
            NetworkProxyOptions(18080, maxStoredBytes = 32L * 1024 * 1024 + 1), NetworkProxyOptions(18080, maxBodyBytes = 1024 * 1024 + 1),
            NetworkProxyOptions(18080, List(33) { rule }), NetworkProxyOptions(18080, listOf(rule), maxBodyBytes = 4)))
            assertFailsWith<IllegalArgumentException> { invalid.validate() }
        assertFailsWith<IllegalArgumentException> { rule.copy(headers = emptyMap(), bodyBase64 = null).validate() }
        assertFailsWith<IllegalArgumentException> { rule.copy(bodyBase64 = Base64.getEncoder().encodeToString(ByteArray(16385))).validate() }
        NetworkProxyOptions(18080).validate()
    }
}
