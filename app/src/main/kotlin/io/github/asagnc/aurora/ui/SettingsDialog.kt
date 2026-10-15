package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.root.RootPhase
import io.github.asagnc.aurora.runtime.ChatViewModel

@Composable internal fun SettingsDialog(model: ChatViewModel, dismiss: () -> Unit) {
    val root by model.rootState.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = dismiss, title = { Text("Aurora 设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("版本 0.1.1 · Android 16+")
            Text(root.detail)
            if (root.phase == RootPhase.READY || root.phase == RootPhase.CONNECTING) {
                TextButton(onClick = model::disableRoot) { Text("断开 Root，使用普通模式") }
            } else TextButton(onClick = model::enableRoot) { Text("授权并连接 Root") }
            Text("未授权 Root 也可使用对话和文件工具。授权由手机 Root 管理器处理。")
            Text("文件分为会话空间、只读共享资源、保留工作区。删除会话保留共享内容和用户原件。")
            Text("可用工具：批量读取、批量编辑、目录列举。API 密钥通过 Android Keystore 加密保存。")
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("关闭") } })
}
