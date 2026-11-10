package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

object RequestEncoder {
    fun encode(request: ModelRequest): JsonObject {
        val connection = request.connection
        ModelRules.validate(request)
        val fields = linkedMapOf<String, JsonElement>("model" to string(connection.model), "stream" to JsonPrimitive(true))
        when (connection.protocol) {
            Protocol.CHAT -> {
                fields["messages"] = JsonArray(listOf(MessageProjection.message(Protocol.CHAT, "system", request.system)) + request.history)
                fields["stream_options"] = objectOf("include_usage" to JsonPrimitive(true))
                fields["tools"] = JsonArray(request.tools.map { tool -> objectOf("type" to string("function"), "function" to definition(tool)) })
                if (connection.effort != "auto") fields["reasoning_effort"] = string(connection.effort)
            }
            Protocol.RESPONSES -> {
                fields["input"] = JsonArray(listOf(MessageProjection.message(Protocol.RESPONSES, "developer", request.system)) + request.history)
                fields["tools"] = JsonArray(request.tools.map { tool -> JsonObject(definition(tool) + ("type" to string("function"))) })
                if (connection.effort != "auto") fields["reasoning"] = objectOf("effort" to string(connection.effort))
                fields["store"] = JsonPrimitive(false)
            }
            Protocol.ANTHROPIC -> {
                fields["system"] = JsonArray(listOf(objectOf("type" to string("text"), "text" to string(request.system),
                    "cache_control" to objectOf("type" to string("ephemeral")))))
                fields["messages"] = JsonArray(withAnthropicBreakpoint(request.history))
                fields["max_tokens"] = JsonPrimitive(8192)
                fields["tools"] = JsonArray(request.tools.mapIndexed { index, tool ->
                    val definition = objectOf("name" to string(tool.name), "description" to string(tool.description), "input_schema" to tool.schema)
                    if (index == request.tools.lastIndex) JsonObject(definition + ("cache_control" to ephemeral())) else definition
                })
                if (connection.effort != "auto") {
                    fields["thinking"] = objectOf("type" to string("adaptive"))
                    fields["output_config"] = objectOf("effort" to string(connection.effort))
                }
            }
        }
        if (request.tools.isEmpty()) fields.remove("tools")
        ProviderCacheRouting.apply(request, fields)
        return JsonObject(fields)
    }

    private fun definition(tool: ToolDefinition): JsonObject = objectOf(
        "name" to string(tool.name), "description" to string(tool.description), "parameters" to tool.schema
    )

    private fun ephemeral() = objectOf("type" to string("ephemeral"))

    private fun withAnthropicBreakpoint(history: List<JsonObject>): List<JsonObject> {
        if (history.isEmpty()) return history
        val last = history.last()
        val blocks = when (val content = last["content"]) {
            is JsonArray -> content.toMutableList()
            is JsonPrimitive -> mutableListOf<JsonElement>(objectOf("type" to string("text"), "text" to content))
            else -> return history
        }
        val eligible = blocks.indexOfLast { candidate ->
            val block = candidate as? JsonObject ?: return@indexOfLast false
            when (block.text("type")) {
                "text" -> !block.text("text").isNullOrEmpty()
                "image", "document", "tool_use", "tool_result" -> true
                else -> false
            }
        }
        if (eligible < 0) return history
        val block = blocks[eligible] as? JsonObject ?: return history
        blocks[eligible] = JsonObject(block + ("cache_control" to objectOf("type" to string("ephemeral"))))
        return history.dropLast(1) + JsonObject(last + ("content" to JsonArray(blocks)))
    }
}
