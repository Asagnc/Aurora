package io.github.asagnc.aurora.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
enum class Protocol { CHAT, RESPONSES, ANTHROPIC }

@Serializable
data class Connection(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val protocol: Protocol,
    val model: String,
    val contextLimit: Long? = null,
    val effort: String = "auto",
    val enabled: Boolean = true,
    val official: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val complete: Boolean = true,
)

@Serializable
data class WireHistory(
    val connectionId: String,
    val model: String,
    val protocol: Protocol,
    val effort: String,
    val entries: List<JsonObject> = emptyList(),
)

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val pinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val wire: WireHistory? = null,
    val interrupted: Boolean = false,
)

@Serializable
data class Usage(
    val input: Long? = null,
    val output: Long? = null,
    val cacheRead: Long? = null,
    val cacheWrite: Long? = null,
) {
    val cacheShare: Double? get() = input?.takeIf { it > 0 }?.let { total ->
        cacheRead?.let { cached -> cached.toDouble() / total }
    }
}

data class ToolCall(val id: String, val name: String, val arguments: JsonObject)
data class ModelTurn(
    val text: String,
    val nativeEntries: List<JsonObject>,
    val calls: List<ToolCall>,
    val stopReason: String,
    val usage: Usage,
)
data class ToolDefinition(val name: String, val description: String, val schema: JsonObject)
data class ModelRequest(
    val connection: Connection,
    val system: String,
    val history: List<JsonObject>,
    val tools: List<ToolDefinition>,
)

interface ModelTransport {
    suspend fun generate(request: ModelRequest, onText: suspend (String) -> Unit): ModelTurn
}
interface ToolExecutor {
    val definitions: List<ToolDefinition>
    suspend fun execute(call: ToolCall): String
}
