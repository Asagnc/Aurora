package io.github.asagnc.aurora.runtime

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootPacketClient
import java.util.UUID
import kotlinx.serialization.json.*

internal class PacketInjectionTool(private val client: RootPacketClient, private val owner: String,
    private val prepared: suspend (String, String) -> Unit = { _, _ -> }) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("packet_inject_root",
        "Inject one explicitly supplied complete Ethernet frame using Root libpcap. interface and frameBase64 are required; " +
            "interface cannot be any, and the actual interface must expose Ethernet (DLT_EN10MB). Frame is 14..16384 bytes, " +
            "including link header, not a PCAP file, IP-only payload or HTTP session. packets explicitly repeats this frame (default 1, max 4096). " +
            "Defaults: packetsPerSecond 10 (max 1000), seconds 5 (max 30), bytes 1 MiB (max 16 MiB). " +
            "Whole frames only; no catch-up bursts. Stop on count/bytes/deadline/cancel or first send failure; partial writes are never retried. " +
            "Read injection.complete/reason/acceptedPackets/acceptedBytes; budget stops before all requested frames are errors with partial counts. " +
            "pcap acceptance is not delivery: drivers may reject or rewrite headers, and receivers may drop frames. " +
            "Root/SELinux/interface limitations fail explicitly. No TLS decryption, UID attribution, route changes or automatic replay of uncertain outcomes.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "interface" to objectOf("type" to string("string"), "maxLength" to JsonPrimitive(64)),
            "frameBase64" to objectOf("type" to string("string"), "maxLength" to JsonPrimitive(21848)),
            "packets" to integer(1, 4096), "packetsPerSecond" to integer(1, 1000), "seconds" to integer(1, 30), "bytes" to integer(14, 16777216)),
            "required" to JsonArray(listOf(string("interface"), string("frameBase64"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "packet_inject_root")
        val options = PacketInjectionRequest.parse(call.arguments)
        val id = UUID.randomUUID().toString()
        prepared(call.id, "runs/$id")
        return client.inject(owner, id, options)
    }
    private fun integer(low: Int, high: Int) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(low), "maximum" to JsonPrimitive(high))
}
