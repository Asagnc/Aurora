package io.github.asagnc.aurora.core

import kotlinx.serialization.json.JsonArray

fun requestFingerprint(system: String, tools: List<ToolDefinition>): String = OwnedFiles.digest(
    objectOf("system" to string(system), "tools" to JsonArray(tools.map { tool -> objectOf(
        "name" to string(tool.name), "description" to string(tool.description), "schema" to tool.schema
    ) })).toString().toByteArray(Charsets.UTF_8))
