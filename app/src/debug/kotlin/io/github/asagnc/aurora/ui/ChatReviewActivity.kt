package io.github.asagnc.aurora.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.ToolRun
import io.github.asagnc.aurora.runtime.ChatState

/** ADB-only screenshot fixture. Reuses production components without invoking the agent or writing conversations. */
class ChatReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scene = intent.getStringExtra("scene")?.takeIf { it in setOf("empty", "chat", "agent") } ?: "empty"
        setContent {
            AuroraTheme {
                var state by remember { mutableStateOf(reviewState(scene)) }
                var draft by remember { mutableStateOf("") }
                var menu by remember { mutableStateOf(false) }
                var models by remember { mutableStateOf(false) }
                Scaffold(containerColor = MaterialTheme.colorScheme.background,
                    topBar = { Column {
                        ChatHeader({}, { menu = true })
                        Text("设计验收 · 示例数据 · $scene", Modifier.padding(horizontal = 24.dp),
                            color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
                    } }, bottomBar = {
                        ChatComposer(state, draft, { draft = it }, {}, { state = state.copy(selected = it) }, { models = true },
                            { state = state.copy(running = false, stage = "已停止 · 保留已完成记录") })
                    }) { padding -> ChatBody(state, Modifier.padding(padding)) }
                if (menu) ConversationMenuSheet(scene != "empty", {}, {}, {}) { menu = false }
                if (models) ModelPickerSheet(state.connections, state.selected,
                    { state = state.copy(selected = it); models = false }, {}) { models = false }
            }
        }
    }
}

private fun reviewState(scene: String): ChatState {
    val connection = Connection(name = "界面示例", baseUrl = "https://example.invalid", protocol = Protocol.RESPONSES,
        model = "gpt-6-astra", effort = "high", contextLimit = 214000)
    val state = ChatState(connections = listOf(connection), selected = connection)
    if (scene == "empty") return state
    val messages = listOf(ChatMessage(role = "user", text = "比较这两个网页，整理关键差异。"),
        ChatMessage(role = "assistant", text = if (scene == "agent") "正在读取页面，已完成第一份资料的检查。"
            else "可以，把两个页面链接发给我。我会核对资料，再比较内容和关键变化。\n\n最后给你一份差异摘要，并保留对应资料，方便继续分析。"))
    return state.copy(messages = messages, running = scene == "agent", steps = if (scene == "agent") 3 else 0,
        requests = 2, usage = Usage(input = 38500, output = 860, cacheRead = 36190), totalUsage = Usage(output = 860), seconds = 10.0,
        stage = if (scene == "agent") "分析中 · 可查看步骤详情" else "",
        toolRuns = if (scene != "agent") emptyList() else listOf(
            ToolRun("1", "review-1", "browser_open_batch", "completed", "{\"pages\":[\"page-1\",\"page-2\"]}"),
            ToolRun("2", "review-2", "browser_read", "completed", "{\"page\":\"page-1\",\"text\":\"示例页面内容\"}"),
            ToolRun("3", "review-3", "browser_read", "started", null)))
}
