package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

class StreamAssembler(private val protocol: Protocol) {
    private val text = StringBuilder()
    private val reasoning = StringBuilder()
    private val callParts = sortedMapOf<Int, MutableCall>()
    private val anthropicBlocks = sortedMapOf<Int, MutableBlock>()
    private var entries: List<JsonObject> = emptyList()
    private var usage = Usage()
    private var stopReason = "incomplete"
    private var complete = false

    fun accept(event: SseEvent): String {
        if (event.data == "[DONE]") { complete = protocol == Protocol.CHAT && stopReason != "incomplete"; return "" }
        val item = WireJson.parseToJsonElement(event.data).jsonObject
        item.obj("error")?.let { error("模型流错误：${it.text("type") ?: "unknown"}") }
        return when (protocol) {
            Protocol.CHAT -> chat(item)
            Protocol.RESPONSES -> responses(item)
            Protocol.ANTHROPIC -> anthropic(item)
        }
    }

    fun finish(): ModelTurn {
        check(complete) { "连接在完整终态之前结束" }
        val calls = when (protocol) {
            Protocol.CHAT -> callParts.values.map { it.finish() }
            Protocol.ANTHROPIC -> anthropicBlocks.values.filter { it.base.text("type") == "tool_use" }.map { it.call() }
            Protocol.RESPONSES -> entries.filter { it.text("type") == "function_call" }.map {
                ToolCall(requireNotNull(it.text("call_id")), requireNotNull(it.text("name")),
                    WireJson.parseToJsonElement(requireNotNull(it.text("arguments"))).jsonObject)
            }
        }
        if (protocol == Protocol.CHAT) entries = listOf(chatEntry())
        if (protocol == Protocol.ANTHROPIC) entries = listOf(objectOf("role" to string("assistant"),
            "content" to JsonArray(anthropicBlocks.values.map { it.finish() })))
        return ModelTurn(text.toString(), entries, calls, stopReason, usage)
    }

    private fun chat(item: JsonObject): String {
        item.obj("usage")?.let { usage = Usage(it.number("prompt_tokens"), it.number("completion_tokens"),
            it.obj("prompt_tokens_details")?.number("cached_tokens") ?: it.number("prompt_cache_hit_tokens"),
            it.obj("prompt_tokens_details")?.number("cache_write_tokens")) }
        val choice = item.array("choices").firstOrNull()?.jsonObject ?: return ""
        choice.text("finish_reason")?.let { stopReason = it }
        val delta = choice.obj("delta") ?: return ""
        delta.text("reasoning_content")?.let { reasoning.append(it) }
        for (part in delta.array("tool_calls")) {
            val value = part.jsonObject
            val call = callParts.getOrPut(value.number("index")!!.toInt()) { MutableCall() }
            value.text("id")?.let { call.id = it }
            value.obj("function")?.let { function ->
                function.text("name")?.let { call.name += it }
                function.text("arguments")?.let { call.arguments.append(it) }
            }
        }
        return delta.text("content").orEmpty().also { text.append(it) }
    }

    private fun responses(item: JsonObject): String {
        when (item.text("type")) {
            "response.output_text.delta" -> return item.text("delta").orEmpty().also { text.append(it) }
            "response.completed" -> {
                val response = requireNotNull(item.obj("response"))
                entries = response.array("output").map { it.jsonObject }
                stopReason = response.text("status") ?: "incomplete"
                complete = stopReason == "completed"
                response.obj("usage")?.let { usage = Usage(it.number("input_tokens"), it.number("output_tokens"),
                    it.obj("input_tokens_details")?.number("cached_tokens"), it.obj("input_tokens_details")?.number("cache_write_tokens")) }
            }
            "response.failed", "response.incomplete", "error" -> error("模型请求未完成")
        }
        return ""
    }

    private fun anthropic(item: JsonObject): String {
        when (item.text("type")) {
            "message_start" -> item.obj("message")?.obj("usage")?.let { updateAnthropicUsage(it) }
            "content_block_start" -> anthropicBlocks[item.number("index")!!.toInt()] = MutableBlock(item.obj("content_block")!!)
            "content_block_delta" -> {
                val block = anthropicBlocks[item.number("index")!!.toInt()] ?: error("内容块缺少开始事件")
                val delta = item.obj("delta")!!
                when (delta.text("type")) {
                    "text_delta" -> return delta.text("text").orEmpty().also { block.value.append(it); text.append(it) }
                    "thinking_delta" -> block.value.append(delta.text("thinking").orEmpty())
                    "signature_delta" -> block.signature.append(delta.text("signature").orEmpty())
                    "input_json_delta" -> block.value.append(delta.text("partial_json").orEmpty())
                }
            }
            "message_delta" -> {
                item.obj("delta")?.text("stop_reason")?.let { stopReason = it }
                item.obj("usage")?.let { updateAnthropicUsage(it) }
            }
            "message_stop" -> complete = stopReason != "incomplete"
            "error" -> error("Anthropic 流错误")
        }
        return ""
    }

    private fun updateAnthropicUsage(value: JsonObject) {
        val read = value.number("cache_read_input_tokens") ?: usage.cacheRead
        val write = value.number("cache_creation_input_tokens") ?: usage.cacheWrite
        val plain = value.number("input_tokens")
        usage = Usage(plain?.let { it + (read ?: 0) + (write ?: 0) } ?: usage.input,
            value.number("output_tokens") ?: usage.output, read, write)
    }

    private fun chatEntry(): JsonObject {
        val fields = linkedMapOf<String, JsonElement>("role" to string("assistant"), "content" to string(text.toString()))
        if (reasoning.isNotEmpty()) fields["reasoning_content"] = string(reasoning.toString())
        if (callParts.isNotEmpty()) fields["tool_calls"] = JsonArray(callParts.values.map { call ->
            objectOf("id" to string(call.id), "type" to string("function"), "function" to objectOf(
                "name" to string(call.name), "arguments" to string(call.arguments.toString())))
        })
        return JsonObject(fields)
    }
}

private class MutableCall {
    var id = ""
    var name = ""
    val arguments = StringBuilder()
    fun finish(): ToolCall {
        require(id.isNotBlank() && name.isNotBlank()) { "工具调用缺少标识或名称" }
        return ToolCall(id, name, WireJson.parseToJsonElement(arguments.toString()).jsonObject)
    }
}

private class MutableBlock(val base: JsonObject) {
    val value = StringBuilder()
    val signature = StringBuilder()
    fun finish(): JsonObject = when (base.text("type")) {
        "text" -> JsonObject(base + ("text" to string(base.text("text").orEmpty() + value)))
        "thinking" -> JsonObject(base + mapOf("thinking" to string(base.text("thinking").orEmpty() + value),
            "signature" to string(base.text("signature").orEmpty() + signature)))
        "tool_use" -> JsonObject(base + ("input" to if (value.isNotEmpty()) WireJson.parseToJsonElement(value.toString()) else base["input"]!!))
        else -> base
    }
    fun call(): ToolCall = ToolCall(requireNotNull(base.text("id")), requireNotNull(base.text("name")), finish()["input"]!!.jsonObject)
}
