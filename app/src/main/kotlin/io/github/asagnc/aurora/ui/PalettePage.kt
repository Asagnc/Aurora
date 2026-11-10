package io.github.asagnc.aurora.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun PalettePage(dismiss: () -> Unit) {
    val controller = LocalAppearance.current
    val state = controller.state
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var source by remember { mutableStateOf(if (state.dynamic) "系统" else if (state.palette == "自定义") "自定义" else "预设") }
    FullPage("配色", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AuroraTabs(listOf("预设", "系统", "自定义").map { it to it }, source) { name ->
                source = name
                when (name) {
                    "系统" -> controller.update(state.copy(dynamic = true))
                    "自定义" -> controller.update(state.copy(dynamic = false, palette = "自定义"))
                    else -> controller.update(state.copy(dynamic = false, palette = if (state.palette == "自定义") "青岚" else state.palette))
                }
            }
            when (source) {
                "预设" -> palettes.keys.toList().chunked(2).forEach { names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        names.forEach { name ->
                            val chosen = state.palette == name
                            Card(onClick = { controller.update(state.copy(palette = name, dynamic = false)) },
                                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton; selected = chosen },
                                colors = CardDefaults.cardColors(containerColor = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ThemePreview(remember(state, name, dark) { appearanceScheme(state.copy(palette = name), dark) }, compact = true)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                        if (chosen) Text("✓", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        if (names.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                "系统" -> { ThemePreview(MaterialTheme.colorScheme); Text("使用 Android 壁纸提供的系统配色，不读取或显示壁纸。", color = AuroraMuted) }
                else -> key(state.custom) { CustomAccent(state.custom) { controller.update(state.copy(custom = it, palette = "自定义", dynamic = false)) } }
            }
            if (source != "系统") {
                com.itos.coui.layout.Group(title = "色彩风格") {
                    AuroraChoice("风格", state.style, listOf("柔和", "鲜明", "单色"),
                        { controller.update(state.copy(style = it)) }, label = { it })
                }
            }
            Text("更改会即时应用。字体大小跟随系统。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun CustomAccent(initial: Int, apply: (Int) -> Unit) {
    val values = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    val hue = rememberSliderState(value = values[0], trackRange = 0f..360f)
    val saturation = rememberSliderState(value = values[1])
    val brightness = rememberSliderState(value = values[2])
    var hex by remember(initial) { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    val selected = AndroidColor.HSVToColor(floatArrayOf(hue.value, saturation.value, brightness.value))
    ThemePreview(appearanceScheme(LocalAppearance.current.state.copy(custom = selected), MaterialTheme.colorScheme.background.luminance() < 0.5f))
    Text("色相", style = MaterialTheme.typography.bodyMedium)
    Slider(state = hue, onValueChange = { hue.value = it })
    Text("饱和度", style = MaterialTheme.typography.bodyMedium)
    Slider(state = saturation, onValueChange = { saturation.value = it })
    Text("明度", style = MaterialTheme.typography.bodyMedium)
    Slider(state = brightness, onValueChange = { brightness.value = it })
    Button({ apply(selected) }) { Text("应用所选颜色") }
    AuroraInput(hex, { hex = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(6) },
        label = "颜色值（可选）", prefix = { Text("#") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton({ apply((0xFF000000L or hex.toLong(16)).toInt()) }, enabled = hex.length == 6) { Text("应用颜色值") }
}
