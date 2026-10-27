package io.github.asagnc.aurora.runtime

import android.content.Context
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.root.RootCommandClient
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

internal class SocketOwnerTool(context: Context, private val client: RootCommandClient, private val owner: String,
    private val prepared: suspend (String, String) -> Unit) : ToolExecutor {
    private val binary = Path.of(context.applicationInfo.nativeLibraryDir, "libaurora_socket_diag.so").toString()
    override val definitions = listOf(ToolDefinition("socket_owner_root",
        "Query a bounded current Root NETLINK_SOCK_DIAG socket snapshot for an exact numeric IPv4/IPv6 TCP/UDP five-tuple. " +
            "All six fields required; localPort 1..65535, remotePort 0..65535. No DNS or zone suffix. " +
            "Returns all exact matches (max 32), kernel UID/inode/cookie/interface/state, boot and network/user namespace identity and observation interval. " +
            "Scan is not atomic; sockets can change during/after observation. Multiple matches remain multiple; unconnected UDP/TIME_WAIT/missing inode are unknown. " +
            "UIDs use the recorded user namespace, or explicit initial_user_namespace when the live kernel has USER_NS disabled (namespace inode is then null). " +
            "Use the actual socket family: Java dual-stack IPv4 connections can be IPv4-mapped IPv6. UID 0 does not identify an application. " +
            "No package-name guesses, historical PCAP or per-packet attribution, routing changes or polling. " +
            "Interrupted, denied, oversized or incomplete dumps are unavailable, never a partial unique UID.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "family" to choice("ipv4", "ipv6"), "transport" to choice("tcp", "udp"),
            "localAddress" to objectOf("type" to string("string")), "remoteAddress" to objectOf("type" to string("string")),
            "localPort" to port(1), "remotePort" to port(0)),
            "required" to JsonArray(listOf("family", "transport", "localAddress", "localPort", "remoteAddress", "remotePort").map(::string)),
            "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "socket_owner_root")
        val request = SocketOwnerRequest.parse(call.arguments)
        val initialOnly = client.initialUserNamespaceOnly()
        val id = UUID.randomUUID().toString(); prepared(call.id, "runs/$id")
        val command = WireJson.parseToJsonElement(client.execute(owner, CommandRequest(request.argv(binary, initialOnly),
            timeoutMs = 5000, outputLimit = 8192, environment = mapOf("LC_ALL" to "C")), id)).jsonObject
        return try { JsonObject(command + ("socketSnapshot" to socketOwnerCompletion(command, request, initialOnly))).toString() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { JsonObject(command + ("error" to (command["error"] ?: string("socket_snapshot_unavailable")))).toString() }
    }

    companion object {
        private fun choice(vararg values: String) = objectOf("type" to string("string"), "enum" to JsonArray(values.map(::string)))
        private fun port(minimum: Int) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(minimum), "maximum" to JsonPrimitive(65535))
    }
}
