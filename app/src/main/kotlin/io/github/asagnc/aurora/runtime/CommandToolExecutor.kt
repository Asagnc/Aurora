package io.github.asagnc.aurora.runtime

import io.github.asagnc.aurora.core.*
import java.nio.file.Path
import kotlinx.serialization.json.*
import io.github.asagnc.aurora.root.RootCommandClient

internal class CommandToolExecutor(private val runner: NativeCommandRunner, private val root: Path,
    private val privileged: RootCommandClient? = null) : ToolExecutor {
    private val name = if (privileged == null) "command_execute" else "command_execute_root"
    override val definitions = if (!runner.available) emptyList() else listOf(ToolDefinition(name,
        (if (privileged == null) "Execute an Android-native foreground command as Aurora's ordinary app UID, not Root or Linux/chroot. "
        else "Execute an Android-native foreground command with the connected Root service's UID 0. Not Linux/chroot. " +
            "Root-created command files may require explicit ownership restoration; do not recursively chown mounts. ") +
        "argv is passed literally without shell interpolation. Use /system/bin/sh -c explicitly when shell syntax is needed. " +
        "directory is relative to the conversation. Captures stdout/stderr artifacts (8 MiB each); previews and timeout are bounded. " +
        "Cancellation kills the supervised process group; independently daemonized sessions are not contained. No interactive PTY or persistent background jobs. " +
        "A working directory is not a security sandbox. Root access remains subject to SELinux and the root manager.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "argv" to objectOf("type" to string("array"), "items" to objectOf("type" to string("string")), "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(128)),
            "directory" to objectOf("type" to string("string")),
            "timeoutMs" to objectOf("type" to string("integer"), "minimum" to JsonPrimitive(1), "maximum" to JsonPrimitive(300000)),
            "outputLimit" to objectOf("type" to string("integer"), "minimum" to JsonPrimitive(1024), "maximum" to JsonPrimitive(if (privileged == null) 65536 else 8192)),
            "environment" to objectOf("type" to string("object"), "additionalProperties" to objectOf("type" to string("string")))
        ), "required" to JsonArray(listOf(string("argv"))), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == name)
        val args = call.arguments
        val argv = args.array("argv").map { require(it is JsonPrimitive && it.isString); it.content }
        val environment = args["environment"]?.jsonObject?.mapValues { (_, value) ->
            require(value is JsonPrimitive && value.isString); value.content
        }.orEmpty()
        val request = CommandRequest(argv, args.text("directory").orEmpty(), args.number("timeoutMs") ?: 60_000,
            Math.toIntExact(args.number("outputLimit") ?: 8192), environment)
        return privileged?.execute(root.fileName.toString(), request) ?: runner.execute(root, request).toString()
    }
}
