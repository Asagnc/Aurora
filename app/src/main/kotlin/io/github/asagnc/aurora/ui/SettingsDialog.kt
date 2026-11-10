package io.github.asagnc.aurora.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Divider
import com.itos.coui.topappbar.CouiTopAppBarDefaults
import com.itos.coui.topappbar.LargeTopAppBar
import com.itos.coui.topappbar.TopAppBarButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
import com.itos.coui.text.Text as CouiText
import com.itos.coui.text.CouiTextKind
import io.github.asagnc.aurora.root.RootPhase
import io.github.asagnc.aurora.runtime.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun SettingsDialog(model: ChatViewModel, providers: () -> Unit, files: () -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val version by produceState<String?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
        }.getOrNull() }
    }
    val root by model.rootState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    FullPage("设置", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsEntries("模型与工作流", listOf(
                SettingsEntry("供应商与 API", "模型连接与密钥", R.drawable.ic_hub) { providers() },
                SettingsEntry("Skills", "安装与管理工作流", R.drawable.ic_extension) { page = "skills" }))
            SettingsEntries("设备与执行", listOf(
                SettingsEntry("Root 权限", when (root.phase) {
                    RootPhase.READY -> "已连接"
                    RootPhase.CONNECTING -> "连接中"
                    RootPhase.UNAVAILABLE -> "连接不可用"
                    RootPhase.ORDINARY -> "未连接"
                }, R.drawable.ic_shield) { page = "root" },
                SettingsEntry("Linux 环境", "基础环境与构建工具", R.drawable.ic_terminal) { page = "linux" },
                SettingsEntry("工具与执行", "查看能力与使用条件", R.drawable.ic_checklist) { page = "tools" }))
            if (Build.VERSION.SDK_INT >= 37) SettingsSection("网络权限") { LocalNetworkPermissionRow() }
            SettingsEntries("外观与数据", listOf(
                SettingsEntry("外观与配色", LocalAppearance.current.state.let { if (it.dynamic) "系统动态配色" else it.palette },
                    R.drawable.ic_palette) { page = "appearance" },
                SettingsEntry("空间与文件", "会话、共享资源与工作区", R.drawable.ic_folder_open) { files() }))
            SettingsEntries("关于", listOf(SettingsEntry("关于 Aurora", version ?: "开发版本", R.drawable.ic_info) { page = "about" }))
            Spacer(Modifier.height(16.dp))
        }
    }
    when (page) {
        "appearance" -> AppearanceSheet { page = null }
        "root" -> RootConnectionPage(model) { page = null }
        "tools" -> ExecutionStatusPage(model.commandsAvailable) { page = null }
        "skills" -> SkillsPage { page = null }
        "linux" -> LinuxEnvironmentPage { page = null }
        "about" -> FullPage("关于 Aurora", { page = null }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsStatus("Aurora", version ?: "开发版本", R.drawable.ic_info)
                Text("你的 Android Agent", style = MaterialTheme.typography.headlineSmall)
                Text("Android 16 及以上 · 开发预览", color = AuroraMuted)
                SettingsDetails("隐私与项目状态", "自备模型密钥，能力按环境启用") {
                    Text("模型密钥由你配置并加密保存在本机。")
                    Text("系统工具按当前权限与实际自检结果开放，开发预览不代表全部场景已验收。")
                }
            }
        }
    }
}

@Composable internal fun FullPage(title: String, dismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        } }
        val scroll = CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val density = LocalDensity.current
        com.itos.coui.layout.Scaffold(
            modifier = Modifier.fillMaxSize().imePadding().nestedScroll(scroll.nestedScrollConnection),
            topBar = { LargeTopAppBar(title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = { TopAppBarButton(onClick = dismiss) { LocalIcon(R.drawable.ic_arrow_back, "返回") } },
                scrollBehavior = scroll) }) { padding ->
            Box(Modifier.fillMaxSize().padding(scroll.state.contentPadding(padding, density))
                .consumeWindowInsets(padding)) { content() }
        }
    }
}

private data class SettingsEntry(val title: String, val summary: String, val icon: Int, val click: () -> Unit)

@Composable private fun SettingsEntries(title: String, entries: List<SettingsEntry>) {
    Group(title = title) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) Divider(startIndent = 60.dp)
            Item(onClick = entry.click,
                icon = { IconBadge(entry.icon, MaterialTheme.colorScheme.primary) },
                summary = { CouiText(text = entry.summary, kind = CouiTextKind.PreferenceSummary, color = AuroraMuted) }, trailing = { SettingsChevron() }) { CouiText(text = entry.title, kind = CouiTextKind.PreferenceTitle) }
        }
    }
}

@Composable internal fun IconBadge(icon: Int, color: Color) {
    Icon(painterResource(icon), null, tint = if (LocalAppearance.current.state.colorful) color else AuroraMuted)
}
