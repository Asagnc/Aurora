package io.github.asagnc.aurora.runtime

import io.github.asagnc.aurora.core.*
import java.nio.file.Path
import kotlinx.serialization.json.*

internal class CommandToolExecutor(private val runner: NativeCommandRunner, private val root: Path) : ToolExecutor {
    override val definitions = if (!runner.available) emptyList() else listOf(ToolDefinition("command_execute",
        "Execute an Android-native foreground command as Aurora's ordinary app UID, not Root or Linux/chroot. " +
        "argv is passed literally without shell interpolation. Use /system/bin/sh -c explicitly when shell syntax is needed. " +
        "directory is relative to the conversation. Captures stdout/stderr artifacts (8 MiB each); previews and timeout are bounded. " +
        "Cancellation kills the supervised process group; independently daemonized sessions are not contained. No interactive PTY or persistent background jobs. " +
        "A working directory is not a security sandbox. Commands cannot grant Android/Root permissions.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "argv" to objectOf("type" to string("array"), "items" to objectOf("type" to string("string")), "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(128)),
            "directory" to objectOf("type" to string("string")),
            "timeoutMs" to objectOf("type" to string("integer"), "minimum" to JsonPrimitive(1), "maximum" to JsonPrimitive(300000)),
            "outputLimit" to objectOf("type" to string("integer"), "minimum" to JsonPrimitive(1024), "maximum" to JsonPrimitive(65536)),
            "environment" to objectOf("type" to string("object"), "additionalProperties" to objectOf("type" to string("string")))
        ), "required" to JsonArray(listOf(string("argv"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "command_execute")
        val args = call.arguments
        val argv = args.array("argv").map { require(it is JsonPrimitive && it.isString); it.content }
        val environment = args["environment"]?.jsonObject?.mapValues { (_, value) ->
            require(value is JsonPrimitive && value.isString); value.content
        }.orEmpty()
        return runner.execute(root, CommandRequest(argv, args.text("directory").orEmpty(), args.number("timeoutMs") ?: 60_000,
            Math.toIntExact(args.number("outputLimit") ?: 8192), environment)).toString()
    }
}
