package io.github.asagnc.aurora.runtime

import android.content.Context
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Bounded offline decoding; live capture is a separate Root-only operation. */
internal class PacketInspectionTool(context: Context, private val root: Path,
    private val runner: NativeCommandRunner = NativeCommandRunner(context),
    private val prepared: suspend (String, String) -> Unit = { _, _ -> }) : ToolExecutor {
    private val binary = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_tcpdump.so")
    override val definitions = listOf(ToolDefinition("packet_inspect",
        "Read a conversation-owned PCAP/PCAPNG using packaged tcpdump 4.99.7/libpcap 1.10.7. " +
            "Offline only: no Root or live network capture. path is relative to the conversation; links are rejected. " +
            "filter is an optional literal BPF expression. count limits matching packets, not bytes scanned; " +
            "work is bounded to 30 seconds and a 512 MiB input. No DNS/service lookups. " +
            "stdout/stderr preview and artifacts are bounded; output may be partial on timeout. " +
            "Does not decrypt TLS or infer application identity. Inspect exitCode/error before treating decoding as successful.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "path" to objectOf("type" to string("string")),
            "filter" to objectOf("type" to string("string"), "maxLength" to JsonPrimitive(4096)),
            "count" to objectOf("type" to string("integer"), "minimum" to JsonPrimitive(1), "maximum" to JsonPrimitive(5000))),
            "required" to JsonArray(listOf(string("path"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String = withContext(Dispatchers.IO) {
        require(call.name == "packet_inspect")
        require(call.arguments.keys.all { it in setOf("path", "filter", "count") })
        require(Files.isRegularFile(binary, LinkOption.NOFOLLOW_LINKS)) { "Packaged packet tool unavailable" }
        val path = call.arguments["path"] as? JsonPrimitive
        require(path?.isString == true)
        val count = call.arguments["count"]?.let {
            require(it is JsonPrimitive && !it.isString)
            requireNotNull(it.longOrNull)
        } ?: 100
        require(count in 1..5000)
        val filter = call.arguments["filter"]?.let {
            require(it is JsonPrimitive && it.isString)
            it.content
        }.orEmpty()
        require(filter.length <= 4096 && '\u0000' !in filter)
        val file = input(path.content)
        val argv = listOf(binary.toString(), "-nn", "-t", "-c", count.toString(), "-r", file.toString(), "--") +
            if (filter.isBlank()) emptyList() else listOf(filter)
        val artifact = UUID.randomUUID().toString()
        prepared(call.id, "runs/$artifact")
        runner.execute(root, CommandRequest(argv, timeoutMs = 30_000,
            environment = mapOf("LC_ALL" to "C")), artifact).toString()
    }

    private fun input(value: String): Path {
        require(root.isAbsolute && !Files.isSymbolicLink(root))
        val relative = Path.of(value)
        require(value.isNotBlank() && !relative.isAbsolute && relative.none { it.toString() in setOf("..", ".aurora-transactions") })
        var target = root
        for (part in relative) {
            target = target.resolve(part)
            require(!Files.isSymbolicLink(target)) { "Packet input cannot follow a link" }
        }
        require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) { "Packet input is not a regular file" }
        require(Files.size(target) in 24..512L * 1024 * 1024) { "Packet input exceeds the offline inspection budget" }
        return target
    }
}
