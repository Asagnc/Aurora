package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.util.UUID
import kotlinx.serialization.json.*

/** Explicit test calls through the production tool ledger and Root endpoint. */
internal class FridaDeviceTools(private val app: AuroraApplication, private val owner: String) {
    private val tool = FridaTool(app, owner) { call, path -> app.toolRuns.attachArtifact(owner, call, path); Unit }
    suspend fun frida(args: JsonObject): JsonObject = app.tasks.run(owner) {
        val call = ToolCall(UUID.randomUUID().toString(), "frida", args)
        WireJson.parseToJsonElement(app.toolRuns.recording(owner, tool).execute(call)).jsonObject
    }
    suspend fun root(argv: List<String>): JsonObject = WireJson.parseToJsonElement(
        requireNotNull(app.root.commandClient).execute(owner, CommandRequest(argv, timeoutMs = 1500))).jsonObject
}
