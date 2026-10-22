package io.github.asagnc.aurora.core

import kotlinx.serialization.json.*

data class LinuxTerminalOptions(val argv: List<String>, val columns: Int, val rows: Int,
    val timeoutMs: Long, val awakeMs: Long)

/** Start only; ordinary terminal tools retain ownership checks for subsequent operations. */
class LinuxTerminalTool(private val start: suspend (LinuxTerminalOptions) -> JsonObject) : ToolExecutor {
    private fun integer(min: Int, max: Int) = objectOf("type" to string("integer"),
        "minimum" to JsonPrimitive(min), "maximum" to JsonPrimitive(max))
    override val definitions = listOf(ToolDefinition("terminal_start_linux",
        "Start an interactive Root Linux PTY only when a program requires a terminal; prefer command_execute_linux for batch work. " +
            "Requires installed Root generation and supported task cgroup/private mounts; no PRoot fallback. Generation is pinned on first Linux use for this Agent run; a live PTY keeps its own lease afterward. " +
            "Default argv=/bin/sh; absolute guest executable and literal arguments. Fixed cwd=/mnt, HOME=/root, TMPDIR=/tmp use conversation Linux files. " +
            "Read-only shared base. One unresolved Linux run per conversation. Use terminal_read/write/resize/stop/list with the returned id. " +
            "Output is merged, persisted first 16 MiB, live ring last 64 KiB. Persists across turns/pages until stopped/deadline/process death. " +
            "Starting does not imply program success; inspect output and final state. Never automatically replay uncertain input. " +
            "Idle sessions hold no permanent CPU wake lock; awakeMs is an optional bounded lease for active computation, at most timeoutMs.",
        objectOf("type" to string("object"), "properties" to objectOf(
            "argv" to objectOf("type" to string("array"), "items" to objectOf("type" to string("string")),
                "minItems" to JsonPrimitive(1), "maxItems" to JsonPrimitive(128)),
            "columns" to integer(2, 300), "rows" to integer(2, 200),
            "timeoutMs" to integer(1, 270000), "awakeMs" to integer(0, 270000)),
            "required" to JsonArray(emptyList()), "additionalProperties" to JsonPrimitive(false))))

    override suspend fun execute(call: ToolCall): String {
        require(call.name == "terminal_start_linux")
        val args = call.arguments
        require(args.keys.all { it in setOf("argv", "columns", "rows", "timeoutMs", "awakeMs") })
        val argv = args["argv"]?.jsonArray?.map { require(it.jsonPrimitive.isString); it.jsonPrimitive.content }
            ?: listOf("/bin/sh")
        fun number(name: String, fallback: Long): Long {
            if (name !in args) return fallback
            val value = args[name] as? JsonPrimitive
            require(value != null && !value.isString)
            return requireNotNull(value.longOrNull)
        }
        val timeout = number("timeoutMs", 270_000)
        val awake = number("awakeMs", 0)
        val columns = number("columns", 80)
        val rows = number("rows", 24)
        require(argv.size in 1..128 && argv.first().startsWith("/"))
        require(argv.all { '\u0000' !in it } && argv.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1 } <= 65536)
        require(timeout in 1..270_000 && awake in 0..timeout && columns in 2..300 && rows in 2..200)
        return start(LinuxTerminalOptions(argv, columns.toInt(), rows.toInt(), timeout, awake)).toString()
    }
}
