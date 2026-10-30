package io.github.asagnc.aurora.root

import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.CommandRequest
import java.util.UUID
import kotlinx.serialization.json.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Optional explicit policy qualification for the death/failure matrix. */
internal suspend fun qualifyFridaPolicyFixture(app: AuroraApplication) {
    if (InstrumentationRegistry.getArguments().getString("auroraFridaPolicyTest") != "true") return
    check(InstrumentationRegistry.getArguments().getString("auroraRootTest") == "true")
    try {
        val connection = requireNotNull(app.root.fridaConnection)
        val result = connection.client.policy(connection.identity, true, qualification = true)
        check(result["policyQualified"]!!.jsonPrimitive.boolean) { "Policy fixture was not qualified" }
    } catch (failure: Throwable) { withContext(NonCancellable) { app.root.disconnect() }; throw failure }
}

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
