package io.github.asagnc.aurora.reverse

import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*

internal class SharedToolchainTool(private val manager: SharedToolchainManager, private val owner: String,
    private val prepared: suspend (String, String) -> Unit) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("shared_toolchain",
        "Manage shared static reverse tools without per-conversation copies. action=status/install/update/remove/cleanup/probe. " +
        "install/update downloads and atomically publishes the compiled trusted bundle after full verification; failed/cancelled staging is removed. " +
        "remove requires an exact installed bundle id. cleanup retries up to 64 recognized abandoned stages. " +
        "Installation completion is not runtime readiness: inspect runtimeReady before analysis. Never infer installed tools can execute.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "action" to objectOf("type" to string("string"), "enum" to JsonArray(listOf("status", "install", "update", "remove", "cleanup", "probe").map(::string))),
            "bundle" to objectOf("type" to string("string"))),
            "required" to JsonArray(listOf(string("action"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "shared_toolchain")
        val args = call.arguments
        require(args.keys.all { it in setOf("action", "bundle") })
        return when (args.text("action")) {
            "status" -> manager.status()
            "probe" -> manager.probe(owner) { id -> prepared(call.id, "runs/$id") }
            "install", "update" -> {
                require("bundle" !in args)
                val id = manager.install()
                objectOf("installed" to string(id), "status" to manager.status())
            }
            "remove" -> objectOf("removed" to JsonPrimitive(manager.remove(requireNotNull(args.text("bundle")))), "status" to manager.status())
            "cleanup" -> objectOf("stagesRemoved" to JsonPrimitive(manager.cleanup()), "status" to manager.status())
            else -> error("Unknown shared toolchain action")
        }.toString()
    }
}
