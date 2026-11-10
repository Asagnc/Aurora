package io.github.asagnc.aurora.ui

import androidx.compose.runtime.Composable
import io.github.asagnc.aurora.core.*

@Composable internal fun ProviderPresetPicker(selected: ProviderPreset?, choose: (ProviderPreset?) -> Unit) {
    AuroraChoice("供应商", selected, ProviderCatalog.presets + listOf(null), choose,
        label = { it?.name ?: "自定义中转 / 其他地域" })
}
