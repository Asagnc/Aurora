package io.github.asagnc.aurora.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.core.ProviderCatalog

internal fun modelFamily(model: String): String? = ProviderCatalog.presets.firstOrNull { model in it.models }?.name

@Composable internal fun ModelBrand(model: String, size: Dp = 28.dp) {
    val drawable = when (modelFamily(model)) {
        "OpenAI" -> R.drawable.brand_openai
        "Claude" -> R.drawable.brand_claude
        "DeepSeek" -> R.drawable.brand_deepseek
        "Gemini" -> R.drawable.brand_gemini
        "GLM" -> R.drawable.brand_zai
        "小米 MiMo" -> R.drawable.brand_xiaomimimo
        "Grok" -> R.drawable.brand_grok
        "MiniMax" -> R.drawable.brand_minimax
        "Kimi" -> R.drawable.brand_kimi
        "阿里百炼 · 北京" -> R.drawable.brand_qwen
        else -> R.drawable.ic_hub
    }
    Image(painterResource(drawable), modelFamily(model) ?: "自定义模型", Modifier.size(size).clip(CircleShape)
        .background(Color.White).padding(size * 0.16f))
}

