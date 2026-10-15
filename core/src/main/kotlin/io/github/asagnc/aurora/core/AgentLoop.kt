package io.github.asagnc.aurora.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

data class RunUpdate(val text: String, val usage: Usage, val stage: String)
data class RunResult(val text: String, val entries: List<JsonObject>, val usage: Usage,
    val totalUsage: Usage = usage, val requests: Int = 1)

class AgentLoop(private val transport: ModelTransport, private val executor: ToolExecutor) {
    suspend fun run(request: ModelRequest, onUpdate: suspend (RunUpdate) -> Unit,
        checkpoint: suspend (List<JsonObject>) -> Unit): RunResult {
        val history = request.history.toMutableList()
        val callsSeen = mutableSetOf<String>()
        var totals: Usage? = null
        var requests = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val text = StringBuilder()
            var lastUpdate = 0L
            val turn = transport.generate(request.copy(history = history)) { delta ->
                text.append(delta)
                val now = System.nanoTime()
                if (now - lastUpdate >= 50_000_000) {
                    onUpdate(RunUpdate(text.toString(), Usage(), "生成回复"))
                    lastUpdate = now
                }
            }
            totals = totals?.plus(turn.usage) ?: turn.usage
            requests++
            history += turn.nativeEntries
            if (turn.calls.isEmpty()) {
                require(turn.stopReason in setOf("stop", "end_turn", "completed")) { "模型未完整结束：${turn.stopReason}" }
                checkpoint(history.toList())
                return RunResult(turn.text, history, turn.usage, totals, requests)
            }
            require(turn.stopReason in setOf("tool_calls", "tool_use", "completed")) {
                "拒绝执行未完成或被截断的工具调用"
            }
            require(turn.calls.map { it.id }.distinct().size == turn.calls.size) { "Duplicate tool call identifiers" }
            require(turn.calls.none { it.id in callsSeen }) { "Tool call identifier reused in this run; inspect before replaying" }
            callsSeen += turn.calls.map { it.id }
            val results = mutableListOf<Pair<ToolCall, String>>()
            for (call in turn.calls) {
                currentCoroutineContext().ensureActive()
                onUpdate(RunUpdate(turn.text, turn.usage, "执行 ${call.name}"))
                val output = try { executor.execute(call) } catch (cancel: CancellationException) {
                    throw cancel
                } catch (failure: Exception) {
                    WireJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), objectOf(
                        "error" to string(failure.message ?: failure.javaClass.simpleName)
                    ))
                }
                results += call to output
            }
            history += MessageProjection.results(request.connection.protocol, results)
            checkpoint(history.toList())
        }
    }
}
