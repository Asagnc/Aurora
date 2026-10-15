package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.data.ToolRun
import io.github.asagnc.aurora.core.WireJson
import kotlinx.serialization.json.jsonObject

@Composable internal fun ToolActivity(events: List<ToolRun>) {
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth()) {
        TextButton({ expanded = !expanded }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text("${if (expanded) "⌄" else "›"} 最近执行记录 · ${events.size}", color = AuroraMuted)
        }
        if (expanded) events.takeLast(50).forEach { event ->
            val status = when {
                event.state == "started" -> "执行中"
                event.state == "uncertain" -> "结果待核实"
                event.result?.let { runCatching { WireJson.parseToJsonElement(it).jsonObject.containsKey("error") }.getOrDefault(false) } == true -> "返回错误"
                else -> "已返回"
            }
            TextButton({ detail = if (detail == event.id) null else event.id }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("${event.tool} · $status", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (detail == event.id) SelectionContainer {
                Text(event.result?.let { it.take(8000) + if (it.length > 8000) "\n（预览已截断）" else "" } ?: "尚无结果",
                    Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (expanded && events.size > 50) Text("显示最近 50 项", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
    }
}
