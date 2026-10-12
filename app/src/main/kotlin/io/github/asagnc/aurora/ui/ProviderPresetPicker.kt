package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import io.github.asagnc.aurora.core.*

@Composable internal fun ProviderPresetPicker(selected: ProviderPreset?, choose: (ProviderPreset?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(selected?.name ?: "选择供应商或自定义中转") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProviderCatalog.presets.forEach { preset ->
                DropdownMenuItem(text = { Text(preset.name) }, onClick = { choose(preset); expanded = false })
            }
            DropdownMenuItem(text = { Text("自定义中转 / 其他地域") }, onClick = { choose(null); expanded = false })
        }
    }
}

@Composable internal fun PresetModelPicker(models: List<String>, selected: String, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("推荐型号 · $selected") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(text = { Text(model) }, onClick = { choose(model); expanded = false })
            }
        }
    }
}
