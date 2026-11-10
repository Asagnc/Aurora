package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.itos.coui.layout.Item
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ConversationMenuSheet(hasConversation: Boolean, new: () -> Unit,
    files: () -> Unit, browser: () -> Unit, terminal: () -> Unit, dismiss: () -> Unit) {
    AuroraPrompt("本会话", "关闭", dismiss, dismiss) {
        Column(Modifier.fillMaxWidth()) {
            ConversationMenuItem("新会话", R.drawable.ic_edit, true) { dismiss(); new() }
            ConversationMenuItem("本会话文件", R.drawable.ic_folder_open, hasConversation) { dismiss(); files() }
            ConversationMenuItem("浏览器", R.drawable.ic_language, hasConversation) { dismiss(); browser() }
            ConversationMenuItem("交互终端", R.drawable.ic_terminal, hasConversation) { dismiss(); terminal() }
        }
    }
}

@Composable private fun ConversationMenuItem(label: String, icon: Int, enabled: Boolean, click: () -> Unit) {
    Item(onClick = click, enabled = enabled, icon = { LocalIcon(icon, label) }) { Text(label) }
}
