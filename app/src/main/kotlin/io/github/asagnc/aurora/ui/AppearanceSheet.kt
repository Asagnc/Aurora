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
import androidx.compose.ui.unit.dp

@Composable internal fun AppearanceSheet(dismiss: () -> Unit) {
    val controller = LocalAppearance.current
    val state = controller.state
    var colors by remember { mutableStateOf(false) }
    FullPage("外观与主题", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ThemePreview(MaterialTheme.colorScheme)
            Text("界面模式", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("浅色", "深色", "跟随系统").forEach { mode ->
                    val preview = appearanceScheme(state, mode == "深色")
                    Surface(onClick = { controller.update(state.copy(mode = mode)) }, modifier = Modifier.weight(1f),
                        color = preview.background, shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (state.mode == mode) MaterialTheme.colorScheme.primary else AuroraBorder)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.fillMaxWidth(0.6f).height(8.dp).background(preview.primaryContainer, CircleShape))
                            Box(Modifier.fillMaxWidth().height(8.dp).background(preview.outlineVariant, CircleShape))
                            Text(mode, color = preview.onBackground, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text("配色", style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    AppearanceRow("配色来源", if (state.dynamic) "系统" else if (state.palette == "自定义") "自定义" else "预设主题") { colors = true }
                    AppearanceRow("当前主题", if (state.dynamic) "系统动态配色" else state.palette) { colors = true }
                    AppearanceRow("色彩风格", if (state.dynamic) "由系统决定" else state.style) { colors = true }
                }
            }
            Text("显示", style = MaterialTheme.typography.titleMedium)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column {
                    ListItem(headlineContent = { Text("纯黑背景") }, supportingContent = { Text("仅在深色模式生效") },
                        trailingContent = { Switch(state.pureBlack, { controller.update(state.copy(pureBlack = it)) }) })
                    ListItem(headlineContent = { Text("彩色功能图标") }, trailingContent = {
                        Switch(state.colorful, { controller.update(state.copy(colorful = it)) })
                    })
                }
            }
            TextButton({ controller.update(AppearanceState()) }) { Text("恢复默认外观") }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (colors) PalettePage { colors = false }
}

@Composable private fun AppearanceRow(title: String, value: String, click: () -> Unit) {
    Surface(onClick = click, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(10.dp)); Text("›", color = AuroraMuted)
        }
    }
}

@Composable internal fun ThemePreview(scheme: ColorScheme, compact: Boolean = false) {
    MaterialTheme(colorScheme = scheme) {
        Surface(color = scheme.background, shape = RoundedCornerShape(if (compact) 12.dp else 20.dp), border = BorderStroke(1.dp, scheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 18.dp)) {
                Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.End)) {
                    Text("你好，Aurora", Modifier.padding(8.dp), color = scheme.onPrimaryContainer,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
                }
                Text("从一个想法开始。", color = scheme.onBackground,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
                Surface(color = scheme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, scheme.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("+", color = scheme.onSurface)
                        Text(if (compact) "…" else "发送消息…", Modifier.weight(1f).padding(start = 8.dp), color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.size(if (compact) 20.dp else 28.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text("↑", color = scheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
