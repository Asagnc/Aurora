package io.github.asagnc.aurora.root

import android.os.Build
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal class FridaTool(private val app: AuroraApplication, private val owner: String,
    prepared: suspend (String, String) -> Unit) : ToolExecutor {
    private val attachment = FridaAttachment(app, owner, prepared)
    override val definitions = listOf(ToolDefinition("frida",
        "On-demand ARM64 Root Frida17.24.3. action=status/probe/attach/spawn. Does not require a Linux image. " +
            "probe tests a temporary CModule hook only inside the current Aurora Root service; java=true also tests its ART bridge. " +
            "Matching probe must pass in this Root service before attach. Probe success does not guarantee other targets work. " +
            "attach requires exact pid/startTicks, source=conversation/resources/workspace, relative script path and its current SHA256. " +
            "Get PID/start ticks with existing Root process/file tools, and script version with file tools. " +
            "spawn requires package instead of pid/startTicks; explicitly launches that Android package suspended, loads the script then resumes. " +
            "Only this task's captured target before resume intent may be killed on failure; after resume intent it is preserved. " +
            "Missing capture after a spawn call stays unknown and blocks deletion; no automatic replay. java=true supplies the fixed global Java bridge. " +
            "Observation1..250000ms defaults60000, followed by bounded unload/detach and independent resource cleanup. " +
            "workerSucceeded describes worker/cleanup status, not asynchronous user-code completion. " +
            "Full bounded events are in runs/<runId>/frida.log; read on demand. Cleanup cannot undo script side effects. " +
            "An unconfirmed intent blocks another dispatch in this conversation; inspect root_managed_tasks status/recover.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "action" to enumeration("status", "probe", "attach", "spawn"), "pid" to integer(1, Int.MAX_VALUE.toLong()),
            "package" to objectOf("type" to string("string")),
            "startTicks" to integer(1, Long.MAX_VALUE), "source" to enumeration("conversation", "resources", "workspace"),
            "path" to objectOf("type" to string("string")), "scriptSha256" to objectOf("type" to string("string"), "pattern" to string("^[a-f0-9]{64}$")),
            "java" to objectOf("type" to string("boolean")), "observeMs" to integer(1, 250000)),
            "required" to JsonArray(listOf(string("action"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "frida")
        return when (call.arguments.text("action")) {
            "status" -> { require(call.arguments.keys == setOf("action")); status().toString() }
            "probe" -> {
                require(call.arguments.keys.all { it in setOf("action", "java") })
                val java = call.arguments["java"]?.let { require(it is JsonPrimitive && !it.isString); requireNotNull(it.booleanOrNull) } ?: false
                val result = attachment.run(call.id, null, java)
                JsonObject(WireJson.parseToJsonElement(reply(result)).jsonObject + status() +
                    ("probeSucceeded" to JsonPrimitive(result.probeObserved == true))).toString()
            }
            "attach", "spawn" -> reply(attachment.run(call.id, FridaAttachRequest.parse(call.arguments)))
            else -> error("Unknown Frida action")
        }
    }

    private suspend fun status(): JsonObject {
        val connection = app.root.fridaConnection
        val installed = if (Build.SUPPORTED_ABIS.first() == "arm64-v8a" && connection != null)
            connection.client.packageInfo(connection.identity) else null
        return objectOf("packagedForAbi" to JsonPrimitive(Build.SUPPORTED_ABIS.first() == "arm64-v8a"),
            "rootConnected" to JsonPrimitive(connection != null), "pending" to JsonPrimitive(app.managedRuns.pendingCount(owner)),
            "nativeProbePassed" to JsonPrimitive(installed?.nativeProbePassed == true),
            "javaProbePassed" to JsonPrimitive(installed?.javaProbePassed == true))
    }

    private fun reply(result: RootManagedResult): String = JsonObject(
        WireJson.parseToJsonElement(WireJson.encodeToString(result)).jsonObject +
            ("workerSucceeded" to JsonPrimitive(result.successful))).toString()
    private fun enumeration(vararg values: String) = objectOf("type" to string("string"), "enum" to JsonArray(values.map(::string)))
    private fun integer(min: Long, max: Long) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(min), "maximum" to JsonPrimitive(max))
}
