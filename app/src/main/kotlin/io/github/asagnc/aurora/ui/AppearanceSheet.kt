package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable internal fun AppearanceSheet(dismiss: () -> Unit) {
    val controller = LocalAppearance.current
    val state = controller.state
    var colors by remember { mutableStateOf(false) }
    FullPage("外观与配色", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsSection("界面模式") {
                val modes = listOf("浅色", "深色", "跟随系统")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(selected = state.mode == mode, onClick = { controller.update(state.copy(mode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size)) {
                            Text(if (mode == "跟随系统") "系统" else mode)
                        }
                    }
                }
            }
            SettingsSection("主题") {
                ListItem(onClick = { colors = true }, supportingContent = {
                    Text(if (state.dynamic) "系统动态配色" else "${state.palette} · ${state.style}")
                }, trailingContent = { SettingsChevron() }) { Text("主题配色") }
            }
            SettingsSection("显示") {
                AppearanceSwitch("纯黑背景", "仅在深色模式生效", state.pureBlack) { controller.update(state.copy(pureBlack = it)) }
                AppearanceSwitch("彩色功能图标", "关闭后使用统一图标色", state.colorful) { controller.update(state.copy(colorful = it)) }
            }
            SettingsSection("效果预览") { ThemePreview(MaterialTheme.colorScheme) }
            TextButton({ controller.update(AppearanceState()) }) { Text("恢复默认外观") }
            Text("外观即时应用，字体大小跟随系统。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (colors) PalettePage { colors = false }
}

@Composable private fun AppearanceSwitch(title: String, summary: String, checked: Boolean, change: (Boolean) -> Unit) {
    ListItem(onClick = { change(!checked) }, supportingContent = { Text(summary) },
        modifier = Modifier.semantics { role = Role.Switch; stateDescription = if (checked) "已开启" else "已关闭" },
        trailingContent = { Switch(checked, onCheckedChange = null) }) { Text(title) }
}

@Composable internal fun ThemePreview(scheme: ColorScheme, compact: Boolean = false) {
    MaterialExpressiveTheme(colorScheme = scheme) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)) {
            Column(Modifier.padding(if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aurora", color = scheme.primary,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge)
                Surface(color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer, shape = MaterialTheme.shapes.large) {
                    Text("从一个想法开始。", Modifier.padding(12.dp),
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge)
                }
                if (!compact) Text("文字清晰，界面跟随你的配色。", color = scheme.onSurfaceVariant)
            }
        }
    }
}
