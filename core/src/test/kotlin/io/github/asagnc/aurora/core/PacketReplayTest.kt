package io.github.asagnc.aurora.core

import java.util.Base64
import kotlinx.serialization.json.*
import kotlin.test.*

class PacketReplayTest {
    private val frames = listOf(ByteArray(60) { it.toByte() }, ByteArray(80) { (it + 1).toByte() })
    private fun args() = objectOf("path" to string("capture.pcap"), "sourceSha256" to string("a".repeat(64)),
        "interface" to string("owned0"), "firstPacket" to JsonPrimitive(1), "count" to JsonPrimitive(2))
    private fun record() = objectOf("protocol" to JsonPrimitive(1), "linkType" to JsonPrimitive(1), "sourceBytes" to JsonPrimitive(1000),
        "firstPacket" to JsonPrimitive(1), "selectedBytes" to JsonPrimitive(140),
        "frames" to JsonArray(frames.map { bytes -> string(bytes.joinToString("") { "%02x".format(it) }) }))

    @Test fun exactSelectionProducesOneOrderedBoundedRootBatch() {
        val request = PacketReplayRequest.parse(args())
        val selected = packetReplaySelection(record(), request, 1000)
        val received = PacketInjectionRequest.parseWire(selected.arguments())
        assertEquals(2L, received.packets); assertEquals(2, received.frames.size)
        frames.zip(received.frames).forEach { (expected, actual) -> assertContentEquals(expected, actual) }
        assertFailsWith<IllegalArgumentException> { PacketInjectionRequest.parse(selected.arguments()) }
        assertEquals(8, selected.argv("/owned/worker").size)
    }

    @Test fun missingMismatchedTruncatedAndOversizedSelectionsCannotReachRootBatch() {
        val request = PacketReplayRequest.parse(args())
        val invalid = listOf(record() + ("linkType" to JsonPrimitive(113)), record() + ("sourceBytes" to JsonPrimitive(999)),
            record() + ("firstPacket" to JsonPrimitive(0)), record() + ("selectedBytes" to JsonPrimitive(139)),
            record() + ("frames" to JsonArray(listOf(string("00")))), record() + ("frames" to JsonArray(listOf(string("gg".repeat(60)), string("00".repeat(80))))),
            record() + ("error" to string("selection_incomplete")))
        invalid.forEach { assertFailsWith<IllegalArgumentException> { packetReplaySelection(JsonObject(it), request, 1000) } }
        val limited = PacketReplayRequest.parse(JsonObject(args() + ("bytes" to JsonPrimitive(139))))
        assertFailsWith<IllegalArgumentException> { packetReplaySelection(record(), limited, 1000) }
        assertFailsWith<IllegalArgumentException> { packetReplayRecord("[".repeat(30) + "0" + "]".repeat(30)) }
    }

    @Test fun invalidSelectorsAndImplicitBatchRepeatAreRejected() {
        for (value in listOf(args() + ("sourceSha256" to string("unknown")), args() + ("count" to JsonPrimitive(65)),
            args() + ("firstPacket" to string("1")), args() + ("shell" to string("ignored")))) {
            assertFailsWith<IllegalArgumentException> { PacketReplayRequest.parse(JsonObject(value)) }
        }
        val selected = packetReplaySelection(record(), PacketReplayRequest.parse(args()), 1000)
        assertFailsWith<IllegalArgumentException> { PacketInjectionRequest.parseWire(JsonObject(selected.arguments() + ("packets" to JsonPrimitive(3)))) }
        val excessive = JsonArray(List(2) { string(Base64.getEncoder().encodeToString(ByteArray(10000))) })
        assertFailsWith<IllegalArgumentException> { PacketInjectionRequest.parseWire(JsonObject(selected.arguments() + ("framesBase64" to excessive))) }
    }

    @Test fun varyingFrameLengthsUseExactPrefixAndCurrentPartialLength() {
        val request = packetReplaySelection(record(), PacketReplayRequest.parse(args()), 1000)
        val partial = objectOf("protocol" to JsonPrimitive(2), "selectedFrames" to JsonPrimitive(2), "reason" to string("partial_write"),
            "frameBytes" to JsonPrimitive(140), "requestedPackets" to JsonPrimitive(2), "acceptedPackets" to JsonPrimitive(1),
            "acceptedBytes" to JsonPrimitive(130), "partialBytes" to JsonPrimitive(70), "systemErrno" to JsonPrimitive(0), "complete" to JsonPrimitive(false))
        fun command(value: JsonObject) = objectOf("error" to string("command_exit"), "exitCode" to JsonPrimitive(1),
            "timedOut" to JsonPrimitive(false), "stopped" to JsonPrimitive(false), "captureComplete" to JsonPrimitive(true),
            "stdout" to objectOf("text" to string(value.toString()), "previewTruncated" to JsonPrimitive(false), "artifactTruncated" to JsonPrimitive(false)))
        assertEquals(partial, packetInjectionCompletion(command(partial), request))
        assertFailsWith<IllegalArgumentException> { packetInjectionCompletion(command(JsonObject(partial + ("acceptedBytes" to JsonPrimitive(150)))), request) }
    }
}
