package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

/** Literal capture options; shared by the Agent and privileged Binder receiver. */
data class PacketCaptureRequest(val device: String, val seconds: Long, val bytes: Long,
    val packets: Long, val filter: String) {
    val timeoutMs get() = (seconds + 10) * 1000
    fun arguments() = objectOf("interface" to string(device), "seconds" to JsonPrimitive(seconds),
        "bytes" to JsonPrimitive(bytes), "packets" to JsonPrimitive(packets), "filter" to string(filter))
    fun argv(binary: String) = listOf(binary, device, seconds.toString(), bytes.toString(), packets.toString(), filter)

    companion object {
        fun parse(args: JsonObject): PacketCaptureRequest {
            require(args.keys.all { it in setOf("interface", "seconds", "bytes", "packets", "filter") })
            fun text(key: String, fallback: String): String = args[key]?.let {
                require(it is JsonPrimitive && it.isString); it.content
            } ?: fallback
            fun value(key: String, fallback: Long, range: LongRange): Long = (args[key]?.let {
                require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull)
            } ?: fallback).also { require(it in range) }
            val device = text("interface", "any").also { require(it.matches(Regex("[A-Za-z0-9_.:@-]{1,64}"))) }
            val filter = text("filter", "").also { require(it.toByteArray(Charsets.UTF_8).size <= 4096 && '\u0000' !in it) }
            return PacketCaptureRequest(device, value("seconds", 15, 1L..270),
                value("bytes", 16 * 1024 * 1024, 24L..536870912),
                value("packets", 100000, 1L..10000000), filter)
        }
    }
}

/** A valid log or PCAP alone cannot prove successful completion. */
fun packetCaptureCompletion(command: JsonObject, request: PacketCaptureRequest): JsonObject {
    require(command["error"] == null && command.number("exitCode") == 0L)
    require(command["timedOut"] == JsonPrimitive(false) && command["stopped"] == JsonPrimitive(false))
    require(command["captureComplete"] == JsonPrimitive(true))
    val output = requireNotNull(command["stdout"] as? JsonObject)
    require(output["previewTruncated"] == JsonPrimitive(false) && output["artifactTruncated"] == JsonPrimitive(false))
    val text = requireNotNull(output.text("text")).also { require(it.toByteArray().size <= 4096) }
    val capture = WireJson.parseToJsonElement(text).jsonObject
    require(capture.number("protocol") == 1L && capture["fileComplete"] == JsonPrimitive(true))
    val reason = capture.text("reason")
    require(reason in setOf("duration", "byte_budget", "packet_budget"))
    val packets = requireNotNull(capture.number("packets")).also { require(it in 0..request.packets) }
    val bytes = requireNotNull(capture.number("bytes")).also { require(it in 24..request.bytes) }
    require(bytes in (24 + packets * 16)..(24 + packets * 65551))
    if (reason == "packet_budget") require(packets == request.packets)
    for (key in listOf("device", "inode")) require(requireNotNull(capture.number(key)) >= 0)
    for (key in listOf("received", "dropped")) {
        val stat = requireNotNull(capture[key])
        require(stat == JsonNull || (stat is JsonPrimitive && !stat.isString && stat.longOrNull in 0..4294967295L))
    }
    return capture
}
