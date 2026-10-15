package io.github.asagnc.aurora.core

/** Fixed for one run so tool schemas and routing do not change halfway through a prefix. */
class ToolRouter(executors: List<ToolExecutor>) : ToolExecutor {
    private val routes = buildMap {
        executors.forEach { executor -> executor.definitions.forEach { definition ->
            require(put(definition.name, executor) == null) { "Duplicate tool: ${definition.name}" }
        } }
    }
    override val definitions = executors.flatMap { it.definitions }
    override suspend fun execute(call: ToolCall): String =
        requireNotNull(routes[call.name]) { "Unknown tool: ${call.name}" }.execute(call)
}
