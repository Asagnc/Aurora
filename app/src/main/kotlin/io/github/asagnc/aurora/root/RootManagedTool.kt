package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.core.*
import kotlinx.serialization.json.*

/** Recovery remains available separately from the not-yet-qualified Frida injection backend. */
internal class RootManagedTool(private val manager: RootManagedRunManager, private val owner: String) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("root_managed_tasks",
        "Inspect or recover up to four of this conversation's pending Root managed intents. action=status/recover. " +
            "Recovery never replays scripts or terminates a pre-existing attached target. It may reclaim only this task's " +
            "captured spawn before any resume intent, using its exact same-boot identity. Only exact cleanup, retirement, " +
            "or confirmed original-target exit releases a Frida intent. Capture recovery verifies the original closed segment prefix " +
            "and retries publication without starting another capture; failed publication retains its intent even after resource exit. Root must already be connected. " +
            "Retained intents block conversation deletion. Use frida status/probe for qualification scoped to the current Root service.",
        objectOf("type" to string("object"), "properties" to objectOf("action" to objectOf(
            "type" to string("string"), "enum" to JsonArray(listOf("status", "recover").map(::string)))),
            "required" to JsonArray(listOf(string("action"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "root_managed_tasks" && call.arguments.keys == setOf("action"))
        val action = call.arguments.text("action")
        require(action in setOf("status", "recover"))
        if (action == "recover") manager.recover(owner)
        return objectOf("pending" to JsonPrimitive(manager.pendingCount(owner))).toString()
    }
}
