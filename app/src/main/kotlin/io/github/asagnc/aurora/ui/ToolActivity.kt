package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.asagnc.aurora.data.ToolRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ToolActivity(events: List<ToolRun>, openArtifact: (String) -> Unit = {}, owner: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }
    val presentations by produceState(emptyList<ToolPresentation>(), events) {
        value = withContext(Dispatchers.Default) { events.takeLast(50).map(::presentTool) }
    }
    val lineColor = AuroraBorder
    val active = presentations.lastOrNull { it.event.state == "started" }
    Column(Modifier.fillMaxWidth()) {
        Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active != null) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(active?.title ?: "执行记录", Modifier.weight(1f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text("已返回 ${presentations.count { it.event.state == "completed" }} 项", color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
                Text(if (expanded) "⌄" else "›", color = AuroraMuted)
            }
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(start = 8.dp).drawBehind {
            drawLine(color = lineColor, start = androidx.compose.ui.geometry.Offset.Zero,
                end = androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = 1.dp.toPx())
        }.padding(start = 12.dp)) { presentations.forEach { item ->
            val event = item.event
            Surface(onClick = { detail = if (detail == event.id) null else event.id }, color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (event.state == "started") "·" else if (item.failed) "!" else if (event.state == "uncertain") "?" else "✓", color = AuroraMuted)
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(item.status, color = if (item.failed) MaterialTheme.colorScheme.error else AuroraMuted, style = MaterialTheme.typography.bodySmall)
                    Text(if (detail == event.id) "⌄" else "›", color = AuroraMuted)
                }
            }
            if (detail == event.id) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    ToolResultDetails(event, openArtifact, owner)
                }
            }
        } }
        if (expanded && events.size > 50) Text("显示最近 50 项", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
    }
}
