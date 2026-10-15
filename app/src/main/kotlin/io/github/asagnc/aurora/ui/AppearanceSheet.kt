package io.github.asagnc.aurora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R

@Composable internal fun AppearanceSheet(dismiss: () -> Unit) {
    val controller = LocalAppearance.current
    val state = controller.state
    var custom by remember { mutableStateOf(false) }
    var hex by remember { mutableStateOf("%06X".format(state.custom and 0xFFFFFF)) }
    FullPage("外观与配色", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("界面模式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("浅色", "深色", "跟随系统").forEach { mode ->
                    FilterChip(state.mode == mode, { controller.update(state.copy(mode = mode)) }, label = { Text(mode) })
                }
            }
            Text("主题配色", style = MaterialTheme.typography.titleMedium)
            (palettes.keys.toList() + "自定义").chunked(3).forEach { names ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    names.forEach { name ->
                        val colors = palettes[name] ?: listOf(Color(state.custom), Color(0xFF32BFD7), Color(0xFFEDB660))
                        Surface(onClick = {
                            if (name == "自定义") custom = true
                            else controller.update(state.copy(palette = name, dynamic = false))
                        }, Modifier.weight(1f), shape = RoundedCornerShape(16.dp),
                            color = if (state.palette == name && !state.dynamic) AuroraMint else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (state.palette == name && !state.dynamic) MaterialTheme.colorScheme.primary else AuroraBorder)) {
                            Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    colors.forEach { color -> Box(Modifier.size(20.dp).background(color, CircleShape)) }
                                }
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Text("效果预览", style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("你好，我是 Aurora。", style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(R.drawable.ic_lightbulb, Color(0xFFE57D15))
                        Text("思考 · 高", Modifier.weight(1f).padding(start = 8.dp))
                        FilledIconButton({}) { LocalIcon(R.drawable.ic_arrow_upward, "发送样式预览") }
                    }
                    Text("42.8 tok/s     缓存 86%     上下文 18%", color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
                    Text("仅为配色示例", color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(shape = RoundedCornerShape(18.dp)) {
                Column {
                    ListItem(headlineContent = { Text("彩色功能图标") }, trailingContent = {
                        Switch(state.colorful, { controller.update(state.copy(colorful = it)) })
                    })
                    ListItem(headlineContent = { Text("跟随系统动态配色") }, trailingContent = {
                        Switch(state.dynamic, { controller.update(state.copy(dynamic = it)) })
                    })
                }
            }
            Text("配色用于按钮、图标与选中状态。字体大小跟随系统设置。", color = AuroraMuted,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 24.dp))
        }
    }
    if (custom) AlertDialog(onDismissRequest = { custom = false }, title = { Text("自定义强调色") },
        text = { OutlinedTextField(hex, { hex = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(6) },
            label = { Text("六位颜色值，例如 6755D9") }, prefix = { Text("#") }, singleLine = true) },
        confirmButton = { TextButton({
            controller.update(state.copy(palette = "自定义", dynamic = false, custom = Color(0xFF000000 or hex.toLong(16)).toArgb()))
            custom = false
        }, enabled = hex.length == 6) { Text("应用") } }, dismissButton = { TextButton({ custom = false }) { Text("取消") } })
}
