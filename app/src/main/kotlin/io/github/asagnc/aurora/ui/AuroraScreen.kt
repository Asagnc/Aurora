package io.github.asagnc.aurora.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.ChatViewModel
import kotlinx.coroutines.launch

@Composable fun AuroraScreen(model: ChatViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(false) }
    var metrics by remember { mutableStateOf<String?>(null) }
    var space by remember { mutableStateOf<String?>(null) }
    var conversationFilesOnly by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Connection?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) model.importFiles(uris) }
    fun choose(connection: Connection) {
        if (state.messages.isNotEmpty() && state.selected?.let { CachePolicy.impact(it, connection) } != CacheImpact.UNCHANGED) pending = connection
        else model.selectConnection(connection)
    }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    ModalNavigationDrawer(drawerState = drawer, scrimColor = AuroraInk.copy(alpha = 0.28f), drawerContent = {
        ConversationDrawer(state.sessions, model, onOpen = { id -> draft = ""; model.selectConversation(id); scope.launch { drawer.close() } },
            onProviders = { providers = true }, onSettings = { metrics = "settings" }, onSpace = { conversationFilesOnly = false; space = it })
    }) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ scope.launch { drawer.open() } }) { LocalIcon(R.drawable.ic_menu, "会话抽屉") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("Aurora", style = MaterialTheme.typography.titleLarge) }
                Box {
                    IconButton({ menu = true }) { LocalIcon(R.drawable.ic_more_vert, "会话菜单") }
                    DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surface) {
                        DropdownMenuItem(text = { Text("新会话") }, leadingIcon = { LocalIcon(R.drawable.ic_add, "新会话") },
                            onClick = { menu = false; draft = ""; model.newConversation() })
                        DropdownMenuItem(text = { Text("本会话文件") }, leadingIcon = { LocalIcon(R.drawable.ic_folder_open, "本会话文件") },
                            enabled = state.current != null, onClick = { menu = false; conversationFilesOnly = true; space = "conversation" })
                        DropdownMenuItem(text = { Text("浏览器") }, leadingIcon = { LocalIcon(R.drawable.ic_language, "浏览器") },
                            enabled = state.current != null, onClick = { menu = false; browser = true })
                    }
                }
            }
        }, bottomBar = {
            ChatComposer(state, draft, { draft = it }, { picker.launch(arrayOf("*/*")) }, ::choose, { models = true },
                { if (state.running) model.stop() else if (model.send(draft)) draft = "" })
        }) { padding ->
            if (state.messages.isEmpty() && state.streaming.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.aurora_artwork), "Aurora", Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)))
                    Spacer(Modifier.height(20.dp))
                    Text("从一个想法开始", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("让 Aurora 帮你处理手机上的任务", color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
                    if (state.connections.isEmpty()) TextButton({ providers = true }, Modifier.padding(top = 12.dp)) { Text("添加自己的 API 连接") }
                }
            } else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                if (state.olderBefore != null) item { TextButton({ model.loadOlderMessages() }) { Text("更早的消息") } }
                items(state.messages, key = { it.id }) { message -> MessageBubble(message) }
                if (state.toolRuns.isNotEmpty()) item { ToolActivity(state.toolRuns) }
                if (state.streaming.isNotEmpty()) item { MessageBubble(ChatMessage(role = "assistant", text = state.streaming)) }
                if (state.stage.isNotEmpty()) item { Text(state.stage, color = AuroraMuted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (models) ModelPickerSheet(state.connections, state.selected, ::choose, { models = false; providers = true }) { models = false }
    if (providers) ProviderDialog(state.connections, ::choose, model::saveConnection) { providers = false }
    space?.let { SpaceBrowserSheet(model, it, conversationFilesOnly) { space = null } }
    if (browser) state.current?.id?.let { BrowserPage(model.browser, it) { browser = false } }
    pending?.let { next -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("切换配置") },
        text = { Text(CachePolicy.switchExplanation(state.selected?.let { CachePolicy.impact(it, next) } ?: CacheImpact.NEW_SCOPE)) },
        confirmButton = { TextButton({ model.selectConnection(next); pending = null }) { Text("确认切换") } },
        dismissButton = { TextButton({ pending = null }) { Text("取消") } }) }
    metrics?.let { kind -> if (kind == "settings") SettingsDialog(model,
        { metrics = null; providers = true }, { metrics = null; conversationFilesOnly = false; space = "workspace" }) { metrics = null }
        }
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("操作未完成") }, text = { Text(error) },
        confirmButton = { TextButton(model::clearError) { Text("知道了") } }) }
}

@Composable internal fun LocalIcon(resource: Int, label: String) { Icon(painterResource(resource), label, Modifier.size(22.dp)) }

@Composable private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top) {
        Surface(Modifier.weight(1f, fill = false), shape = RoundedCornerShape(18.dp), color = if (user) AuroraMint else MaterialTheme.colorScheme.background) {
            SelectionContainer { Text(message.text + if (message.complete) "" else "\n（未完成）", Modifier.padding(if (user) 14.dp else 4.dp), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
