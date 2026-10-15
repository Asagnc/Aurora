package io.github.asagnc.aurora.ui

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeContrastTest {
    @Test fun presetsAndExtremeCustomColorsKeepTextReadableInBothModes() {
        val states = palettes.keys.map { AppearanceState(palette = it) } +
            listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFFF00, 0xFF00FF00, 0xFF0000FF).map { AppearanceState(palette = "自定义", custom = it.toInt()) }
        for (state in states) for (dark in listOf(false, true)) for (style in listOf("柔和", "鲜明", "单色")) {
            val scheme = appearanceScheme(state.copy(style = style), dark)
            listOf(scheme.primary to scheme.onPrimary, scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.secondaryContainer to scheme.onSecondaryContainer, scheme.tertiaryContainer to scheme.onTertiaryContainer,
                scheme.background to scheme.onBackground, scheme.surface to scheme.onSurface,
                scheme.surface to scheme.onSurfaceVariant, scheme.errorContainer to scheme.onErrorContainer).forEach { (bg, fg) ->
                assertTrue("${state.palette}/$dark/$style contrast ${colorContrast(bg, fg)}", colorContrast(bg, fg) >= 4.5f)
            }
        }
        assertEquals(Color.Black, appearanceScheme(AppearanceState(pureBlack = true), true).background)
        assertNotEquals(Color.Black, appearanceScheme(AppearanceState(pureBlack = true), false).background)
    }
}
