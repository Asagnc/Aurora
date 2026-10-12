package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

object MessageProjection {
    fun message(protocol: Protocol, role: String, text: String): JsonObject = when (protocol) {
        Protocol.CHAT, Protocol.ANTHROPIC -> objectOf("role" to string(role), "content" to string(text))
        Protocol.RESPONSES -> objectOf("role" to string(role), "content" to JsonArray(listOf(
            objectOf("type" to string(if (role == "assistant") "output_text" else "input_text"), "text" to string(text))
        )))
    }

    fun history(conversation: Conversation, connection: Connection): List<JsonObject> {
        val native = conversation.wire
        if (native != null && native.connectionId == connection.id && native.model == connection.model &&
            native.protocol == connection.protocol) return native.entries
        return conversation.messages.filter { it.complete && it.role in setOf("user", "assistant") }
            .map { message(connection.protocol, it.role, it.text) }
    }

    fun result(protocol: Protocol, call: ToolCall, output: String): JsonObject = when (protocol) {
        Protocol.CHAT -> objectOf("role" to string("tool"), "tool_call_id" to string(call.id), "content" to string(output))
        Protocol.RESPONSES -> objectOf("type" to string("function_call_output"), "call_id" to string(call.id), "output" to string(output))
        Protocol.ANTHROPIC -> objectOf("role" to string("user"), "content" to JsonArray(listOf(
            objectOf("type" to string("tool_result"), "tool_use_id" to string(call.id), "content" to string(output))
        )))
    }
}
