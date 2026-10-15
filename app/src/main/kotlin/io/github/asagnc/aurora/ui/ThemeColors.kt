package io.github.asagnc.aurora.ui

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Complete role mapping; presets and arbitrary custom accents use the same contrast rules. */
internal fun appearanceScheme(state: AppearanceState, dark: Boolean): ColorScheme {
    val colors = palettes[state.palette] ?: palettes.getValue("青岚")
    val seed = if (state.palette == "自定义") Color(state.custom) else colors.first()
    val accent = if (state.style == "单色") Color(0xFF62696A) else seed
    val canvas = if (dark) Color(0xFF171B1B) else Color(0xFFF8F8F5)
    val ink = if (dark) Color(0xFFE8ECE9) else Color(0xFF252A2C)
    val tint = if (state.style == "鲜明") 0.06f else 0.02f
    val background = if (dark && state.pureBlack) Color.Black else lerp(canvas, accent, tint)
    val surface = if (dark) lerp(Color(0xFF222827), accent, tint) else Color(0xFFFFFEFC)
    val primary = contrastAgainst(if (dark) lerp(accent, Color.White, 0.42f) else accent, background)
    val secondary = contrastAgainst(lerp(accent, ink, 0.55f), background)
    val tertiary = contrastAgainst(if (state.style == "单色") secondary else colors.last(), background)
    val primaryContainer = lerp(surface, accent, if (dark) 0.24f else 0.13f)
    val secondaryContainer = lerp(surface, secondary, 0.12f)
    val tertiaryContainer = lerp(surface, tertiary, 0.12f)
    val error = contrastAgainst(if (dark) Color(0xFFFFB4AB) else Color(0xFFBA1A1A), background)
    val errorContainer = if (dark) Color(0xFF66302D) else Color(0xFFFFDAD6)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = readableOn(primary), primaryContainer = primaryContainer,
        onPrimaryContainer = readableOn(primaryContainer), inversePrimary = contrastAgainst(accent, ink),
        secondary = secondary, onSecondary = readableOn(secondary), secondaryContainer = secondaryContainer,
        onSecondaryContainer = readableOn(secondaryContainer), tertiary = tertiary, onTertiary = readableOn(tertiary),
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = readableOn(tertiaryContainer),
        background = background, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = secondaryContainer, onSurfaceVariant = contrastAgainst(lerp(ink, surface, 0.32f), surface),
        surfaceTint = primary, inverseSurface = ink, inverseOnSurface = readableOn(ink),
        error = error, onError = readableOn(error), errorContainer = errorContainer, onErrorContainer = readableOn(errorContainer),
        outline = lerp(ink, surface, 0.50f), outlineVariant = lerp(ink, surface, 0.87f),
        scrim = Color.Black, surfaceBright = if (dark) lerp(surface, Color.White, 0.08f) else surface,
        surfaceDim = if (dark) background else lerp(surface, ink, 0.05f), surfaceContainerLowest = background,
        surfaceContainerLow = lerp(surface, background, 0.5f), surfaceContainer = lerp(surface, accent, 0.035f),
        surfaceContainerHigh = lerp(surface, ink, 0.04f), surfaceContainerHighest = lerp(surface, ink, 0.08f),
    )
}

internal fun colorContrast(first: Color, second: Color): Float {
    val a = first.luminance(); val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}
private fun readableOn(color: Color) = if (colorContrast(Color.White, color) >= colorContrast(Color.Black, color)) Color.White else Color.Black
private fun contrastAgainst(color: Color, background: Color): Color {
    if (colorContrast(color, background) >= 4.5f) return color
    val target = readableOn(background)
    return (1..20).map { lerp(color, target, it / 20f) }.first { colorContrast(it, background) >= 4.5f }
}
