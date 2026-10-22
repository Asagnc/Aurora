package io.github.asagnc.aurora.terminal

import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

internal class TerminalToolExecutor(private val manager: TerminalManager, private val owner: String,
    private val rootAvailable: Boolean) : ToolExecutor {
    private val text = objectOf("type" to string("string"))
    private fun integer(min: Int, max: Int) = objectOf("type" to string("integer"), "minimum" to JsonPrimitive(min), "maximum" to JsonPrimitive(max))
    private fun definition(name: String, description: String, properties: JsonObject, vararg required: String) = ToolDefinition(
        name, description, objectOf("type" to string("object"), "properties" to properties,
            "required" to JsonArray(required.map(::string)), "additionalProperties" to JsonPrimitive(false)))
    override val definitions = if (!manager.available) emptyList() else listOf(
        definition("terminal_start", "Start an interactive Android PTY, only when a program requires a terminal. Ordinary batch work should use command_execute. " +
            "Session persists across model turns and page changes until stopped, deadline or process death. No automatic input replay; not a sandbox or Linux installation. " +
            "stdout/stderr are merged. At most 4 live sessions per conversation, 8 globally. Log stores first 16 MiB; byte cursor retains last 64 KiB. " +
            "root requires a connected Root service. Pass argv literally. Idle PTYs do not hold a permanent CPU wake lock. " +
            "For known active local computation, optional awakeMs grants a bounded CPU lease (0 by default; at most timeoutMs and 300000ms), not a freeze-policy bypass.",
            objectOf("argv" to objectOf("type" to string("array"), "items" to text, "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(128)),
                "directory" to text, "columns" to integer(2, 300), "rows" to integer(2, 200), "timeoutMs" to integer(1, 3600000),
                "root" to objectOf("type" to string("boolean")), "awakeMs" to integer(0, 300000),
                "environment" to objectOf("type" to string("object"), "additionalProperties" to text))),
        definition("terminal_read", "Read merged raw output incrementally using a byte cursor, with optional event-driven wait. " +
            "next is the next cursor; cursorDropped means bytes fell outside the live ring. text may split UTF-8; optional includeBase64 returns exact bytes. " +
            "Choose view=output (default) for append-only bytes, screen for a bounded current VT screen/TUI, both only when needed. " +
            "After app restart only persisted first-16-MiB log is available; its cursor uses stored bytes, no VT restoration.",
            objectOf("id" to text, "cursor" to objectOf("type" to string("integer"), "minimum" to JsonPrimitive(0)),
                "limit" to integer(1, 65536), "waitMs" to integer(0, 10000),
                "view" to objectOf("type" to string("string"), "enum" to JsonArray(listOf("output", "screen", "both").map(::string))),
                "includeBase64" to objectOf("type" to string("boolean"))), "id"),
        definition("terminal_write", "Send 1..4096 UTF-8 bytes literally to a live terminal. Include newline to submit; use control characters for Ctrl-C (U+0003), EOF (U+0004). " +
            "Does not assert that a command succeeded. Inspect output/exit status. Split larger input explicitly; never automatically retry uncertain input.",
            objectOf("id" to text, "text" to text), "id", "text"),
        definition("terminal_resize", "Set interactive terminal dimensions, notifying the foreground process and reflowing the screen.",
            objectOf("id" to text, "columns" to integer(2, 300), "rows" to integer(2, 200)), "id", "columns", "rows"),
        definition("terminal_stop", "Stop a terminal and await cleanup. Android backends supervise job-control groups; detached new sessions are outside containment. Linux uses its task cgroup and retains a recovery lease if stopped proof is missing. No automatic restart.",
            objectOf("id" to text), "id"),
        definition("terminal_list", "List this conversation's active and recent persisted terminals, including exit, interruption and truncation state.", objectOf())
    )

    override suspend fun execute(call: ToolCall): String = try {
        val args = call.arguments
        fun id() = requireNotNull(args.text("id"))
        when (call.name) {
            "terminal_start" -> {
                val root = args["root"]?.jsonPrimitive?.booleanOrNull ?: false
                check(!root || rootAvailable) { "Root terminal is not connected" }
                val argv = args["argv"]?.jsonArray?.map { require(it.jsonPrimitive.isString); it.jsonPrimitive.content } ?: listOf("/system/bin/sh")
                val env = args["environment"]?.jsonObject?.mapValues { require(it.value.jsonPrimitive.isString); it.value.jsonPrimitive.content }.orEmpty()
                manager.start(owner, PtyRequest(argv, args.text("directory").orEmpty(), Math.toIntExact(args.number("columns") ?: 80),
                    Math.toIntExact(args.number("rows") ?: 24), args.number("timeoutMs") ?: 300_000, env, args.number("awakeMs") ?: 0), root).json()
            }
            "terminal_read" -> manager.read(owner, id(), TerminalRead(args.number("cursor") ?: 0, Math.toIntExact(args.number("limit") ?: 8192),
                args.number("waitMs") ?: 0, args.text("view") ?: "output", args["includeBase64"]?.jsonPrimitive?.booleanOrNull ?: false))
            "terminal_write" -> { manager.write(owner, id(), requireNotNull(args.text("text"))); objectOf("inputAccepted" to JsonPrimitive(true), "commandSuccess" to JsonNull) }
            "terminal_resize" -> { manager.resize(owner, id(), Math.toIntExact(requireNotNull(args.number("columns"))), Math.toIntExact(requireNotNull(args.number("rows")))); objectOf("resized" to JsonPrimitive(true)) }
            "terminal_stop" -> manager.stop(owner, id()).json()
            "terminal_list" -> objectOf("terminals" to JsonArray(manager.list(owner).map { it.json() }))
            else -> error("Unknown terminal tool")
        }.toString()
    } catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) { objectOf("error" to string("terminal_operation_failed"), "reason" to string(failure.message?.take(200) ?: failure.javaClass.simpleName),
        "inputReplaySafe" to JsonPrimitive(false)).toString() }
}
