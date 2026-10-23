package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Decode only bounded envelopes. Page data remains untrusted even when this succeeds. */
fun decodeBrowserScriptResult(encoded: String): JsonObject {
    require(encoded.length <= 40000 && jsonWithinDepth(encoded)) { "Renderer result exceeded budget" }
    val result = WireJson.parseToJsonElement(encoded) as? JsonObject ?: error("Renderer returned no result object")
    check(result["ok"] == JsonPrimitive(true)) { result.text("error")?.take(500) ?: "Page script failed" }
    require(result.keys == setOf("ok", "value", "truncated")) { "Invalid renderer result fields" }
    val truncated = result.getValue("truncated")
    require(truncated == JsonPrimitive(true) || truncated == JsonPrimitive(false)) { "Invalid renderer truncation flag" }
    if (truncated == JsonPrimitive(true)) {
        val value = result.getValue("value")
        require(value is JsonPrimitive && value.isString) { "Truncated result is not a text preview" }
    }
    return result
}

fun decodeBrowserJavascriptCallback(raw: String): JsonObject {
    require(raw.length <= 80002 && jsonWithinDepth(raw)) { "Renderer callback exceeded budget" }
    val value = WireJson.parseToJsonElement(raw)
    require(value is JsonPrimitive && value.isString) { "Renderer returned no serialized result" }
    return decodeBrowserScriptResult(value.content)
}
