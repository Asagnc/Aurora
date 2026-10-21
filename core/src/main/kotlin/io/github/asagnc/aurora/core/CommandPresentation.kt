package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** A small, valid JSON projection; cutting a large JSON string would hide exit/error metadata. */
fun compactCommandResult(result: String): String? {
    if (result.length > 2 * 1024 * 1024 || !jsonWithinDepth(result)) return null
    val record = runCatching { WireJson.parseToJsonElement(result) as? JsonObject }.getOrNull() ?: return null
    val fields = listOf("error", "exitCode", "backend", "uid", "timedOut", "stopped", "captureComplete")
    val compact = linkedMapOf<String, JsonElement>()
    fields.forEach { name -> record[name]?.let { compact[name] = it } }
    for (name in listOf("stdout", "stderr")) {
        val output = record[name] as? JsonObject ?: continue
        val projected = output.filterKeys { it in setOf("text", "bytes", "storedBytes", "previewTruncated", "artifactTruncated", "artifact", "encoding") }.toMutableMap()
        val text = (output["text"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        if (text != null && text.length > 512) {
            val preview = text.take(512).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }
            projected["text"] = JsonPrimitive(preview)
            projected["previewTruncated"] = JsonPrimitive(true)
        }
        compact[name] = JsonObject(projected)
    }
    return JsonObject(compact).toString().takeIf { it.length <= 8000 }
}
