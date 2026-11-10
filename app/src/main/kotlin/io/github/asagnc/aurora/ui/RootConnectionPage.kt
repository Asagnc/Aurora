package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.root.RootPhase
import io.github.asagnc.aurora.runtime.ChatViewModel

@Composable internal fun RootConnectionPage(model: ChatViewModel, dismiss: () -> Unit) {
    val root by model.rootState.collectAsStateWithLifecycle()
    var policy by remember { mutableStateOf(false) }
    val connected = root.phase == RootPhase.READY
    val connecting = root.phase == RootPhase.CONNECTING
    FullPage("Root 权限", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsStatus("当前连接", when (root.phase) {
                RootPhase.READY -> "已连接"
                RootPhase.CONNECTING -> "连接中"
                RootPhase.UNAVAILABLE -> "连接不可用"
                RootPhase.ORDINARY -> "未连接"
            }, R.drawable.ic_shield)
            if (!connected) Text("先在 Root 管理器中允许 Aurora，再连接。", color = AuroraMuted)
            if (connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = { if (connected || connecting) model.disableRoot() else model.enableRoot() },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (connected) "断开 Root" else if (connecting) "取消连接" else "连接 Root")
            }
            if (root.phase == RootPhase.UNAVAILABLE) SettingsNotice(root.detail)
            Group(title = "相关能力") {
                Item(onClick = { policy = true }, icon = { LocalIcon(R.drawable.ic_shield, "Frida") },
                    summary = { Text("当前策略与适配状态") }, trailing = { SettingsChevron() }) { Text("Frida 策略适配") }
            }
            SettingsDetails("授权与连接说明", "APatch、KernelSU、FolkPatch 等管理器") {
                Text("授权和撤销均在 Root 管理器内完成。这里仅连接已授权的 Root 服务，不发起授权弹窗。")
                Text("连接后可使用 Root 命令与终端；Linux、抓包和逆向工具仍需通过当前环境自检。")
                Text(root.detail, color = AuroraMuted)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (policy) FridaPolicyPage { policy = false }
}
