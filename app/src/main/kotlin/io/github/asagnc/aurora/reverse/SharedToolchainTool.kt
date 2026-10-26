package io.github.asagnc.aurora.reverse

import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*

internal class SharedToolchainTool(private val manager: SharedToolchainManager, private val owner: String,
    private val prepared: suspend (String, String) -> Unit,
    private val environment: io.github.asagnc.aurora.linux.LinuxAgentEnvironment) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("shared_toolchain",
        "Manage shared tools without per-conversation copies. family=static-reverse (default) or android-build. action=status/install/update/remove/cleanup/probe. " +
        "android-build install requires nativeSource=conversation/resources/workspace and nativePath selecting the fixed reviewed ARM64 archive; no arbitrary download URL. " +
        "android-build probe actually builds an owned Java/Compose/dual-ABI JNI APK, without installing it; network dependency downloads and a 270-second task budget apply. " +
        "install/update downloads and atomically publishes the compiled trusted bundle after full verification; failed/cancelled staging is removed. " +
        "remove requires an exact installed bundle id. cleanup retries up to 64 recognized abandoned stages. " +
        "probe qualifies the same Linux generation pinned by this Agent run, including after a shared environment update. " +
        "Installation completion is not runtime readiness: inspect runtimeReady before analysis. Never infer installed tools can execute.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "action" to objectOf("type" to string("string"), "enum" to JsonArray(listOf("status", "install", "update", "remove", "cleanup", "probe").map(::string))),
            "bundle" to objectOf("type" to string("string")),
            "family" to objectOf("type" to string("string"), "enum" to JsonArray(listOf("static-reverse", "android-build").map(::string))),
            "nativeSource" to objectOf("type" to string("string"), "enum" to JsonArray(listOf("conversation", "resources", "workspace").map(::string))),
            "nativePath" to objectOf("type" to string("string"))),
            "required" to JsonArray(listOf(string("action"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "shared_toolchain")
        val args = call.arguments
        require(args.keys.all { it in setOf("action", "bundle", "family", "nativeSource", "nativePath") })
        val family = args.text("family") ?: "static-reverse"
        require(family in setOf("static-reverse", "android-build"))
        val androidBuild = family == "android-build"
        if (args.text("action") !in setOf("install", "update") || !androidBuild)
            require("nativeSource" !in args && "nativePath" !in args)
        return when (args.text("action")) {
            "status" -> manager.status(environment.selectedGeneration(), androidBuild)
            "probe" -> {
                var attached = false
                val attach: suspend (String) -> Unit = { id ->
                    // One ledger entry has one primary artifact; the reply lists every step's log directory.
                    if (!attached) { prepared(call.id, "runs/$id"); attached = true }
                }
                if (androidBuild) manager.probeAndroid(owner, environment.generation(), attach)
                else manager.probe(owner, environment.generation(), attach)
            }
            "install", "update" -> {
                require("bundle" !in args)
                val id = if (androidBuild) manager.installAndroid(owner, requireNotNull(args.text("nativeSource")), requireNotNull(args.text("nativePath"))) else manager.install()
                objectOf("installed" to string(id), "status" to manager.status(environment.selectedGeneration(), androidBuild))
            }
            "remove" -> objectOf("removed" to JsonPrimitive(manager.remove(requireNotNull(args.text("bundle")))), "status" to manager.status(environment.selectedGeneration()))
            "cleanup" -> objectOf("stagesRemoved" to JsonPrimitive(manager.cleanup()), "status" to manager.status(environment.selectedGeneration()))
            else -> error("Unknown shared toolchain action")
        }.toString()
    }
}
