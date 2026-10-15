package io.github.asagnc.aurora.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.asagnc.aurora.runtime.ChatState

@Composable internal fun MetricDialog(kind: String, state: ChatState, dismiss: () -> Unit) {
    val (title, text) = when (kind) {
        "speed" -> "速度详情" to "输出 tokens：${state.usage.output ?: "服务未提供"}\n本轮总耗时：%.2f 秒\n当前显示端到端估算，包含首字等待和工具运行；不是纯生成速度。".format(state.seconds)
        "cache" -> "缓存详情" to "输入 tokens：${state.usage.input ?: "服务未提供"}\n缓存读取：${state.usage.cacheRead ?: "服务未提供"}\n缓存写入：${state.usage.cacheWrite ?: "服务未提供"}\n命中率来自服务实际用量，不用客户端猜测值代替。"
        "context" -> "上下文详情" to "最近请求输入：${state.usage.input ?: "服务未提供"}\n模型上限：${state.selected?.contextLimit ?: "未配置"}\n工具结果和模型原生状态也属于上下文。"
        else -> error("Unknown metric: $kind")
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}
