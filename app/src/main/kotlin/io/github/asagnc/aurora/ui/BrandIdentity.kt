package io.github.asagnc.aurora.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.core.ProviderCatalog

// Visual identity only: names never establish endpoint trust or protocol capabilities.
internal fun modelFamily(model: String): String? = ProviderCatalog.presets.firstOrNull { model in it.models }?.name ?: when {
    model.startsWith("gpt-", true) -> "OpenAI"
    model.startsWith("claude-", true) -> "Claude"
    model.startsWith("deepseek", true) -> "DeepSeek"
    model.startsWith("gemini-", true) -> "Gemini"
    model.startsWith("glm-", true) -> "GLM"
    model.startsWith("mimo-", true) -> "小米 MiMo"
    model.startsWith("grok-", true) -> "Grok"
    model.startsWith("minimax-", true) -> "MiniMax"
    model.startsWith("kimi-", true) -> "Kimi"
    model.startsWith("qwen", true) -> "阿里百炼 · 北京"
    else -> null
}

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
    val tint = if (LocalAppearance.current.state.colorful) when (modelFamily(model)) {
        "Claude" -> Color(0xFFD27757)
        "DeepSeek" -> Color(0xFF4B7BE5)
        "Gemini", "阿里百炼 · 北京" -> Color(0xFF8874DD)
        else -> AuroraInk
    } else AuroraInk
    Icon(painterResource(drawable), modelFamily(model) ?: "自定义模型", Modifier.size(size).padding(size * 0.08f), tint = tint)
}

