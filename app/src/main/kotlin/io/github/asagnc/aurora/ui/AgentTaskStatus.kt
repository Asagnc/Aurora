package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.runtime.ChatState

@Composable internal fun AgentTaskStatus(state: ChatState) {
    var expanded by remember(state.taskId) { mutableStateOf(false) }
    val status = when (state.taskStatus) {
        "queued" -> "等待启动"; "running" -> "正在执行"; "completed" -> "已完成"
        "cancelled" -> "已停止"; "failed" -> "执行失败"; "interrupted" -> "进程中断"
        else -> "结果待核实"
    }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, enabled = state.taskId != null,
            contentPadding = PaddingValues(vertical = 6.dp)) {
            Text(state.stage.ifBlank { "任务$status" } + if (state.taskId != null) "  ·  详情" else "",
                color = if (state.running) MaterialTheme.colorScheme.primary else AuroraMuted,
                style = MaterialTheme.typography.bodySmall)
        }
        if (expanded) SelectionContainer {
            Text("任务 ID：${state.taskId}\n状态：$status\n运行记录保留在本会话；中断操作不会自动重放。",
                color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
