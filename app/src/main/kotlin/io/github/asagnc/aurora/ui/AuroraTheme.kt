package io.github.asagnc.aurora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import com.itos.coui.theme.CouiTheme
import com.itos.coui.theme.CouiPresets

internal val AuroraInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
internal val AuroraMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
internal val AuroraBorder: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
internal val AuroraMist: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
internal val AuroraMint: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer

@Composable fun AuroraTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<AppearanceController?>(null) }
    LaunchedEffect(context) { controller = AppearanceController(loadAppearance(context), context) }
    val appearance = controller ?: return
    val state = appearance.state
    val dark = when (state.mode) { "深色" -> true; "浅色" -> false; else -> isSystemInDarkTheme() }
    val activity = LocalActivity.current
    SideEffect { activity?.let {
        WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    } }
    val scheme = remember(state, dark) {
        val base = if (state.dynamic) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else appearanceScheme(state, dark)
        if (dark && state.pureBlack) base.copy(background = Color.Black, surfaceContainerLowest = Color.Black) else base
    }
    val neutral = if (dark) CouiPresets.Dark else CouiPresets.Light
    val surfaces = scheme.copy(
        background = scheme.background,
        surface = if (dark && state.pureBlack) Color.Black else neutral.surface,
        surfaceContainerLowest = if (dark && state.pureBlack) Color.Black else neutral.background,
        surfaceContainerLow = neutral.card, surfaceContainer = if (dark) neutral.container else neutral.background,
        surfaceContainerHigh = neutral.container, surfaceContainerHighest = neutral.container,
        onSurface = neutral.onSurface, onSurfaceVariant = neutral.onSurfaceVariant,
        outlineVariant = neutral.outlineVariant)
    val pageTint = when (state.style) { "鲜明" -> 0.06f; "单色" -> 0f; else -> 0.02f }
    val coui = neutral.copy(
        background = if (dark) neutral.background else lerp(neutral.background, scheme.primary, pageTint),
        accent = scheme.primary, onAccent = scheme.onPrimary,
        accentOnSurface = scheme.primary, checkboxAccent = scheme.primary, menuCheckboxAccent = scheme.primary)
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialExpressiveTheme(colorScheme = surfaces) {
            CouiTheme(darkTheme = dark, colorScheme = coui, content = content)
        }
    }
}
