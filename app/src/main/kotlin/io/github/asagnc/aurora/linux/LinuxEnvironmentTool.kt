package io.github.asagnc.aurora.linux

import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

internal class LinuxEnvironmentTool(private val manager: LinuxEnvironmentManager) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("linux_environment",
        "Inspect, install or clean shared Linux base images. action=status/install/cleanup. Installation downloads a verified Ubuntu base for this device ABI. Does not execute Linux commands, install compiler/tool packs, or prove PRoot/chroot availability.",
        objectOf("type" to string("object"), "properties" to objectOf("action" to objectOf("type" to string("string"),
            "enum" to JsonArray(listOf("status", "install", "cleanup").map(::string)))), "required" to JsonArray(listOf(string("action"))),
            "additionalProperties" to JsonPrimitive(false))))
    override suspend fun execute(call: ToolCall): String = try {
        require(call.name == "linux_environment")
        when (call.arguments.text("action")) {
            "install" -> { manager.install(); status() }
            "cleanup" -> objectOf("removedVersions" to JsonPrimitive(manager.cleanup()))
            "status" -> status()
            else -> error("Unknown Linux environment action")
        }.toString()
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { objectOf("error" to string("linux_environment_unavailable"),
        "hint" to string("Inspect ABI, storage, source and image integrity. Installed base images do not imply an executable Linux backend.")).toString() }
    private suspend fun status(): JsonObject = objectOf("stage" to string(manager.status.value), "executionReady" to JsonPrimitive(false),
        "current" to (manager.registry.current()?.let(::string) ?: JsonNull),
        "versions" to JsonArray(manager.registry.versions().map { version -> objectOf("id" to string(version.id),
            "distribution" to string(version.manifest.image.distribution), "version" to string(version.manifest.image.version),
            "abi" to string(version.manifest.image.architecture.abi), "bytes" to JsonPrimitive(version.manifest.expandedBytes)) }))
}
