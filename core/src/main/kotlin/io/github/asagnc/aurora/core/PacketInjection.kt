package io.github.asagnc.aurora.core

import java.util.Base64
import kotlinx.serialization.json.*

/** Immutable-by-convention decoded bytes are reparsed at the privileged Binder boundary. */
data class PacketInjectionRequest(val device: String, val frame: ByteArray, val seconds: Long,
    val rate: Long, val packets: Long, val bytes: Long, val additionalFrames: List<ByteArray> = emptyList()) {
    val frames get() = listOf(frame) + additionalFrames
    val timeoutMs get() = (seconds + 10) * 1000
    fun arguments() = JsonObject(objectOf("interface" to string(device),
        "seconds" to JsonPrimitive(seconds), "packetsPerSecond" to JsonPrimitive(rate), "packets" to JsonPrimitive(packets), "bytes" to JsonPrimitive(bytes)) +
        if (additionalFrames.isEmpty()) mapOf("frameBase64" to string(Base64.getEncoder().encodeToString(frame)))
        else mapOf("framesBase64" to JsonArray(frames.map { string(Base64.getEncoder().encodeToString(it)) })))
    fun argv(binary: String) = listOf(binary, device, seconds.toString(), rate.toString(), packets.toString(), bytes.toString()) +
        frames.map { data -> data.joinToString("") { "%02x".format(it) } }

    companion object {
        fun parse(args: JsonObject): PacketInjectionRequest = decode(args, false)
        fun parseWire(args: JsonObject): PacketInjectionRequest = decode(args, true)
        private fun decode(args: JsonObject, batch: Boolean): PacketInjectionRequest {
            val keys = setOf("interface", "frameBase64", "seconds", "packetsPerSecond", "packets", "bytes") + if (batch) setOf("framesBase64") else emptySet()
            require(args.keys.all { it in keys })
            val device = args.requiredText("interface")
            require(device != "any" && device.matches(Regex("[A-Za-z0-9_.:@-]{1,64}")))
            val multiple = args["framesBase64"]?.let { value ->
                require(value is JsonArray && value.size in 1..64 && "frameBase64" !in args)
                value.map { require(it is JsonPrimitive && it.isString); it.content }
            }
            val frames = (multiple ?: listOf(args.requiredText("frameBase64"))).map { encoded ->
                require(encoded.length <= 21848)
                Base64.getDecoder().decode(encoded).also { require(it.size in 14..16384) }
            }
            require(frames.sumOf { it.size } <= 16384)
            fun value(key: String, default: Long, range: LongRange): Long = (args[key]?.let {
                require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull)
            } ?: default).also { require(it in range) }
            val packets = value("packets", frames.size.toLong(), 1L..4096)
            require(multiple == null || packets == frames.size.toLong())
            return PacketInjectionRequest(device, frames.first(), value("seconds", 5, 1L..30),
                value("packetsPerSecond", 10, 1L..1000), packets,
                value("bytes", 1024 * 1024, frames.maxOf { it.size }.toLong()..16777216), frames.drop(1))
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
    val frames = request.frames
    require(number("protocol") == 2L && number("frameBytes") == frames.sumOf { it.size }.toLong())
    require(number("selectedFrames") == frames.size.toLong())
    require(number("requestedPackets") == request.packets)
    val packets = number("acceptedPackets").also { require(it in 0..request.packets) }
    val bytes = number("acceptedBytes").also { require(it in 0..request.bytes) }
    val nextFrame = frames[(packets % frames.size).toInt()].size.toLong()
    val partial = number("partialBytes").also { require(it in 0 until nextFrame) }
    val expectedBytes = packets / frames.size * frames.sumOf { it.size } + frames.take((packets % frames.size).toInt()).sumOf { it.size }
    require(bytes == expectedBytes + partial)
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
    if (reason == "byte_budget") require(request.bytes - bytes < nextFrame)
    return record
}
