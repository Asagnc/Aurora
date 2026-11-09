package io.github.asagnc.aurora.uiqualification

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.material3.CouiMaterial3Style
import com.itos.coui.theme.Coui
import com.itos.coui.theme.CouiPresets
import com.itos.coui.theme.CouiTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.itos.coui.layout.Scaffold as CouiScaffold
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.switch.Switch as CouiSwitch
import com.itos.coui.topappbar.TopAppBar as CouiTopAppBar

class PreviewApplication : Application() {
    override fun onCreate() { super.onCreate(); Coui.initialize(CouiMaterial3Style()) }
}

class PreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var mode by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val dark = mode ?: isSystemInDarkTheme()
            val palette = if (dark) CouiPresets.Dark else CouiPresets.Light
            val base = if (dark) darkColorScheme() else lightColorScheme()
            val scheme = base.copy(primary = palette.accent, onPrimary = palette.onAccent,
                background = palette.background, surface = palette.surface, surfaceContainer = palette.card,
                surfaceContainerHigh = palette.container, onSurface = palette.onSurface,
                onSurfaceVariant = palette.onSurfaceVariant, outlineVariant = palette.outlineVariant)
            MaterialExpressiveTheme(colorScheme = scheme) {
                CouiTheme(darkTheme = dark, colorScheme = palette) {
                    CouiScaffold(topBar = { CouiTopAppBar(title = { Text("Aurora", maxLines = 1) }) }) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                            .imePadding().verticalScroll(rememberScrollState())) {
                            Group(title = "仅本地预览") {
                                Item(title = { Text("深色外观") }, summary = { Text("不修改主 Aurora 的设置") },
                                    trailing = { CouiSwitch(modifier = Modifier.semantics { contentDescription = "预览深色外观" }, checked = dark, onCheckedChange = { mode = it }) })
                            }
                            ChatPreview()
                        }
                    }
                }
            }
        }
    }
}
