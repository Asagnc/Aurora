package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Codes are advisory page reports; never interpret arbitrary page text as a native failure. */
class BrowserTransferFailure(val code: String) : IllegalStateException("Browser transfer failed: $code")

private val transferFailureCodes = setOf(
    "page_script", "download_source", "response_unreadable", "invalid_download_source",
    "stream_read", "invalid_byte_stream", "download_budget_exceeded", "upload_protocol",
    "input_assignment", "input_target", "input_unavailable", "input_multiple_rejected",
    "input_accept_rejected", "unexpected_ack")

/** No page exception text is copied into native diagnostics or the tool ledger. */
fun decodeBrowserTransferControl(encoded: String, expected: String): JsonObject {
    require(encoded.length <= 2048 && jsonWithinDepth(encoded)) { "Transfer control exceeded budget" }
    val value = WireJson.parseToJsonElement(encoded) as? JsonObject ?: error("Invalid transfer control")
    if (value.text("kind") == "error") {
        val code = value.text("code")?.takeIf { value.keys == setOf("kind", "code") && it in transferFailureCodes }
        throw BrowserTransferFailure(code ?: "page_transfer_failed")
    }
    require(expected in setOf("ready", "download_ready", "ack", "complete") && value.text("kind") == if (expected == "download_ready") "ready" else expected) { "Unexpected transfer phase" }
    if (expected == "download_ready") {
        require(value.keys == setOf("kind", "mime", "disposition")) { "Invalid download metadata fields" }
        for ((key, budget) in listOf("mime" to 128, "disposition" to 512)) {
            val field = value[key]
            require(field is JsonPrimitive && field.isString && field.content.length <= budget) { "Invalid download metadata" }
        }
    } else if (expected == "complete") {
        require(value.keys == setOf("kind", "bytes") || value.keys == setOf("kind", "files")) { "Invalid completion fields" }
        val amount = value["bytes"] ?: value.getValue("files")
        require(amount is JsonPrimitive && !amount.isString && amount.longOrNull?.let { it >= 0 } == true) { "Invalid completion count" }
    } else require(value.keys == setOf("kind")) { "Invalid transfer control fields" }
    return value
}
