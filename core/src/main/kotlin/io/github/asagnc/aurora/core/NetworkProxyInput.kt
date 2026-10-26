package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Agent-facing header edits are an explicit array, avoiding open-ended map schemas across providers. */
fun networkProxyOptions(input: JsonObject): NetworkProxyOptions {
    require(input.keys.all { it in setOf("port", "rules", "durationSeconds", "maxFlows", "maxStoredBytes", "maxBodyBytes") })
    val rules = input["rules"]?.jsonArray ?: JsonArray(emptyList())
    require(rules.size <= 32)
    return NetworkProxyOptions(Math.toIntExact(input.proxyNumber("port", -1)), rules.map { networkProxyRule(it.jsonObject) },
        Math.toIntExact(input.proxyNumber("durationSeconds", 60)), Math.toIntExact(input.proxyNumber("maxFlows", 64)),
        input.proxyNumber("maxStoredBytes", 8L * 1024 * 1024), Math.toIntExact(input.proxyNumber("maxBodyBytes", 1024 * 1024))).also { it.validate() }
}

private fun networkProxyRule(input: JsonObject): NetworkProxyRule {
    require(input.keys.all { it in setOf("stage", "host", "pathPrefix", "method", "headers", "bodyBase64", "direction") })
    val edits = input["headers"]?.jsonArray ?: JsonArray(emptyList())
    require(edits.size <= 32)
    val headers = linkedMapOf<String, String?>()
    val names = mutableSetOf<String>()
    for (entry in edits) {
        val edit = entry.jsonObject
        require(edit.keys.all { it in setOf("name", "value", "remove") })
        val name = requireNotNull(edit.proxyString("name"))
        require(names.add(name.lowercase())) { "Duplicate proxy header edit" }
        val remove = edit["remove"]?.let {
            require(it is JsonPrimitive && !it.isString); requireNotNull(it.booleanOrNull)
        } ?: false
        if (remove) { require("value" !in edit); headers[name] = null }
        else headers[name] = requireNotNull(edit.proxyString("value"))
    }
    return NetworkProxyRule(requireNotNull(input.proxyString("stage")), requireNotNull(input.proxyString("host")),
        input.proxyString("pathPrefix") ?: "/", input.proxyString("method"), headers, input.proxyString("bodyBase64"), input.proxyString("direction"))
}

private fun JsonObject.proxyString(key: String): String? {
    val value = get(key) ?: return null
    if (value == JsonNull) return null
    require(value is JsonPrimitive && value.isString) { "Proxy string field has the wrong type" }
    return value.content
}

private fun JsonObject.proxyNumber(key: String, default: Long): Long {
    val value = get(key) ?: return default
    require(value is JsonPrimitive && !value.isString) { "Proxy numeric field has the wrong type" }
    return requireNotNull(value.longOrNull)
}
