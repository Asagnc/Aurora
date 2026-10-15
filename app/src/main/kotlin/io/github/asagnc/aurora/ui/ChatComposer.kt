package io.github.asagnc.aurora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    choose: (Connection) -> Unit, models: () -> Unit, send: () -> Unit, metric: (String) -> Unit) {
    Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, AuroraBorder), shadowElevation = 2.dp) {
            Surface(Modifier.padding(6.dp), shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, AuroraBorder.copy(alpha = 0.55f))) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (state.attachments.isNotEmpty()) Text("已导入 ${state.attachments.size} 个文件 · 当前会话空间", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    BasicTextField(draft, edit, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = AuroraInk), maxLines = 6,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field ->
                            Box { if (draft.isEmpty()) Text("给 Aurora 发消息…", color = AuroraMuted, style = MaterialTheme.typography.bodyLarge); field() }
                        })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(attach, Modifier.size(44.dp).background(AuroraMist, CircleShape), enabled = !state.running) { LocalIcon(R.drawable.ic_add, "上传文件") }
                        EffortPill(state.selected, choose, Modifier.weight(1.4f))
                        Surface(onClick = models, modifier = Modifier.weight(0.75f), shape = CircleShape, color = AuroraMist) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.selected?.let { ModelBrand(it.model, 22.dp) }
                                if (state.selected == null) Text("模型", Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                                else Spacer(Modifier.weight(1f))
                                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), "选择模型", Modifier.size(16.dp))
                            }
                        }
                        FilledIconButton(send, Modifier.size(44.dp), colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)) {
                            LocalIcon(if (state.running) R.drawable.ic_stop else R.drawable.ic_arrow_upward, if (state.running) "停止生成" else "发送")
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            val speed = state.usage.output?.takeIf { state.seconds > 0 }?.let { "≈%.1f tok/s".format(it / state.seconds) } ?: "— tok/s"
            MetricButton(speed, Modifier.weight(1f)) { metric("speed") }
            VerticalDivider(Modifier.height(18.dp), color = AuroraBorder)
            MetricButton(state.usage.cacheShare?.let { "缓存 %.1f%%".format(it * 100) } ?: "缓存 —", Modifier.weight(1f)) { metric("cache") }
            VerticalDivider(Modifier.height(18.dp), color = AuroraBorder)
            MetricButton(state.usage.input?.let { "上下文 $it" } ?: "上下文 —", Modifier.weight(1f)) { metric("context") }
        }
    }
}

@Composable private fun MetricButton(text: String, modifier: Modifier, click: () -> Unit) {
    TextButton(click, modifier, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = AuroraMuted)) {
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun EffortPill(connection: Connection?, choose: (Connection) -> Unit, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { expanded = true }, enabled = connection != null, shape = CircleShape, color = AuroraMint,
            modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_lightbulb), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("思考 · ${effortLabel(connection?.effort ?: "auto")}", Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), "思考等级", Modifier.size(14.dp))
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
