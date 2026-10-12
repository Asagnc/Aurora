package io.github.asagnc.aurora.core

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class RuntimeBoundaryTest {
    @Test fun anthropicToolResultsAreOneUserTurnWithAllCallIds() {
        val results = MessageProjection.results(Protocol.ANTHROPIC,
            listOf(ToolCall("a", "read", objectOf()) to "one", ToolCall("b", "read", objectOf()) to "two"))
        assertEquals(1, results.size)
        assertEquals(listOf("a", "b"), results.single().array("content").map { it.jsonObject.text("tool_use_id") })
    }

    @Test fun changedCredentialOrPrefixNeverReusesOpaqueNativeHistory() {
        val connection = Connection(name = "test", baseUrl = "https://example.com/v1", protocol = Protocol.CHAT, model = "test", credentialRevision = "first")
        val native = objectOf("secret_native_state" to string("opaque"))
        val wire = WireHistory(connection.id, connection.model, connection.protocol, "auto", listOf(native), "first", connection.baseUrl, "prefix")
        val conversation = Conversation(messages = listOf(ChatMessage(role = "user", text = "facts")), wire = wire)
        assertEquals(listOf(native), MessageProjection.history(conversation, connection, "prefix"))
        assertEquals("facts", MessageProjection.history(conversation, connection.copy(credentialRevision = "second"), "prefix").single().text("content"))
        assertEquals("facts", MessageProjection.history(conversation, connection, "changed-prefix").single().text("content"))
    }

    @Test fun unsupportedOfficialToolProtocolFailsBeforeNetwork() {
        val connection = Connection(name = "official", baseUrl = "https://api.openai.com/v1", protocol = Protocol.CHAT, model = "gpt-6.1-sol")
        val request = ModelRequest(connection, "system", emptyList(), listOf(ToolDefinition("read", "read", objectOf())))
        assertFailsWith<ConfigurationException> { RequestEncoder.encode(request) }
        assertNotNull(RequestEncoder.encode(request.copy(connection = connection.copy(protocol = Protocol.RESPONSES))))
    }

    @Test fun unrelatedModelNamesDoNotInheritReasoningControls() {
        val connection = Connection(name = "test", baseUrl = "https://example.com/v1", protocol = Protocol.ANTHROPIC, model = "claude-unknown")
        assertEquals(listOf("auto"), CachePolicy.supportedEfforts(connection))
    }

    @Test fun fileToolsRequireRealVersionsAndKeepResourcesReadOnly() = runTest {
        val root = Files.createTempDirectory("aurora-tools")
        val executor = FileToolExecutor(mapOf("conversation" to OwnedFiles(root), "resources" to OwnedFiles(root.resolve("resources"), false)))
        val create = ToolCall("c", "files_edit_batch", objectOf("space" to string("conversation"), "edits" to JsonArray(listOf(
            objectOf("path" to string("a"), "expectedVersion" to JsonNull, "text" to string("actual"))))))
        assertEquals("committed", WireJson.parseToJsonElement(executor.execute(create)).jsonObject.text("status"))
        assertEquals("actual", Files.readString(root.resolve("a")))
        assertFailsWith<IllegalArgumentException> { executor.execute(create) }
        assertFailsWith<IllegalArgumentException> { executor.execute(create.copy(arguments = JsonObject(create.arguments + ("space" to string("resources"))))) }
    }
}
