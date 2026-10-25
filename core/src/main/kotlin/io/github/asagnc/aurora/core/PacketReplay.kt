package io.github.asagnc.aurora.core

import java.util.Base64
import kotlinx.serialization.json.*

data class PacketReplayRequest(val path: String, val sourceSha256: String, val firstPacket: Long,
    val count: Int, val injection: PacketInjectionRequest) {
    companion object {
        fun parse(args: JsonObject): PacketReplayRequest {
            require(args.keys.all { it in setOf("path", "sourceSha256", "interface", "firstPacket", "count", "seconds", "packetsPerSecond", "bytes") })
            val path = args.requiredText("path").also { require(it.isNotBlank() && it.length <= 4096) }
            val sha = args.requiredText("sourceSha256").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            fun number(key: String, fallback: Long, range: LongRange) = (args[key]?.let {
                require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull)
            } ?: fallback).also { require(it in range) }
            val count = number("count", 1, 1L..64).toInt()
            val budget = number("bytes", 16384, (count * 14L)..16384)
            val options = PacketInjectionRequest.parse(objectOf("interface" to string(args.requiredText("interface")),
                "frameBase64" to string(Base64.getEncoder().encodeToString(ByteArray(14))), "packets" to JsonPrimitive(count),
                "seconds" to JsonPrimitive(number("seconds", 15, 1L..30)),
                "packetsPerSecond" to JsonPrimitive(number("packetsPerSecond", 10, 1L..1000)), "bytes" to JsonPrimitive(budget)))
            return PacketReplayRequest(path, sha, number("firstPacket", 0, 0L..1000000), count, options)
        }
    }
}

/** Bound the worker channel before decoding either a selected slice or its rejection. */
fun packetReplayRecord(text: String): JsonObject {
    require(text.toByteArray(Charsets.UTF_8).size <= 65536 && jsonWithinDepth(text, 4))
    return WireJson.parseToJsonElement(text).jsonObject
}

/** Only a complete, bounded, exact selection can reach the privileged sender. */
fun packetReplaySelection(record: JsonObject, request: PacketReplayRequest, sourceBytes: Int): PacketInjectionRequest {
    fun number(key: String): Long {
        val value = record[key]; require(value is JsonPrimitive && !value.isString)
        return requireNotNull(value.longOrNull)
    }
    require(record["error"] == null && number("protocol") == 1L && number("linkType") == 1L)
    require(number("sourceBytes") == sourceBytes.toLong() && number("firstPacket") == request.firstPacket)
    val selected = requireNotNull(record["frames"] as? JsonArray)
    require(selected.size == request.count)
    val frames = selected.map { item ->
        require(item is JsonPrimitive && item.isString)
        val value = item.content
        require(value.length in 28..32768 && value.length % 2 == 0 && value.all { it in '0'..'9' || it in 'a'..'f' })
        ByteArray(value.length / 2) { index -> (value[index * 2].digitToInt(16) * 16 + value[index * 2 + 1].digitToInt(16)).toByte() }
    }
    val bytes = frames.sumOf { it.size }
    require(bytes <= request.injection.bytes && bytes <= 16384 && number("selectedBytes") == bytes.toLong())
    val options = request.injection.copy(frame = frames.first(), additionalFrames = frames.drop(1))
    return PacketInjectionRequest.parseWire(options.arguments())
}
