package io.github.asagnc.aurora.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

data class RunUpdate(val text: String, val usage: Usage, val stage: String)
data class RunResult(val text: String, val entries: List<JsonObject>, val usage: Usage)

class AgentLoop(private val transport: ModelTransport, private val executor: ToolExecutor) {
    suspend fun run(request: ModelRequest, onUpdate: suspend (RunUpdate) -> Unit,
        checkpoint: suspend (List<JsonObject>) -> Unit): RunResult {
        val history = request.history.toMutableList()
        while (true) {
            currentCoroutineContext().ensureActive()
            val text = StringBuilder()
            val turn = transport.generate(request.copy(history = history)) { delta ->
                text.append(delta)
                onUpdate(RunUpdate(text.toString(), Usage(), "生成回复"))
            }
            history += turn.nativeEntries
            if (turn.calls.isEmpty()) {
                require(turn.stopReason in setOf("stop", "end_turn", "completed")) { "模型未完整结束：${turn.stopReason}" }
                checkpoint(history.toList())
                return RunResult(turn.text, history, turn.usage)
            }
            require(turn.stopReason in setOf("tool_calls", "tool_use", "completed")) {
                "拒绝执行未完成或被截断的工具调用"
            }
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
                history += MessageProjection.result(request.connection.protocol, call, output)
            }
            checkpoint(history.toList())
        }
    }
}
