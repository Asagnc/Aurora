package io.github.asagnc.aurora.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AppearanceState(val palette: String = "青岚", val mode: String = "跟随系统",
    val colorful: Boolean = false, val dynamic: Boolean = false, val custom: Int = 0xFF398B78.toInt(),
    val style: String = "柔和", val pureBlack: Boolean = false)

internal class AppearanceController(initial: AppearanceState, private val context: Context) {
    var state by mutableStateOf(initial)
        private set

    fun update(next: AppearanceState) {
        state = next
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit {
            putString("palette", next.palette).putString("mode", next.mode).putBoolean("colorful", next.colorful)
                .putBoolean("dynamic", next.dynamic).putInt("custom", next.custom)
                .putString("style", next.style).putBoolean("pureBlack", next.pureBlack)
        }
    }
}

internal val LocalAppearance = staticCompositionLocalOf<AppearanceController> { error("Appearance is not provided") }
internal val palettes = linkedMapOf(
    "青岚" to listOf(Color(0xFF398B78), Color(0xFF637F7B), Color(0xFF847151)),
    "极光" to listOf(Color(0xFF6755D9), Color(0xFF21B9DA), Color(0xFF83DEB7)),
    "海蓝" to listOf(Color(0xFF2667BE), Color(0xFF23AACA), Color(0xFFB1E2F6)),
    "森林" to listOf(Color(0xFF28724A), Color(0xFF78AB65), Color(0xFFD0E6AF)),
    "日落" to listOf(Color(0xFFA84621), Color(0xFFE98149), Color(0xFFFFCA78)),
    "紫罗兰" to listOf(Color(0xFF8542BA), Color(0xFFAD85E1), Color(0xFFE3C8FA)),
    "墨白" to listOf(Color(0xFF52575A), Color(0xFF717779), Color(0xFF86827A)),
)

internal suspend fun loadAppearance(context: Context): AppearanceState = withContext(Dispatchers.IO) {
    val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    AppearanceState(prefs.getString("palette", "青岚") ?: "青岚", prefs.getString("mode", "跟随系统") ?: "跟随系统",
        prefs.getBoolean("colorful", false), prefs.getBoolean("dynamic", false), prefs.getInt("custom", Color(0xFF398B78).toArgb()),
        prefs.getString("style", "柔和")?.takeIf { it in listOf("柔和", "鲜明", "单色") } ?: "柔和", prefs.getBoolean("pureBlack", false))
}
