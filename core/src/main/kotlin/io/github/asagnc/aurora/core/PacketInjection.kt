package io.github.asagnc.aurora.core

import java.util.Base64
import kotlinx.serialization.json.*

/** Immutable-by-convention decoded bytes are reparsed at the privileged Binder boundary. */
data class PacketInjectionRequest(val device: String, val frame: ByteArray, val seconds: Long,
    val rate: Long, val packets: Long, val bytes: Long) {
    val timeoutMs get() = (seconds + 10) * 1000
    fun arguments() = objectOf("interface" to string(device), "frameBase64" to string(Base64.getEncoder().encodeToString(frame)),
        "seconds" to JsonPrimitive(seconds), "packetsPerSecond" to JsonPrimitive(rate), "packets" to JsonPrimitive(packets), "bytes" to JsonPrimitive(bytes))
    fun argv(binary: String) = listOf(binary, device, seconds.toString(), rate.toString(), packets.toString(), bytes.toString(),
        frame.joinToString("") { "%02x".format(it) })

    companion object {
        fun parse(args: JsonObject): PacketInjectionRequest {
            require(args.keys.all { it in setOf("interface", "frameBase64", "seconds", "packetsPerSecond", "packets", "bytes") })
            val device = args.requiredText("interface")
            require(device != "any" && device.matches(Regex("[A-Za-z0-9_.:@-]{1,64}")))
            val encoded = args.requiredText("frameBase64"); require(encoded.length <= 21848)
            val frame = Base64.getDecoder().decode(encoded).also { require(it.size in 14..16384) }
            fun value(key: String, default: Long, range: LongRange): Long = (args[key]?.let {
                require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull)
            } ?: default).also { require(it in range) }
            return PacketInjectionRequest(device, frame, value("seconds", 5, 1L..30),
                value("packetsPerSecond", 10, 1L..1000), value("packets", 1, 1L..4096),
                value("bytes", 1024 * 1024, frame.size.toLong()..16777216))
        }
    }
}

/** Accepted by pcap is not a delivery proof. Partial writes and deadline stops cannot become success. */
fun packetInjectionCompletion(command: JsonObject, request: PacketInjectionRequest): JsonObject {
    require(command["timedOut"] == JsonPrimitive(false) && command["stopped"] == JsonPrimitive(false))
    require(command["captureComplete"] == JsonPrimitive(true))
    val stdout = requireNotNull(command["stdout"] as? JsonObject)
    require(stdout["previewTruncated"] == JsonPrimitive(false) && stdout["artifactTruncated"] == JsonPrimitive(false))
    val text = requireNotNull(stdout.text("text")).also { require(it.toByteArray().size <= 4096) }
    val record = WireJson.parseToJsonElement(text).jsonObject
    fun number(key: String): Long {
        val value = record[key]; require(value is JsonPrimitive && !value.isString)
        return requireNotNull(value.longOrNull)
    }
    require(number("protocol") == 1L && number("frameBytes") == request.frame.size.toLong())
    require(number("requestedPackets") == request.packets)
    val packets = number("acceptedPackets").also { require(it in 0..request.packets) }
    val bytes = number("acceptedBytes").also { require(it in 0..request.bytes) }
    val partial = number("partialBytes").also { require(it in 0 until request.frame.size.toLong()) }
    require(bytes == packets * request.frame.size + partial)
    require(number("systemErrno") in 0..4095)
    val reason = record.text("reason")
    val expectedExit = when (reason) {
        "packet_budget" -> 0L
        "duration", "byte_budget" -> 3L
        "cancelled" -> 2L
        "setup_error", "poll_error", "timer_error", "inject_error", "partial_write" -> 1L
        else -> error("Unknown injection outcome")
    }
    require(command.number("exitCode") == expectedExit)
    val complete = expectedExit == 0L
    require(record["complete"] == JsonPrimitive(complete))
    if (complete) require(packets == request.packets && partial == 0L && command["error"] == null)
    else require(packets < request.packets && command["error"] != null)
    if (reason != "partial_write") require(partial == 0L)
    if (reason == "byte_budget") require(request.bytes - bytes < request.frame.size)
    return record
}
