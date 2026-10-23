package io.github.asagnc.aurora.core

import java.util.UUID
import kotlinx.serialization.json.*

/** A stopped scope proves cleanup, not that the requested guest command succeeded. */
class LinuxCommandTool(private val run: suspend (String, List<String>, Long) -> JsonObject,
    private val prepared: suspend (String, String) -> Unit) : ToolExecutor {
    override val definitions = listOf(ToolDefinition("command_execute_linux",
        "Execute a batch command inside the installed shared Root Linux generation, pinned on first Linux use for this Agent run. Requires connected Root and supported task cgroup/private mounts; no PRoot fallback. " +
            "argv is literal, begins with an absolute guest executable; use /bin/sh -c explicitly for shell syntax. " +
            "Guest cwd=/mnt, HOME=/root, TMPDIR=/tmp map to this conversation's .aurora-linux/project, home and tmp. " +
            "Base image is read-only; conversation files retain guest ownership and are accessed through file tools. " +
            "stdout/stderr artifacts are bounded to 8 MiB each. Inspect guest exitCode, state and stopped separately. " +
            "No automatic replay after failure or uncertainty; linux_environment recover_runs only cleans up. Use Android command tools for Android executables.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "argv" to objectOf("type" to string("array"), "items" to objectOf("type" to string("string")),
                "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(128)),
            "timeoutMs" to objectOf("type" to string("integer"), "minimum" to JsonPrimitive(1), "maximum" to JsonPrimitive(270000))),
            "required" to JsonArray(listOf(string("argv"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "command_execute_linux")
        require(call.arguments.keys.all { it in setOf("argv", "timeoutMs") })
        val argv = call.arguments.array("argv").map { require(it is JsonPrimitive && it.isString); it.content }
        val rawTimeout = call.arguments["timeoutMs"]
        require(rawTimeout == null || rawTimeout is JsonPrimitive && !rawTimeout.isString && rawTimeout.longOrNull != null)
        val timeout = (rawTimeout as? JsonPrimitive)?.longOrNull ?: 60_000
        require(timeout in 1..270_000 && argv.size in 1..128 && argv.first().startsWith("/"))
        require(argv.all { '\u0000' !in it } && argv.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1 } <= 65536)
        val id = UUID.randomUUID().toString()
        prepared(call.id, "runs/$id")
        return project(id, run(id, argv, timeout)).toString()
    }

    internal fun project(id: String, proof: JsonObject): JsonObject {
        require(proof["protocol"] == JsonPrimitive(1) && proof["runId"] == string(id))
        val stopped = proof["stopped"] == JsonPrimitive(true)
        val completion = proof["completion"] as? JsonObject
        val rawCode = completion?.get("exitCode") as? JsonPrimitive
        val code = rawCode?.takeUnless { it.isString }?.longOrNull?.takeIf { it in 0..255 }
        val timedOut = completion?.get("timedOut") == JsonPrimitive(true)
        val interrupted = completion?.get("interrupted") == JsonPrimitive(true)
        val completed = stopped && completion?.get("scopeRemoved") == JsonPrimitive(true) && code != null &&
            completion["interrupted"] == JsonPrimitive(false) && completion["timedOut"] == JsonPrimitive(false)
        val state = when { !stopped -> "uncertain"; timedOut -> "timed_out"; interrupted -> "interrupted"
            completed -> "exited"; else -> "outcome_unknown" }
        return objectOf("state" to string(state), "exitCode" to (if (completed) JsonPrimitive(code) else JsonNull),
            "success" to JsonPrimitive(completed && code == 0L), "stopped" to JsonPrimitive(stopped),
            "replaySafe" to JsonPrimitive(false), "detail" to proof)
    }
}
