package io.github.asagnc.aurora.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun PalettePage(dismiss: () -> Unit) {
    val controller = LocalAppearance.current
    val state = controller.state
    val dark = MaterialTheme.colorScheme.background.red < 0.4f
    var source by remember { mutableStateOf(if (state.dynamic) "系统" else if (state.palette == "自定义") "自定义" else "预设") }
    FullPage("配色", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("预设", "系统", "自定义").forEach { name -> FilterChip(source == name, {
                    source = name
                    when (name) {
                        "系统" -> controller.update(state.copy(dynamic = true))
                        "自定义" -> controller.update(state.copy(dynamic = false, palette = "自定义"))
                        else -> controller.update(state.copy(dynamic = false, palette = if (state.palette == "自定义") "青岚" else state.palette))
                    }
                }, label = { Text(name) }) }
            }
            when (source) {
                "预设" -> palettes.keys.toList().chunked(2).forEach { names ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        names.forEach { name -> Surface(onClick = { controller.update(state.copy(palette = name, dynamic = false)) },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (state.palette == name) MaterialTheme.colorScheme.primary else AuroraBorder)) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemePreview(remember(state, name, dark) { appearanceScheme(state.copy(palette = name), dark) }, compact = true)
                                Text(name + if (state.palette == name) " ✓" else "", Modifier.padding(4.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        } }
                        if (names.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                "系统" -> { ThemePreview(MaterialTheme.colorScheme); Text("使用 Android 壁纸提供的系统配色，不读取或显示壁纸。", color = AuroraMuted) }
                else -> CustomAccent(state.custom) { controller.update(state.copy(custom = it, palette = "自定义", dynamic = false)) }
            }
            if (source != "系统") {
                Text("色彩风格", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("柔和", "鲜明", "单色").forEach { style -> FilterChip(state.style == style,
                        { controller.update(state.copy(style = style)) }, label = { Text(style) }) }
                }
            }
            Text("更改会即时应用。字体大小跟随系统。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun CustomAccent(initial: Int, apply: (Int) -> Unit) {
    val values = remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember(initial) { mutableFloatStateOf(values[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(values[1]) }
    var brightness by remember(initial) { mutableFloatStateOf(values[2]) }
    var hex by remember(initial) { mutableStateOf("%06X".format(initial and 0xFFFFFF)) }
    val selected = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    ThemePreview(appearanceScheme(LocalAppearance.current.state.copy(custom = selected), MaterialTheme.colorScheme.background.red < 0.4f))
    Text("色相", style = MaterialTheme.typography.bodyMedium)
    Slider(hue, { hue = it }, valueRange = 0f..360f)
    Text("饱和度", style = MaterialTheme.typography.bodyMedium)
    Slider(saturation, { saturation = it })
    Text("明度", style = MaterialTheme.typography.bodyMedium)
    Slider(brightness, { brightness = it })
    Button({ apply(selected) }) { Text("应用所选颜色") }
    OutlinedTextField(hex, { hex = it.filter { c -> c in "0123456789abcdefABCDEF" }.take(6) },
        label = { Text("颜色值（可选）") }, prefix = { Text("#") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton({ apply((0xFF000000L or hex.toLong(16)).toInt()) }, enabled = hex.length == 6) { Text("应用颜色值") }
}
