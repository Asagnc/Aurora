package io.github.asagnc.aurora.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Capture-specific bounds; ordinary commands and Frida keep their existing limits. */
@Serializable data class CaptureRotationOptions(val device: String = "any", val filter: String = "",
    val seconds: Long = 300, val segmentBytes: Long = 16 * 1024 * 1024, val bytes: Long = 64 * 1024 * 1024,
    val segments: Int = 64, val packets: Long = 10_000_000) {
    fun validate() {
        require(device.matches(Regex("[A-Za-z0-9_.:@-]{1,64}")))
        val encoded = filter.toByteArray(Charsets.UTF_8)
        require('\u0000' !in filter && encoded.size <= 4096 && encoded.toString(Charsets.UTF_8) == filter)
        require(seconds in 1..1800 && segmentBytes in 40..67_108_864 && bytes in segmentBytes..536_870_912)
        require(segments in 1..64 && packets in 1..10_000_000)
    }

    companion object {
        const val SNAPSHOT_BYTES = 262144
        const val MAX_MANIFEST_BYTES = 65536
        fun parse(args: JsonObject): CaptureRotationOptions {
            require(args.keys.all { it in setOf("interface", "filter", "seconds", "segmentBytes", "bytes", "segments", "packets") })
            fun number(key: String, fallback: Long): Long = args[key]?.let {
                require(it is JsonPrimitive && !it.isString); requireNotNull(it.longOrNull)
            } ?: fallback
            return CaptureRotationOptions(args.optionalText("interface") ?: "any", args.optionalText("filter").orEmpty(),
                number("seconds", 300), number("segmentBytes", 16 * 1024 * 1024), number("bytes", 64 * 1024 * 1024),
                Math.toIntExact(number("segments", 64)), number("packets", 10_000_000)).also { it.validate() }
        }
    }
}

@Serializable data class CaptureRunBinding(val runId: String, val owner: String, val identity: LinuxRootIdentity) {
    fun validate() {
        require(UUID.fromString(runId).toString() == runId && UUID.fromString(owner).toString() == owner)
        identity.validate()
    }
}

/** Opaque sealed configuration, distinct from Frida's UTF-8 JavaScript. Ticket digest is supplied by the guard, avoiding a digest cycle. */
data class RootCaptureConfiguration(val binding: CaptureRunBinding, val options: CaptureRotationOptions) {
    fun encode(): ByteArray {
        binding.validate(); options.validate()
        val device = options.device.toByteArray(Charsets.US_ASCII); val filter = options.filter.toByteArray(Charsets.UTF_8)
        val out = ByteBuffer.allocate(HEADER_BYTES + device.size + filter.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(MAGIC).putInt(1).putInt(HEADER_BYTES).putInt(options.seconds.toInt())
            .putLong(options.segmentBytes).putLong(options.bytes).putLong(options.packets).putInt(options.segments)
            .putInt(CaptureRotationOptions.SNAPSHOT_BYTES).putInt(binding.identity.pid).putLong(binding.identity.startTicks)
        for (value in listOf(binding.runId, binding.owner, binding.identity.bootId, binding.identity.epoch)) out.put(value.toByteArray(Charsets.US_ASCII))
        out.putInt(device.size).putInt(filter.size).put(device).put(filter)
        check(!out.hasRemaining())
        return out.array()
    }

    fun snapshot(): RootScriptSnapshot {
        val data = encode()
        val digest = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
        return RootScriptSnapshot("capture-rotation-v1", digest, data.size.toLong())
    }

    companion object {
        const val HEADER_BYTES = 212
        private val MAGIC = byteArrayOf(65, 85, 82, 67) // AURC; fixed little-endian header followed by exact UTF-8 bytes.
        fun decode(data: ByteArray): RootCaptureConfiguration {
            require(data.size in HEADER_BYTES + 1..HEADER_BYTES + 64 + 4096)
            val input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            require(ByteArray(4).also(input::get).contentEquals(MAGIC) && input.int == 1 && input.int == HEADER_BYTES)
            val seconds = input.int.toLong(); val segment = input.long; val bytes = input.long; val packets = input.long; val segments = input.int
            require(input.int == CaptureRotationOptions.SNAPSHOT_BYTES)
            val pid = input.int; val ticks = input.long
            fun uuid() = ByteArray(36).also(input::get).toString(Charsets.US_ASCII)
            val run = uuid(); val owner = uuid(); val boot = uuid(); val epoch = uuid()
            val deviceBytes = input.int; val filterBytes = input.int
            require(deviceBytes in 1..64 && filterBytes in 0..4096 && input.remaining() == deviceBytes + filterBytes)
            val device = ByteArray(deviceBytes).also(input::get)
            require(device.all { it in 0..127 })
            val filter = ByteArray(filterBytes).also(input::get)
            val options = CaptureRotationOptions(device.toString(Charsets.US_ASCII), filter.toString(Charsets.UTF_8), seconds, segment, bytes, segments, packets)
            require(options.filter.toByteArray(Charsets.UTF_8).contentEquals(filter))
            return RootCaptureConfiguration(CaptureRunBinding(run, owner, LinuxRootIdentity(pid, ticks, boot, epoch)), options).also {
                it.binding.validate(); it.options.validate()
            }
        }
    }
}

/** Raw libpcap cumulative counters. A backwards value is unknown, not an inferred wraparound. */
@Serializable data class CaptureKernelCounters(val received: Long? = null, val dropped: Long? = null, val interfaceDropped: Long? = null) {
    fun validate() { for (value in listOf(received, dropped, interfaceDropped)) require(value == null || value in 0..0xffffffffL) }
    fun since(before: CaptureKernelCounters): CaptureKernelCounters {
        validate(); before.validate()
        fun delta(old: Long?, next: Long?) = if (old != null && next != null && next >= old) next - old else null
        return CaptureKernelCounters(delta(before.received, received), delta(before.dropped, dropped), delta(before.interfaceDropped, interfaceDropped))
    }
}
