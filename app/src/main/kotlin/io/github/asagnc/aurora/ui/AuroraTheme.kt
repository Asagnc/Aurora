package io.github.asagnc.aurora.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF007F75), onPrimary = Color.White,
        primaryContainer = Color(0xFFD1F4ED), onPrimaryContainer = Color(0xFF003C36),
        background = Color(0xFFF5F9FC), onBackground = Color(0xFF101B36),
        surface = Color(0xFFFCFDFF), onSurface = Color(0xFF101B36),
        surfaceVariant = Color(0xFFEAF0F5), outline = Color(0xFF718098),
    ), content = content)
}
