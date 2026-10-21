package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.core.ChatMessage
import io.github.asagnc.aurora.runtime.ChatState

@Composable internal fun ChatHeader(drawer: () -> Unit, menu: () -> Unit, title: String? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(drawer) { LocalIcon(R.drawable.ic_menu, "会话抽屉") }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title ?: "Aurora", style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (title != null) Text("Aurora", color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
        }
        IconButton(menu) { LocalIcon(R.drawable.ic_folder_open, "会话菜单") }
    }
}

@Composable internal fun ChatBody(state: ChatState, modifier: Modifier = Modifier,
    providers: () -> Unit = {}, older: () -> Unit = {}, openArtifact: (String) -> Unit = {}, openLink: (String) -> Unit = {}) {
    if (state.messages.isEmpty() && state.streaming.isEmpty()) {
        Column(modifier.fillMaxSize().padding(horizontal = 26.dp), verticalArrangement = Arrangement.Center) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_sparkle), null,
                Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text("有什么想交给 Aurora？", fontSize = 25.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            Text("从一个问题，到一项需要完成的任务。\n过程与结果，都在这里。",
                color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
            if (state.running || state.taskId != null) AgentTaskStatus(state)
            if (state.connections.isEmpty()) TextButton(providers, contentPadding = PaddingValues(top = 12.dp)) {
                Text("添加自己的 API 连接", style = MaterialTheme.typography.bodySmall)
            }
        }
    } else ChatTimeline(state.current?.id,
        listOf(state.messages.lastOrNull()?.id, state.streaming, state.toolRuns, state.stage), modifier) {
        if (state.olderBefore != null) item(key = "older") { TextButton(older) { Text("更早的消息") } }
        items(state.messages, key = { it.id }) { message -> MessageBubble(message, owner = state.current?.id, openLink = openLink) }
        if (state.toolRuns.isNotEmpty()) item(key = "tools") { ToolActivity(state.toolRuns, openArtifact, state.current?.id) }
        if (state.streaming.isNotEmpty()) item(key = "stream") {
            MessageBubble(ChatMessage(id = "stream", role = "assistant", text = state.streaming), streaming = true, owner = state.current?.id, openLink = openLink)
        }
        if (state.stage.isNotEmpty() || state.taskId != null) item(key = "task") { AgentTaskStatus(state) }
    }
}

@Composable private fun MessageBubble(message: ChatMessage, streaming: Boolean = false, owner: String? = null,
    openLink: (String) -> Unit = {}) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(Modifier.widthIn(max = if (user) 310.dp else 600.dp),
            shape = if (user) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(0.dp),
            color = if (user) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background) {
            Column {
            if (!user) Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_sparkle), null,
                    Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Aurora", color = AuroraMuted, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                if (!streaming) CopyTextButton(message.text, "复制回复")
            }
            if (user) Row(Modifier.align(Alignment.End)) { CopyTextButton(message.text, "复制消息") }
            if (user) SelectionContainer { Text(message.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
            else RichReply(message.text, owner, streaming, openLink)
            if (!message.complete) Text("（未完成）", color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
