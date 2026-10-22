package io.github.asagnc.aurora.runtime

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootPacketClient
import java.util.UUID
import kotlinx.serialization.json.*

internal class PacketCaptureTool(private val client: RootPacketClient, private val owner: String,
    private val prepared: suspend (String, String) -> Unit = { _, _ -> }) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("packet_capture_root",
        "Capture real network packets using Aurora's bounded libpcap Root worker. Root only, no VPN. " +
            "interface defaults to any; filter is a literal BPF expression. Prefer numeric addresses/ports: named BPF hosts/services can trigger resolution during compilation. Defaults: 15 seconds, 16 MiB, 100000 packets. " +
            "Stop on the first duration/whole-packet byte/packet-count budget; bytes include the PCAP headers. " +
            "Returns a conversation-relative capture.path for packet_inspect and final stored/received/dropped counts. " +
            "snapshotTruncated counts stored packets shorter than their reported original length; a valid record alone does not guarantee full packet bytes. " +
            "Received and dropped are kernel statistics and may be unknown; they are not the stored packet count. " +
            "Cancellation, hard timeout, service death and publication failure are not successful captures. " +
            "No TLS decryption, UID attribution or packet injection. Interfaces without event-driven capture support fail explicitly. " +
            "This does not contain privileged commands in a security sandbox; platform/SELinux restrictions still apply.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "interface" to objectOf("type" to string("string"), "maxLength" to JsonPrimitive(64)),
            "filter" to objectOf("type" to string("string"), "maxLength" to JsonPrimitive(4096)),
            "seconds" to integer(1, 270), "bytes" to integer(24, 536870912), "packets" to integer(1, 10000000)),
            "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "packet_capture_root")
        val request = PacketCaptureRequest.parse(call.arguments)
        val id = UUID.randomUUID().toString()
        prepared(call.id, "runs/$id")
        return client.execute(owner, id, request)
    }
    private fun integer(low: Long, high: Long) = objectOf("type" to string("integer"),
        "minimum" to JsonPrimitive(low), "maximum" to JsonPrimitive(high))
}
