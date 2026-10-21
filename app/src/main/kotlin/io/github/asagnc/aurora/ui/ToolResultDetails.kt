package io.github.asagnc.aurora.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.CommandOutputSummary
import io.github.asagnc.aurora.core.summarizeToolResult
import io.github.asagnc.aurora.data.ToolRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ToolResultDetails(event: ToolRun, openArtifact: (String) -> Unit) {
    val projection by produceState<Pair<io.github.asagnc.aurora.core.ToolResultSummary?, String>?>(null, event.result) {
        value = withContext(Dispatchers.Default) { summarizeToolResult(event.result) to toolResultPreview(event.result) }
    }
    val summary = projection?.first
    val preview = projection?.second ?: "正在读取结果预览…"
    var raw by remember(event.id) { mutableStateOf(false) }
    val command = summary?.stdout != null || summary?.stderr != null
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(event.tool, color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
        if (command) {
            summary.exitCode?.let { Text("退出码 $it", style = MaterialTheme.typography.labelMedium) }
            summary.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            summary.stdout?.let { CommandOutput("标准输出", it, openArtifact) }
            summary.stderr?.let { CommandOutput("错误输出", it, openArtifact) }
            TextButton(onClick = { raw = !raw }) { Text(if (raw) "收起原始结果" else "查看原始结果") }
        }
        if (raw || !command) {
            Text("调用 ID：${event.callId}", color = AuroraMuted, style = MaterialTheme.typography.labelSmall)
            SelectionContainer { Text(preview, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)) }
            CopyTextButton(preview, "复制结果预览")
        }
    }
}

@Composable private fun CommandOutput(title: String, output: CommandOutputSummary, openArtifact: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
            output.text?.takeIf { it.isNotEmpty() }?.let { CopyTextButton(it, "复制$title") }
        }
        SelectionContainer {
            Text(output.text?.ifEmpty { "（无输出）" } ?: "二进制输出，请查看产物文件。",
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        if (output.truncated) Text("仅显示输出预览", color = AuroraMuted, style = MaterialTheme.typography.labelSmall)
        output.artifact?.let { path -> TextButton(onClick = { openArtifact(path) }, contentPadding = PaddingValues(0.dp)) {
            Text("查看产物 · ${path.substringAfterLast('/')}", style = MaterialTheme.typography.labelMedium)
        } }
    }
}
