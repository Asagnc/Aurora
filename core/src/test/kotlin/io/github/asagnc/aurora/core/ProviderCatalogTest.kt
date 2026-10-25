package io.github.asagnc.aurora.core

import kotlin.test.*
import kotlinx.serialization.json.*

class ProviderCatalogTest {
    @Test fun currentKimiAndMiniMaxEffortWireShapesRejectUnsupportedNone() {
        val kimi = Connection(name = "Kimi", baseUrl = "https://api.moonshot.cn/v1", protocol = Protocol.CHAT, model = "kimi-k3.1-pro", effort = "high")
        val mini = kimi.copy(name = "MiniMax", baseUrl = "https://api.minimax.cn/anthropic/v1",
            protocol = Protocol.ANTHROPIC, model = "MiniMax-M3.1-Flash", effort = "xhigh")
        fun encode(connection: Connection) = RequestEncoder.encode(ModelRequest(connection, "fixture", emptyList(), emptyList()))
        assertEquals("high", encode(kimi).text("reasoning_effort"))
        assertEquals("xhigh", encode(mini)["output_config"]!!.jsonObject.text("effort"))
        assertEquals("adaptive", encode(mini)["thinking"]!!.jsonObject.text("type"))
        for (connection in listOf(kimi, mini)) {
            assertFailsWith<ConfigurationException> { encode(connection.copy(effort = "none")) }
            assertFalse("reasoning_effort" in encode(connection.copy(effort = "auto")))
        }
        assertFalse(CachePolicy.supportsEffortUpdates(kimi))
        assertFalse(CachePolicy.supportsEffortUpdates(mini))
    }
    @Test fun aliWorkspaceCannotInjectAnUnrelatedCredentialDestination() {
        val preset = ProviderCatalog.presets.first { it.requiresWorkspace }
        assertFailsWith<IllegalArgumentException> { preset.endpoint() }
        listOf("x.evil.com", "x/redirect", "x@evil.com", "{workspace}", "x?key=secret", "-invalid").forEach {
            assertFailsWith<IllegalArgumentException> { preset.endpoint(it) }
        }
        assertEquals("https://workspace-123.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", preset.endpoint("Workspace-123"))
    }

    @Test fun providerPathsEncodeWithoutSendingRequestsOrGuessingAuthentication() {
        ProviderCatalog.presets.forEach { preset ->
            preset.models.forEach { model ->
                val connection = Connection(name = preset.name, baseUrl = preset.endpoint("workspace-123"),
                    protocol = preset.protocol, model = model, authentication = preset.authentication)
                val endpoint = HttpModelTransport.endpoint(connection)
                assertTrue(endpoint.isHttps)
                assertEquals(model, RequestEncoder.encode(ModelRequest(connection, "instructions", emptyList(),
                    listOf(ToolDefinition("read", "read", objectOf())))).text("model"))
            }
        }
        val mimo = ProviderCatalog.presets.first { it.name == "小米 MiMo" }
        assertEquals(Authentication.API_KEY_HEADER, mimo.authentication)
        val minimax = ProviderCatalog.presets.first { it.name == "MiniMax" }
        assertEquals("/anthropic/v1/messages", HttpModelTransport.endpoint(Connection(name = minimax.name,
            baseUrl = minimax.endpoint(), protocol = minimax.protocol, model = minimax.models.first())).encodedPath)
    }
}
