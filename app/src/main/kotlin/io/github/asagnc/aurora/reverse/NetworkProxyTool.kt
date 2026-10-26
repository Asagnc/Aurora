package io.github.asagnc.aurora.reverse

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxAgentEnvironment
import kotlinx.serialization.json.*

internal class NetworkProxyTool(private val manager: SharedToolchainManager, private val owner: String,
    private val prepared: suspend (String, String) -> Unit, private val environment: LinuxAgentEnvironment) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("network_proxy",
        "Manage a bounded explicit regular HTTP/HTTPS/WebSocket loopback proxy in the current conversation's Root Linux workspace. " +
        "First shared_toolchain family=network-proxy install and probe for the current Root identity/generation/bundle. " +
        "action=status/start/stop/inspect/export_ca/cleanup. start requires options.port and returns a supervised terminal session with its actual bound address. " +
        "cleanup retries only this session's private runtime cache after existing Root stopped-ticket proof; unknown ownership/exit is retained. " +
        "Rules use exact lowercase ASCII host, literal pathPrefix and optional method, headers, Base64 body or directional WebSocket replacement. " +
        "Header edits are {name,value} or {name,remove:true}; framing/routing headers cannot be changed. " +
        "Replacement <=16KiB, combined configuration <=32KiB, duration <=240s, flows <=512, stored artifacts <=32MiB. " +
        "Existing terminal/cgroup/leases own cancellation and deletion barriers; stop never undoes already sent requests. " +
        "Each session has a private CA; export_ca exports only its validated public certificate, without installing trust or changing global routes. " +
        "Clients must explicitly use this proxy and trust its CA; pinned or bypassing clients may fail. " +
        "Use file tools on returned linuxArtifacts for live index and separate WS payloads. Stop/recover before inspect; it redacts sensitive headers/query strings but raw body previews may contain application data. " +
        "Stored/body budgets do not promise a hard heap bound for upstream transient WebSocket parsing.", schema))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "network_proxy")
        val args = call.arguments
        require(args.keys.all { it in setOf("action", "options", "session", "offset", "limit") })
        val attach: suspend (String) -> Unit = { prepared(call.id, "runs/$it") }
        val action = requireNotNull(args.text("action"))
        if (action != "start") require("options" !in args)
        if (action != "inspect") require("offset" !in args && "limit" !in args)
        if (action in setOf("status", "start")) require("session" !in args)
        return when (action) {
            "status" -> manager.status(environment.selectedGeneration(), "network-proxy")
            "start" -> manager.proxy.start(owner, environment.generation(), networkProxyOptions(requireNotNull(args["options"]).jsonObject), attach)
            "stop" -> manager.proxy.stop(owner, requireNotNull(args.text("session")))
            "inspect" -> manager.proxy.inspect(owner, environment.generation(), NetworkProxyRead(requireNotNull(args.text("session")),
                Math.toIntExact(args.number("offset") ?: 0), Math.toIntExact(args.number("limit") ?: 5)), attach)
            "export_ca" -> manager.proxy.exportCa(owner, requireNotNull(args.text("session")))
            "cleanup" -> manager.proxy.cleanup(owner, requireNotNull(args.text("session")))
            else -> error("Unknown proxy action")
        }.toString()
    }

    companion object {
        private fun text() = objectOf("type" to string("string"))
        private fun number(low: Long, high: Long) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(low), "maximum" to JsonPrimitive(high))
        private fun choice(vararg values: String) = objectOf("type" to string("string"), "enum" to JsonArray(values.map(::string)))
        private val header = objectOf("type" to string("object"), "properties" to objectOf("name" to text(), "value" to text(),
            "remove" to objectOf("type" to string("boolean"))), "required" to JsonArray(listOf(string("name"))), "additionalProperties" to JsonPrimitive(false))
        private val rule = objectOf("type" to string("object"), "properties" to objectOf(
            "stage" to choice("request", "response", "websocket"), "host" to text(), "pathPrefix" to text(), "method" to text(),
            "headers" to objectOf("type" to string("array"), "maxItems" to JsonPrimitive(32), "items" to header), "bodyBase64" to text(),
            "direction" to choice("client", "server")), "required" to JsonArray(listOf(string("stage"), string("host"))), "additionalProperties" to JsonPrimitive(false))
        private val options = objectOf("type" to string("object"), "properties" to objectOf("port" to number(1024, 65535),
            "durationSeconds" to number(1, 240), "maxFlows" to number(1, 512), "maxStoredBytes" to number(4096, 32L * 1024 * 1024),
            "maxBodyBytes" to number(1, 1024 * 1024), "rules" to objectOf("type" to string("array"), "maxItems" to JsonPrimitive(32), "items" to rule)),
            "required" to JsonArray(listOf(string("port"))), "additionalProperties" to JsonPrimitive(false))
        private val schema = objectOf("type" to string("object"), "properties" to objectOf("action" to choice("status", "start", "stop", "inspect", "export_ca", "cleanup"),
            "options" to options, "session" to text(), "offset" to number(0, 512), "limit" to number(1, 10)),
            "required" to JsonArray(listOf(string("action"))), "additionalProperties" to JsonPrimitive(false))
    }
}
