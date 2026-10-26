package io.github.asagnc.aurora.reverse

import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.linux.LinuxAgentEnvironment
import java.util.UUID
import kotlinx.serialization.json.*

internal class AndroidBuildTool(private val manager: SharedToolchainManager, private val owner: String,
    private val prepared: suspend (String, String) -> Unit, private val environment: LinuxAgentEnvironment) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("apk_build",
        "Build an Android project inside this conversation's Root Linux workspace using the pinned shared ARM64 Java25/Gradle/SDK. " +
        "Requires shared_toolchain family=android-build install and a successful probe for the current Root identity and Linux generation. " +
        "project is a relative workspace directory; tasks are literal Gradle task names (default assembleDebug), never shell or Gradle options. " +
        "The existing task supervision, durable bundle lease, cancellation and 270-second deadline apply. " +
        "Project build logic executes with Root privileges inside this Linux task. Only build trusted projects. " +
        "Returns execution proof and logs; inspect actual APK outputs with Linux file tools. Does not sign with the user's release key or install an APK.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "project" to objectOf("type" to string("string")),
            "tasks" to objectOf("type" to string("array"), "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(8),
                "items" to objectOf("type" to string("string")))),
            "required" to JsonArray(listOf(string("project"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "apk_build" && call.arguments.keys.all { it in setOf("project", "tasks") })
        val supplied = call.arguments["tasks"]
        val tasks = if (supplied == null) listOf("assembleDebug") else {
            require(supplied is JsonArray)
            supplied.map { require(it is JsonPrimitive && it.isString); it.content }
        }
        val request = AndroidBuildRequest(requireNotNull(call.arguments.text("project")), tasks)
        val generation = environment.generation()
        val id = UUID.randomUUID().toString(); prepared(call.id, "runs/$id")
        return manager.buildAndroid(owner, generation, request, id).toString()
    }
}
