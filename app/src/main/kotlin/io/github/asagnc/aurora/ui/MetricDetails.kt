package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.runtime.ChatState

@Composable internal fun MetricDetails(kind: String, state: ChatState) {
    val title = when (kind) { "speed" -> "速度"; "cache" -> "缓存"; else -> "上下文" }
    val rows = when (kind) {
        "speed" -> listOf("本轮输出 tokens" to state.totalUsage.output?.toString(), "模型请求数" to state.requests.toString(),
            "工具调用数" to state.steps.toString(), "本轮耗时" to "%.2f 秒".format(state.seconds))
        "cache" -> listOf("输入 tokens" to state.usage.input?.toString(), "缓存读取" to state.usage.cacheRead?.toString(), "缓存写入" to state.usage.cacheWrite?.toString())
        else -> listOf("最近输入" to state.usage.input?.toString(), "模型上限" to state.selected?.contextLimit?.toString())
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        rows.forEach { (label, value) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            Text(value ?: "服务未提供", style = MaterialTheme.typography.bodySmall)
        } }
        Text(if (kind == "speed") "整轮端到端估算，包含等待和工具耗时。" else "最近一次请求，以服务实际用量为准。",
            color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
    }
}
