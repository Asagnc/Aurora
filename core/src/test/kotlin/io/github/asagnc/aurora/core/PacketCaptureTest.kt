package io.github.asagnc.aurora.core

import kotlin.test.*
import kotlinx.serialization.json.*

class PacketCaptureTest {
    private val request = PacketCaptureRequest.parse(objectOf())
    private fun completed(capture: JsonObject = capture()) = objectOf("exitCode" to JsonPrimitive(0),
        "timedOut" to JsonPrimitive(false), "stopped" to JsonPrimitive(false), "captureComplete" to JsonPrimitive(true),
        "stdout" to objectOf("text" to string(capture.toString()), "previewTruncated" to JsonPrimitive(false),
            "artifactTruncated" to JsonPrimitive(false)))
    private fun capture() = objectOf("protocol" to JsonPrimitive(1), "reason" to string("duration"),
        "snapshotTruncated" to JsonPrimitive(0), "fileComplete" to JsonPrimitive(true), "packets" to JsonPrimitive(1), "bytes" to JsonPrimitive(82),
        "device" to JsonPrimitive(3), "inode" to JsonPrimitive(123), "received" to JsonNull, "dropped" to JsonNull)

    @Test fun defaultsRoundTripWithoutShellExpansion() {
        assertEquals(request, PacketCaptureRequest.parse(request.arguments()))
        assertEquals(25000L, request.timeoutMs)
        val filter = "udp and port 53; echo injected"
        val custom = PacketCaptureRequest.parse(objectOf("filter" to string(filter)))
        assertEquals(filter, custom.argv("/native/worker").last())
        assertEquals(6, custom.argv("/native/worker").size)
    }

    @Test fun requestRejectsUnknownKeysTypesTraversalAndOversizeUtf8() {
        val bad = listOf(objectOf("output" to string("/outside")), objectOf("seconds" to string("5")),
            objectOf("seconds" to JsonPrimitive(0)), objectOf("seconds" to JsonPrimitive(271)),
            objectOf("bytes" to JsonPrimitive(23)), objectOf("packets" to JsonPrimitive(0)),
            objectOf("interface" to string("../wlan0")), objectOf("filter" to string("\u0000")),
            objectOf("filter" to string("极".repeat(1366))))
        bad.forEach { assertFailsWith<IllegalArgumentException> { PacketCaptureRequest.parse(it) } }
    }

    @Test fun unknownKernelStatsRemainUnknown() {
        val result = packetCaptureCompletion(completed(), request)
        assertEquals(JsonNull, result["received"])
        assertEquals(JsonNull, result["dropped"])
        assertEquals(1L, result.number("packets"))
        assertTrue(requireNotNull(summarizeToolResult("""{"error":"capture_outcome_uncertain"}""")).uncertain)
    }

    @Test fun timeoutCancelOrMissingSupervisorProofCannotSucceed() {
        val invalid = listOf("error" to string("command_timeout"), "timedOut" to JsonPrimitive(true),
            "stopped" to JsonPrimitive(true), "exitCode" to JsonPrimitive(2), "captureComplete" to JsonPrimitive(false))
        invalid.forEach { pair -> assertFails { packetCaptureCompletion(JsonObject(completed() + pair), request) } }
    }

    @Test fun malformedOrImpossibleWorkerResultCannotSucceed() {
        val invalid = listOf("reason" to string("cancelled"), "fileComplete" to JsonPrimitive(false),
            "bytes" to JsonPrimitive(24), "packets" to JsonPrimitive(10000001), "inode" to JsonPrimitive(-1),
            "received" to JsonPrimitive(-1), "snapshotTruncated" to JsonPrimitive(2),
            "reason" to string("byte_budget"), "dropped" to string("unknown"), "reason" to string("packet_budget"))
        invalid.forEach { pair -> assertFails { packetCaptureCompletion(completed(JsonObject(capture() + pair)), request) } }
    }

    @Test fun clippedOrMissingWorkerMetadataCannotSucceed() {
        val stdout = completed().getValue("stdout").jsonObject
        for (key in listOf("previewTruncated", "artifactTruncated")) {
            assertFails { packetCaptureCompletion(JsonObject(completed() + ("stdout" to JsonObject(stdout + (key to JsonPrimitive(true))))), request) }
        }
        assertFails { packetCaptureCompletion(completed(JsonObject(capture() - "fileComplete")), request) }
    }
}
