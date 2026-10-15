package io.github.asagnc.aurora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Box(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Surface(Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AuroraBorder.copy(alpha = 0.65f))) {
        Column(Modifier.padding(horizontal = 14.dp).padding(top = 16.dp, bottom = 4.dp)) {
            if (state.attachments.isNotEmpty()) Text("已导入 ${state.attachments.size} 个文件", Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
            BasicTextField(draft, edit, Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 8.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuroraInk), maxLines = 6,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field ->
                    Box { if (draft.isEmpty()) Text("继续追问，或添加新的任务…", color = AuroraMuted, style = MaterialTheme.typography.bodyLarge); field() }
                })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(attach, Modifier.size(48.dp), enabled = !state.running) { LocalIcon(R.drawable.ic_add, "上传文件") }
                EffortControl(state.selected, choose, Modifier.weight(1.25f))
                Surface(onClick = models, modifier = Modifier.weight(1f), shape = CircleShape, color = Color.Transparent) {
                    Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.selected?.let { ModelBrand(it.model, 24.dp) }
                        Text(state.selected?.let { modelFamily(it.model)?.substringBefore(' ') ?: "模型" } ?: "选择模型",
                            Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), "选择模型", Modifier.size(14.dp))
                    }
                }
                FilledIconButton(send, Modifier.size(44.dp), enabled = state.running || draft.isNotBlank(), colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    LocalIcon(if (state.running) R.drawable.ic_stop else R.drawable.ic_arrow_upward, if (state.running) "停止生成" else "发送")
                }
            }
            ComposerMetrics(state) { metric = it }
        }
    }
    metric?.let { MetricPopup(it, state) { metric = null } }
    }
}

@Composable private fun ComposerMetrics(state: ChatState, metric: (String) -> Unit) {
    val speed = state.totalUsage.output?.takeIf { state.seconds > 0 }?.let { "≈%.1f tok/s".format(it / state.seconds) } ?: "— tok/s"
    val context = state.usage.input?.let { used -> state.selected?.contextLimit?.takeIf { it > 0 }?.let { limit ->
        "上下文 %.0f%%".format(used.toDouble() / limit * 100)
    } ?: "上下文 $used" } ?: "上下文 —"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetricButton(speed, Modifier.weight(1f)) { metric("speed") }
        MetricButton(state.usage.cacheShare?.let { "缓存 %.0f%%".format(it * 100) } ?: "缓存 —",
            Modifier.weight(1f)) { metric("cache") }
        MetricButton(context, Modifier.weight(1f)) { metric("context") }
    }
}

@Composable private fun MetricButton(text: String, modifier: Modifier, click: () -> Unit) {
    TextButton(click, modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 2.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = AuroraMuted)) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun EffortControl(connection: Connection?, choose: (Connection) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { expanded = true }, enabled = connection != null, shape = CircleShape,
            color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(painterResource(R.drawable.ic_lightbulb), null, Modifier.size(19.dp), tint =
                    if (LocalAppearance.current.state.colorful) Color(0xFFE57D15) else MaterialTheme.colorScheme.primary)
                Text(effortLabel(connection?.effort ?: "auto"), Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), "思考等级", Modifier.size(13.dp))
            }
        }
        DropdownMenu(expanded, { expanded = false }) { connection?.let { current ->
            CachePolicy.supportedEfforts(current).forEach { effort -> DropdownMenuItem(text = { Text(effortLabel(effort)) },
                onClick = { choose(current.copy(effort = effort)); expanded = false }) }
        } }
    }
}

internal fun effortLabel(effort: String): String = when (effort) {
    "auto" -> "自动"; "none" -> "关闭"; "low" -> "低"; "medium" -> "中"; "high" -> "高"; "xhigh" -> "更高"; "max" -> "最高"; else -> effort
}
