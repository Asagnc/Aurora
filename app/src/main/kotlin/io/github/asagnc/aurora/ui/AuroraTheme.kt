package io.github.asagnc.aurora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val accent = if (state.palette == "自定义") Color(state.custom) else palettes[state.palette]?.first() ?: palettes.getValue("极光").first()
    val scheme = if (state.dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(
        primary = lerp(accent, Color.White, 0.48f), onPrimary = Color(0xFF191625),
        primaryContainer = lerp(accent, Color(0xFF202025), 0.62f), onPrimaryContainer = Color(0xFFEAE4FF),
        secondary = lerp(accent, Color.White, 0.5f), secondaryContainer = lerp(accent, Color(0xFF25262B), 0.75f),
        onSecondaryContainer = Color(0xFFE6E6EB),
        background = Color(0xFF151619), surface = Color(0xFF1C1D21),
        surfaceContainerLow = lerp(accent, Color(0xFF202126), 0.94f),
        surfaceContainer = Color(0xFF28292F), onSurface = Color(0xFFE6E6EB), onBackground = Color(0xFFE6E6EB),
        onSurfaceVariant = Color(0xFFB5B5C0), outlineVariant = Color(0xFF41414B),
    ) else lightColorScheme(
        primary = accent, onPrimary = if (accent.luminance() > 0.179f) Color(0xFF151619) else Color.White,
        primaryContainer = lerp(accent, Color.White, 0.9f), onPrimaryContainer = Color(0xFF26223C),
        secondary = Color(0xFF62656F), secondaryContainer = Color(0xFFF0F1F5),
        background = Color(0xFFFCFCFD), onBackground = Color(0xFF242629),
        surface = Color.White, onSurface = Color(0xFF242629),
        surfaceContainerLow = lerp(accent, Color.White, 0.965f),
        surfaceContainer = Color(0xFFF1F2F5), surfaceContainerHigh = Color(0xFFEBECF1),
        onSurfaceVariant = Color(0xFF71757E), outline = Color(0xFF858994), outlineVariant = Color(0xFFE3E5ED),
    )
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(colorScheme = scheme, shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp)),
            typography = Typography(
                headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
                bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
            ), content = content)
    }
}
