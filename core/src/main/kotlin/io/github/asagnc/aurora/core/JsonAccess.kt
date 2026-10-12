package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

val WireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
fun objectOf(vararg entries: Pair<String, JsonElement>): JsonObject = JsonObject(linkedMapOf(*entries))
fun string(value: String): JsonPrimitive = JsonPrimitive(value)
