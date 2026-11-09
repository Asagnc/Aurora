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
    com.itos.coui.topappbar.TopAppBar(
        title = { Text(title ?: "Aurora", maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        navigationIcon = { com.itos.coui.topappbar.TopAppBarButton(onClick = drawer) {
            LocalIcon(R.drawable.ic_menu, "会话抽屉")
        } },
        actions = { com.itos.coui.topappbar.TopAppBarButton(onClick = menu) {
            LocalIcon(R.drawable.ic_more_vert, "会话菜单")
        } })
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
        Column(Modifier.widthIn(max = if (user) 310.dp else 600.dp),
            horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
            if (user) Surface(shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer) {
                SelectionContainer {
                    Text(message.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_sparkle), null,
                        Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Aurora", color = AuroraMuted, style = MaterialTheme.typography.labelLarge)
                }
                RichReply(message.text, owner, streaming, openLink)
            }
            if (!message.complete) Text("回复未完成", Modifier.padding(top = 6.dp),
                color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
            if (!streaming) Row(Modifier.padding(top = if (user) 6.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!user) CopyTextButton(message.text, "复制回复")
                message.createdAt?.let { MessageTime(it) }
            }
        }
    }
}
