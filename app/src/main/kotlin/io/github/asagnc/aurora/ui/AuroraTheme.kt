package io.github.asagnc.aurora.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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
    val scheme = remember(state, dark) {
        val base = if (state.dynamic) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else appearanceScheme(state, dark)
        if (dark && state.pureBlack) base.copy(background = Color.Black, surfaceContainerLowest = Color.Black) else base
    }
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
