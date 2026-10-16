package io.github.asagnc.aurora.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.ChatState

@Composable internal fun ChatComposer(state: ChatState, draft: String, edit: (String) -> Unit, attach: () -> Unit,
    choose: (Connection) -> Unit, models: () -> Unit, send: () -> Unit) {
    var metric by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = metric != null) { metric = null }
    Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp).padding(top = 8.dp)) {
        metric?.let { kind ->
            Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface) { MetricDetails(kind, state) }
        }
        Column(Modifier.fillMaxWidth()) {
            Surface(Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(horizontal = 10.dp).padding(top = 12.dp, bottom = 6.dp)) {
                    if (state.attachments.isNotEmpty()) Text("已导入 ${state.attachments.size} 个文件", Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    BasicTextField(draft, edit, Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuroraInk), maxLines = 6,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field ->
                            Box { if (draft.isEmpty()) Text("发消息，或交给 Aurora 一个任务…", color = AuroraMuted, style = MaterialTheme.typography.bodyLarge); field() }
                        })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(attach, Modifier.size(44.dp), enabled = !state.running) { LocalIcon(R.drawable.ic_add, "上传文件") }
                        EffortControl(state.selected, choose, Modifier.width(44.dp))
                        TextButton(models, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = AuroraInk)) {
                            state.selected?.let { ModelBrand(it.model, 18.dp); Spacer(Modifier.width(5.dp)) }
                            Text(state.selected?.model ?: "选择模型", Modifier.weight(1f, fill = false),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            state.selected?.let { Spacer(Modifier.width(5.dp)); Text(effortLabel(it.effort), color = AuroraMuted,
                                style = MaterialTheme.typography.labelMedium) }
                            Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, Modifier.padding(start = 3.dp).size(12.dp))
                        }
                        FilledIconButton(send, Modifier.size(40.dp), enabled = state.running || draft.isNotBlank(), colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            LocalIcon(if (state.running) R.drawable.ic_stop else R.drawable.ic_arrow_upward, if (state.running) "停止生成" else "发送")
                        }
                    }
                }
            }
            ComposerMetrics(state) { metric = if (metric == it) null else it }
        }
    }
}

@Composable private fun ComposerMetrics(state: ChatState, metric: (String) -> Unit) {
    val speed = (state.totalUsage.output?.takeIf { state.seconds > 0 }?.let { "≈%.1f tok/s".format(it / state.seconds) } ?: "— tok/s")
    val context = state.usage.input?.let { used -> state.selected?.contextLimit?.takeIf { it > 0 }?.let { limit ->
        "上下文 %.0f%%".format(used.toDouble() / limit * 100)
    } ?: "上下文 $used" } ?: "上下文 —"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetricButton(speed, R.drawable.ic_gauge, Modifier.weight(1f)) { metric("speed") }
        MetricButton(state.usage.cacheShare?.let { "缓存 %.0f%%".format(it * 100) } ?: "缓存 —",
            R.drawable.ic_database, Modifier.weight(1f)) { metric("cache") }
        MetricButton(context, R.drawable.ic_context, Modifier.weight(1f)) { metric("context") }
    }
}

@Composable private fun MetricButton(text: String, icon: Int, modifier: Modifier, click: () -> Unit) {
    TextButton(click, modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 2.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = AuroraMuted)) {
        Icon(painterResource(icon), null, Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EffortControl(connection: Connection?, choose: (Connection) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    IconButton({ expanded = true }, modifier, enabled = connection != null) {
        Icon(painterResource(R.drawable.ic_thinking), "思考等级：${effortLabel(connection?.effort ?: "auto")}",
            Modifier.size(21.dp), tint = AuroraInk)
    }
    if (expanded && connection != null) ModalBottomSheet(onDismissRequest = { expanded = false },
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("思考等级", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            CachePolicy.supportedEfforts(connection).forEach { effort ->
                TextButton({ choose(connection.copy(effort = effort)); expanded = false }, Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp), colors = ButtonDefaults.textButtonColors(contentColor = AuroraInk)) {
                    LocalIcon(R.drawable.ic_thinking, effortLabel(effort))
                    Text(effortLabel(effort), Modifier.weight(1f).padding(start = 12.dp))
                    if (connection.effort == effort) Text("已选", color = AuroraMuted)
                }
            }
        }
    }
}

internal fun effortLabel(effort: String): String = when (effort) {
    "auto" -> "自动"; "none" -> "关闭"; "low" -> "低"; "medium" -> "中"; "high" -> "高"; "xhigh" -> "更高"; "max" -> "最高"; else -> effort
}
