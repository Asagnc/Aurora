package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ConversationMenuSheet(hasConversation: Boolean, new: () -> Unit,
    files: () -> Unit, browser: () -> Unit, terminal: () -> Unit, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("本会话", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            ConversationMenuItem("新会话", R.drawable.ic_edit, true) { dismiss(); new() }
            ConversationMenuItem("本会话文件", R.drawable.ic_folder_open, hasConversation) { dismiss(); files() }
            ConversationMenuItem("浏览器", R.drawable.ic_language, hasConversation) { dismiss(); browser() }
            ConversationMenuItem("交互终端", R.drawable.ic_terminal, hasConversation) { dismiss(); terminal() }
        }
    }
}

@Composable private fun ConversationMenuItem(label: String, icon: Int, enabled: Boolean, click: () -> Unit) {
    TextButton(click, Modifier.fillMaxWidth(), enabled = enabled, contentPadding = PaddingValues(vertical = 14.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = AuroraInk)) {
        LocalIcon(icon, label)
        Text(label, Modifier.weight(1f).padding(start = 14.dp))
    }
}
