package io.github.asagnc.aurora.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val AuroraInk = Color(0xFF111B38)
internal val AuroraMuted = Color(0xFF697996)
internal val AuroraBorder = Color(0xFFE0E8F2)
internal val AuroraMist = Color(0xFFEEF4FA)
internal val AuroraMint = Color(0xFFD8F5EF)

@Composable fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF008B7D), onPrimary = Color.White,
        primaryContainer = AuroraMint, onPrimaryContainer = Color(0xFF003E38),
        secondary = AuroraMuted, onSecondary = Color.White,
        secondaryContainer = AuroraMist, onSecondaryContainer = AuroraInk,
        tertiary = Color(0xFF7773B9), tertiaryContainer = Color(0xFFEFEDFF),
        background = Color(0xFFF7FBFF), onBackground = AuroraInk,
        surface = Color.White, onSurface = AuroraInk,
        surfaceVariant = AuroraMist, onSurfaceVariant = AuroraMuted,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7FAFF),
        surfaceContainer = Color(0xFFF0F5FB), surfaceContainerHigh = Color(0xFFEAF1F8),
        surfaceContainerHighest = Color(0xFFE4ECF5),
        outline = Color(0xFF9AAAC1), outlineVariant = AuroraBorder,
    ), shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
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
