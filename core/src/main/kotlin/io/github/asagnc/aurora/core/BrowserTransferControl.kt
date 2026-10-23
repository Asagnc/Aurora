package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** No page exception text is copied into native diagnostics or the tool ledger. */
fun decodeBrowserTransferControl(encoded: String, expected: String): JsonObject {
    require(encoded.length <= 2048 && jsonWithinDepth(encoded)) { "Transfer control exceeded budget" }
    val value = WireJson.parseToJsonElement(encoded) as? JsonObject ?: error("Invalid transfer control")
    check(value.text("kind") != "error") { "Page transfer failed; inspect before retrying" }
    require(expected in setOf("ready", "ack", "complete") && value.text("kind") == expected) { "Unexpected transfer phase" }
    if (expected == "complete") {
        require(value.keys == setOf("kind", "bytes") || value.keys == setOf("kind", "files")) { "Invalid completion fields" }
        val amount = value["bytes"] ?: value.getValue("files")
        require(amount is JsonPrimitive && !amount.isString && amount.longOrNull?.let { it >= 0 } == true) { "Invalid completion count" }
    } else require(value.keys == setOf("kind")) { "Invalid transfer control fields" }
    return value
}
