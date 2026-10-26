package io.github.asagnc.aurora.core

import java.util.Base64
import kotlin.test.*
import kotlinx.serialization.json.*

class NetworkProxyInputTest {
    private fun parse(text: String) = networkProxyOptions(WireJson.parseToJsonElement(text).jsonObject)

    @Test fun explicitHeaderEditsPreserveRemovalAndLiteralBinaryData() {
        val value = parse("""{"port":18080,"rules":[{"stage":"response","host":"owned.example","headers":[
            {"name":"X-Owned","value":"modified"},{"name":"X-Remove","remove":true}],"bodyBase64":"b3duZWQ="}]}""")
        assertEquals(60, value.durationSeconds)
        assertEquals(mapOf("X-Owned" to "modified", "X-Remove" to null), value.rules.single().headers)
        assertEquals("b3duZWQ=", value.rules.single().bodyBase64)
    }

    @Test fun unknownCodeFieldsDuplicateHeadersAndImplicitTypeCoercionAreRejected() {
        val invalid = listOf(
            """{"port":18080,"script":"evil.py"}""", """{"port":18080,"protocol":2}""", """{"port":"18080"}""",
            """{"port":18080,"durationSeconds":true}""", """{"port":18080,"maxFlows":"12"}""",
            """{"port":18080,"rules":[{"stage":"request","host":"owned.example","headers":[{"name":"X-A","value":"a"},{"name":"x-a","value":"b"}]}]}""",
            """{"port":18080,"rules":[{"stage":"request","host":"owned.example","headers":[{"name":"X-A","value":"a","remove":true}]}]}""",
            """{"port":18080,"rules":[{"stage":"request","host":"owned.example","headers":[{"name":"X-A","value":42}]}]}""",
            """{"port":18080,"rules":[{"stage":"request","host":"owned.example","headers":[{"name":"X-A","value":"a","remove":"false"}]}]}""")
        invalid.forEach { assertFails { parse(it) } }
    }

    @Test fun combinedArgvBudgetRemainsWithinTheExistingLinuxTicketLimit() {
        val body = Base64.getEncoder().encodeToString(ByteArray(16384))
        val rule = NetworkProxyRule("request", "owned.example", bodyBase64 = body)
        assertTrue(NetworkProxyOptions(18080, listOf(rule)).encoded().length < 65536)
        assertFailsWith<IllegalArgumentException> { NetworkProxyOptions(18080, listOf(rule, rule)).validate() }
    }
}
