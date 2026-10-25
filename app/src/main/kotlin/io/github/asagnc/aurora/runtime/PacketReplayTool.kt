package io.github.asagnc.aurora.runtime

import android.content.Context
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootPacketClient
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

internal class PacketReplayTool(context: Context, private val client: RootPacketClient, private val owner: String,
    private val root: Path, private val prepared: suspend (String, String) -> Unit) : ToolExecutor {
    private val selector = PacketPcapSelector(context)
    override val definitions = listOf(ToolDefinition("packet_replay_root",
        "Replay an exact selected slice of a conversation-owned classic PCAP using Root libpcap. path/sourceSha256/interface are required. " +
            "firstPacket is zero-based (default 0), count is exact (default 1, max 64); selected frames total at most bytes (default/max 16384). " +
            "Input max 32 MiB. All selected frames must be complete Ethernet without an indicated FCS; truncated/cooked/other links, PCAPNG, missing frames " +
            "or selection over budget are rejected before any Root send. PCAPNG can still be inspected with packet_inspect. " +
            "Selected order is preserved, each frame once; capture timestamps are not replayed. Fixed packetsPerSecond defaults 10 (max 1000), " +
            "seconds defaults 15 (max 30). No catch-up bursts or retries of partial/failed sends. Read injection.complete and accepted counters: " +
            "deadline/cancel can leave partial remote effects. Kernel acceptance is not delivery or reconstructed TCP/HTTP state. No route changes or automatic replay of an uncertain result.",
        objectOf("type" to string("object"), "properties" to objectOf("path" to text(), "sourceSha256" to text(), "interface" to text(),
            "firstPacket" to integer(0, 1000000), "count" to integer(1, 64), "bytes" to integer(14, 16384),
            "packetsPerSecond" to integer(1, 1000), "seconds" to integer(1, 30)),
            "required" to JsonArray(listOf("path", "sourceSha256", "interface").map(::string)), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        require(call.name == "packet_replay_root")
        val request = PacketReplayRequest.parse(call.arguments)
        val input = OwnedFiles(root).readBinary(request.path, 32 * 1024 * 1024)
        require(input.size >= 24)
        val digest = MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }
        require(digest == request.sourceSha256) { "PCAP snapshot differs from selected SHA256" }
        val id = UUID.randomUUID().toString(); prepared(call.id, "runs/$id")
        val selected = selector.select(input, request)
        if (selected["error"] != null) return@withContext objectOf("error" to string("packet_replay_input_invalid"),
            "reason" to selected["error"]!!, "injectionStarted" to JsonPrimitive(false)).toString()
        val injection = try { packetReplaySelection(selected, request, input.size) }
        catch (_: IllegalArgumentException) { return@withContext objectOf("error" to string("packet_replay_input_invalid"),
            "reason" to string("selector_invalid_result"), "injectionStarted" to JsonPrimitive(false)).toString() }
        val result = WireJson.parseToJsonElement(client.inject(owner, id, injection)).jsonObject
        JsonObject(result + mapOf("sourceSha256" to string(digest), "firstPacket" to JsonPrimitive(request.firstPacket),
            "selectedFrames" to JsonPrimitive(request.count), "selectedBytes" to JsonPrimitive(injection.frames.sumOf { it.size }))).toString()
    }

    companion object {
        private fun text() = objectOf("type" to string("string"))
        private fun integer(low: Int, high: Int) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(low), "maximum" to JsonPrimitive(high))
    }
}
