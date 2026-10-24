package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*

/** Recovery remains available separately from the not-yet-qualified Frida injection backend. */
internal class RootManagedTool(private val manager: RootManagedRunManager, private val owner: String) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("root_managed_tasks",
        "Inspect or recover this conversation's pending Root injection intents. action=status/recover. " +
            "Recovery never replays scripts or terminates the attached target; only exact cleanup, retirement, " +
            "or confirmed original-target exit releases an intent. Root must already be connected. " +
            "Retained intents block conversation deletion. Frida injection is not yet packaged or qualified.",
        objectOf("type" to string("object"), "properties" to objectOf("action" to objectOf(
            "type" to string("string"), "enum" to JsonArray(listOf("status", "recover").map(::string)))),
            "required" to JsonArray(listOf(string("action"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "root_managed_tasks" && call.arguments.keys == setOf("action"))
        val action = call.arguments.text("action")
        require(action in setOf("status", "recover"))
        if (action == "recover") manager.recover(owner)
        return objectOf("pending" to JsonPrimitive(manager.pendingCount(owner)),
            "injectionReady" to JsonPrimitive(false)).toString()
    }
}
