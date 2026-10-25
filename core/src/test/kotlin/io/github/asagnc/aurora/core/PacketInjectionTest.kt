package io.github.asagnc.aurora.core

import java.util.Base64
import kotlinx.serialization.json.*
import kotlin.test.*

class PacketInjectionTest {
    private val frame = ByteArray(60) { it.toByte() }
    private fun args() = objectOf("interface" to string("owned0"), "frameBase64" to string(Base64.getEncoder().encodeToString(frame)),
        "packets" to JsonPrimitive(2))
    private fun record() = objectOf("protocol" to JsonPrimitive(2), "selectedFrames" to JsonPrimitive(1), "reason" to string("packet_budget"), "frameBytes" to JsonPrimitive(60),
        "requestedPackets" to JsonPrimitive(2), "acceptedPackets" to JsonPrimitive(2), "acceptedBytes" to JsonPrimitive(120),
        "partialBytes" to JsonPrimitive(0), "systemErrno" to JsonPrimitive(0), "complete" to JsonPrimitive(true))
    private fun command(record: JsonObject = record(), code: Long = 0): JsonObject = JsonObject(linkedMapOf<String, JsonElement>(
        "exitCode" to JsonPrimitive(code), "timedOut" to JsonPrimitive(false), "stopped" to JsonPrimitive(false), "captureComplete" to JsonPrimitive(true),
        "stdout" to objectOf("text" to string(record.toString()), "previewTruncated" to JsonPrimitive(false), "artifactTruncated" to JsonPrimitive(false))) +
        if (code != 0L) mapOf("error" to string("command_exit")) else emptyMap())

    @Test fun explicitLiteralInterfaceFrameAndBudgetsRoundTripAtBinderBoundary() {
        val request = PacketInjectionRequest.parse(args())
        assertContentEquals(frame, PacketInjectionRequest.parse(request.arguments()).frame)
        assertEquals("owned0", request.argv("/owned/worker")[1])
        assertEquals(frame.joinToString("") { "%02x".format(it) }, request.argv("/owned/worker").last())
        assertEquals(15000L, request.timeoutMs)
    }

    @Test fun malformedFramesImplicitInterfacesAndUnboundedValuesAreRejected() {
        val invalid = listOf(args() - "interface", args() + ("interface" to string("any")), args() + ("interface" to string("lo;id")),
            args() + ("frameBase64" to string("AA==")), args() + ("frameBase64" to string("not-base64!")),
            args() + ("frameBase64" to string(Base64.getEncoder().encodeToString(ByteArray(16385)))),
            args() + ("packets" to string("2")), args() + ("packets" to JsonPrimitive(4097)),
            args() + ("seconds" to JsonPrimitive(31)), args() + ("packetsPerSecond" to JsonPrimitive(0)),
            args() + ("bytes" to JsonPrimitive(59)), args() + ("shell" to string("ignored")))
        for (value in invalid) assertFailsWith<IllegalArgumentException> { PacketInjectionRequest.parse(JsonObject(value)) }
    }

    @Test fun successRequiresRealCompleteCaptureAndConsistentWholeFrameCounts() {
        val request = PacketInjectionRequest.parse(args())
        assertEquals(record(), packetInjectionCompletion(command(), request))
        for (value in listOf(record() + ("acceptedBytes" to JsonPrimitive(121)), record() + ("acceptedPackets" to JsonPrimitive(1)),
            record() + ("frameBytes" to JsonPrimitive(61)), record() + ("requestedPackets" to JsonPrimitive(3)),
            record() + ("complete" to string("true")), record() + ("partialBytes" to string("0")))) {
            assertFailsWith<IllegalArgumentException> { packetInjectionCompletion(command(JsonObject(value)), request) }
        }
        for (key in listOf("timedOut", "stopped")) assertFailsWith<IllegalArgumentException> {
            packetInjectionCompletion(JsonObject(command() + (key to JsonPrimitive(true))), request)
        }
        assertFailsWith<IllegalArgumentException> { packetInjectionCompletion(JsonObject(command() + ("captureComplete" to JsonPrimitive(false))), request) }
        assertFailsWith<IllegalArgumentException> { packetInjectionCompletion(JsonObject(command() + ("error" to string("command_outcome_uncertain"))), request) }
    }

    @Test fun partialWritesRemainFailuresAndBudgetStopCannotClaimAllFrames() {
        val request = PacketInjectionRequest.parse(args())
        val partial = JsonObject(record() + mapOf("reason" to string("partial_write"), "acceptedPackets" to JsonPrimitive(1),
            "acceptedBytes" to JsonPrimitive(65), "partialBytes" to JsonPrimitive(5), "complete" to JsonPrimitive(false)))
        assertEquals(partial, packetInjectionCompletion(command(partial, 1), request))
        assertFailsWith<IllegalArgumentException> { packetInjectionCompletion(command(partial, 0), request) }
        val budgetRequest = PacketInjectionRequest.parse(JsonObject(args() + ("bytes" to JsonPrimitive(119))))
        val budget = JsonObject(record() + mapOf("reason" to string("byte_budget"), "acceptedPackets" to JsonPrimitive(1),
            "acceptedBytes" to JsonPrimitive(60), "complete" to JsonPrimitive(false)))
        assertEquals(budget, packetInjectionCompletion(command(budget, 3), budgetRequest))
        assertFailsWith<IllegalArgumentException> { packetInjectionCompletion(command(budget, 3), request) }
    }
}
