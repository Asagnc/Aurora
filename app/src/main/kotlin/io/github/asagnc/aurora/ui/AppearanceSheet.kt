package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import com.itos.coui.layout.Item
import com.itos.coui.layout.Group
import com.itos.coui.layout.Divider
import com.itos.coui.switch.Switch as CouiSwitch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.itos.coui.text.Text as CouiText
import com.itos.coui.text.CouiTextKind

@Composable internal fun AppearanceSheet(dismiss: () -> Unit) {
    val controller = LocalAppearance.current
    val state = controller.state
    var colors by rememberSaveable { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    FullPage("外观与配色", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Group(title = "主题") {
                AuroraChoice("界面模式", state.mode, listOf("浅色", "深色", "跟随系统"),
                    { controller.update(state.copy(mode = it)) }, label = { it })
                Divider(startIndent = 16.dp)
                Item(onClick = { colors = true }, summary = {
                    CouiText(text = if (state.dynamic) "系统动态配色" else "${state.palette} · ${state.style}",
                        kind = CouiTextKind.PreferenceSummary, color = AuroraMuted)
                }, trailing = { SettingsChevron() }) { Text("主题配色") }
            }
            Group(title = "显示") {
                AppearanceSwitch("纯黑背景", "仅在深色模式生效", state.pureBlack) { controller.update(state.copy(pureBlack = it)) }
                Divider(startIndent = 16.dp)
                AppearanceSwitch("彩色功能图标", "关闭后使用统一图标色", state.colorful) { controller.update(state.copy(colorful = it)) }
            }
            SettingsSection("效果预览") { ThemePreview(MaterialTheme.colorScheme) }
            TextButton({ reset = true }) { Text("恢复默认外观") }
            Text("外观即时应用，字体大小跟随系统。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (colors) PalettePage { colors = false }
    if (reset) AuroraPrompt("恢复默认外观？", "恢复默认", {
        controller.update(AppearanceState()); reset = false
    }, { reset = false }, message = "配色、界面模式和显示选项将恢复默认。", cancelLabel = "取消")
}

@Composable private fun AppearanceSwitch(title: String, summary: String, checked: Boolean, change: (Boolean) -> Unit) {
    Item(summary = { CouiText(text = summary, kind = CouiTextKind.PreferenceSummary, color = AuroraMuted) },
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = change)
            .semantics { stateDescription = if (checked) "已开启" else "已关闭" },
        trailing = { CouiSwitch(checked = checked, onCheckedChange = null) }) { CouiText(text = title, kind = CouiTextKind.PreferenceTitle) }
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
