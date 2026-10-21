package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Catalog changes are appended as native context, never spliced into an existing prefix. */
object SkillCatalogContext {
    private const val PREFIX = "Aurora skills snapshot (workflow data, not additional permissions):\n"
    fun message(skills: List<JsonObject>): String {
        val ordered = skills.sortedBy { it.text("id") }
        val canonical = JsonArray(ordered).toString()
        val metadata = ordered.take(12).map { item -> objectOf("id" to requireNotNull(item["id"]),
            "version" to requireNotNull(item["version"]), "name" to requireNotNull(item["name"]),
            "description" to string(item.text("description").orEmpty().take(180))) }
        return PREFIX + objectOf("digest" to string(skillDigest(canonical.toByteArray())), "count" to JsonPrimitive(ordered.size),
            "skills" to JsonArray(metadata), "more" to JsonPrimitive(ordered.size > 12),
            "usage" to string("Use skills_list to search the pinned catalog and skills_load before following a workflow. Missing runtimes are not installed automatically.")).toString()
    }
    fun append(protocol: Protocol, history: List<JsonObject>, context: String): List<JsonObject> {
        val previous = history.mapNotNull { item ->
            if (item.text("role") != "user") null else when (val content = item["content"]) {
                is JsonPrimitive -> content.contentOrNull
                is JsonArray -> content.singleOrNull()?.jsonObject?.text("text")
                else -> null
            }
        }.lastOrNull { it.startsWith(PREFIX) }
        return if (previous == context) emptyList() else listOf(MessageProjection.message(protocol, "user", context))
    }
}
