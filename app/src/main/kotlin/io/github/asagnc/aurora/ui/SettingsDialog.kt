package io.github.asagnc.aurora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
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
    var appearance by remember { mutableStateOf(false) }
    var rootDetails by remember { mutableStateOf(false) }
    var environment by remember { mutableStateOf<Boolean?>(null) }
    var about by remember { mutableStateOf(false) }
    var skills by remember { mutableStateOf(false) }
    var linux by remember { mutableStateOf(false) }
    FullPage("设置", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(painterResource(R.drawable.aurora_artwork), "Aurora", Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Aurora", style = MaterialTheme.typography.headlineSmall)
                    Text("你的 Android Agent", color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SettingsGroup("模型与连接") {
                SettingsRow("供应商与 API", "官方服务与自定义中转", R.drawable.ic_hub, Color(0xFF347EDD), click = providers)
            }
            SettingsGroup("设备与执行") {
                LocalNetworkPermissionRow()
                SettingsRow("Skills", "工作流、版本与更新", R.drawable.ic_hub, Color(0xFF347EDD)) { skills = true }
                SettingsRow("Linux 环境", "基础环境、安装与版本", R.drawable.ic_terminal, Color(0xFF347EDD)) { linux = true }
                SettingsRow("Root 权限", root.phase.let { if (it == RootPhase.READY) "已连接" else if (it == RootPhase.CONNECTING) "连接中" else "未连接" },
                    R.drawable.ic_shield, Color(0xFF26A879)) { rootDetails = true }
                SettingsRow("执行环境", "原生命令与交互终端 · 普通 / Root", R.drawable.ic_terminal, Color(0xFF347EDD)) { environment = false }
                SettingsRow("工具能力", "文件、浏览器、图文、Skills 与终端", R.drawable.ic_extension, Color(0xFF9270D7)) { environment = true }
            }
            SettingsGroup("数据与存储") {
                SettingsRow("空间与文件", "会话文件、共享资源与保留工作区", R.drawable.ic_folder_open, Color(0xFF27A899), click = files)
            }
            SettingsGroup("偏好") {
                SettingsRow("外观与配色", LocalAppearance.current.state.let { if (it.dynamic) "系统动态配色" else it.palette },
                    R.drawable.ic_palette, Color(0xFF8B58D0)) { appearance = true }
                SettingsRow("关于 Aurora", "${version ?: "开发版本"} · Android 16+", R.drawable.ic_info, Color(0xFF4086CC)) { about = true }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    environment?.let { ExecutionStatusPage(model.commandsAvailable, it) { environment = null } }
    if (appearance) AppearanceSheet { appearance = false }
    if (rootDetails) AlertDialog(onDismissRequest = { rootDetails = false }, title = { Text("Root 连接") },
        text = { Text(root.detail + "\n\n连接后，新任务可调用 Root 命令与交互终端。Linux、抓包和逆向能力按当前环境的实际资格开放。") },
        confirmButton = { TextButton({
            if (root.phase == RootPhase.READY || root.phase == RootPhase.CONNECTING) model.disableRoot() else model.enableRoot()
        }) { Text(if (root.phase == RootPhase.READY || root.phase == RootPhase.CONNECTING) "断开 Root" else "授权并连接 Root") } },
        dismissButton = { TextButton({ rootDetails = false }) { Text("关闭") } })
    if (about) AlertDialog(onDismissRequest = { about = false }, title = { Text("Aurora · 开发版本") },
        text = { Text("提供对话、文件、浏览器、图文消息、后台 Agent、Skills、HTTP 请求、普通 / Root 终端，以及按环境资格开放的 Linux、抓包与逆向工具。模型 API 密钥由用户自备并加密保存。") },
        confirmButton = { TextButton({ about = false }) { Text("关闭") } })
    if (skills) SkillsPage { skills = false }
    if (linux) LinuxEnvironmentPage { linux = false }
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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(dismiss) { LocalIcon(R.drawable.ic_arrow_back, "返回") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) { Column(content = content) }
    }
}

@Composable private fun SettingsRow(title: String, summary: String, icon: Int, color: Color, click: () -> Unit) {
    ListItem(headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(summary, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { IconBadge(icon, color) }, trailingContent = { Text("›", color = AuroraMuted) },
        modifier = Modifier.clickable(onClick = click), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}

@Composable internal fun IconBadge(icon: Int, color: Color) {
    val tint = if (LocalAppearance.current.state.colorful) color else AuroraMuted
    Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.09f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(22.dp), tint = tint)
    }
}
