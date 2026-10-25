package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

data class CommandOutputSummary(val text: String?, val artifact: String?, val truncated: Boolean,
    val totalBytes: Long? = null, val storedBytes: Long? = null, val artifactClipped: Boolean = false)
data class ToolResultSummary(val error: String?, val exitCode: Long?, val uncertain: Boolean,
    val stdout: CommandOutputSummary?, val stderr: CommandOutputSummary?)

/** Presentation only: never substitute this bounded projection for the original tool result. */
fun summarizeToolResult(result: String?): ToolResultSummary? {
    if (result == null || result.length > 8000 || !jsonWithinDepth(result)) return null
    val record = runCatching { WireJson.parseToJsonElement(result) as? JsonObject }.getOrNull() ?: return null
    val error = (record["error"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    return ToolResultSummary(error, (record["exitCode"] as? JsonPrimitive)?.longOrNull,
        error in setOf("command_outcome_uncertain", "capture_outcome_uncertain", "http_outcome_uncertain"), outputSummary(record["stdout"]), outputSummary(record["stderr"]))
}

private fun outputSummary(value: JsonElement?): CommandOutputSummary? {
    val output = value as? JsonObject ?: return null
    val text = (output["text"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    val artifact = (output["artifact"] as? JsonPrimitive)?.content?.takeIf { path ->
        path.length <= 512 && path.startsWith("runs/") && '\\' !in path &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }
    return CommandOutputSummary(text, artifact, (output["previewTruncated"] as? JsonPrimitive)?.booleanOrNull == true,
        (output["bytes"] as? JsonPrimitive)?.longOrNull, (output["storedBytes"] as? JsonPrimitive)?.longOrNull,
        (output["artifactTruncated"] as? JsonPrimitive)?.booleanOrNull == true)
}
