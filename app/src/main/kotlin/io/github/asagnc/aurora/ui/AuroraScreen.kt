package io.github.asagnc.aurora.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
    var models by remember { mutableStateOf(false) }
    var metrics by remember { mutableStateOf<String?>(null) }
    var space by remember { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<Connection?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) model.importFiles(uris) }
    fun choose(connection: Connection) {
        if (state.messages.isNotEmpty() && state.selected?.let { CachePolicy.impact(it, connection) } != CacheImpact.UNCHANGED) pending = connection
        else model.selectConnection(connection)
    }
    ModalNavigationDrawer(drawerState = drawer, scrimColor = AuroraInk.copy(alpha = 0.28f), drawerContent = {
        ConversationDrawer(state.sessions, model, onOpen = { id -> draft = ""; model.selectConversation(id); scope.launch { drawer.close() } },
            onProviders = { providers = true }, onSettings = { metrics = "settings" }, onSpace = { space = it },
            onNew = { draft = ""; model.newConversation(); scope.launch { drawer.close() } })
    }) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ scope.launch { drawer.open() } }) { LocalIcon(R.drawable.ic_menu, "会话抽屉") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("Aurora", style = MaterialTheme.typography.titleLarge) }
                IconButton({ draft = ""; model.newConversation() }) { LocalIcon(R.drawable.ic_add, "新对话") }
            }
        }, bottomBar = {
            ChatComposer(state, draft, { draft = it }, { picker.launch(arrayOf("*/*")) }, ::choose, { models = true },
                { if (state.running) model.stop() else if (model.send(draft)) draft = "" }, { metrics = it })
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
                if (state.streaming.isNotEmpty()) item { MessageBubble(ChatMessage(role = "assistant", text = state.streaming)) }
                if (state.stage.isNotEmpty()) item { Text(state.stage, color = AuroraMuted, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (models) ModelPickerSheet(state.connections, state.selected, ::choose, { models = false; providers = true }) { models = false }
    if (providers) ProviderDialog(state.connections, ::choose, model::saveConnection) { providers = false }
    space?.let { SpaceBrowserSheet(model, it) { space = null } }
    pending?.let { next -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("切换配置") },
        text = { Text(CachePolicy.switchExplanation(state.selected?.let { CachePolicy.impact(it, next) } ?: CacheImpact.NEW_SCOPE)) },
        confirmButton = { TextButton({ model.selectConnection(next); pending = null }) { Text("确认切换") } },
        dismissButton = { TextButton({ pending = null }) { Text("取消") } }) }
    metrics?.let { kind -> if (kind == "settings") SettingsDialog(model) { metrics = null } else MetricDialog(kind, state) { metrics = null } }
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("操作未完成") }, text = { Text(error) },
        confirmButton = { TextButton(model::clearError) { Text("知道了") } }) }
}

@Composable internal fun LocalIcon(resource: Int, label: String) { Icon(painterResource(resource), label, Modifier.size(22.dp)) }

@Composable private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top) {
        if (!user) {
            Image(painterResource(R.drawable.aurora_artwork), "Aurora", Modifier.padding(top = 4.dp).size(28.dp).clip(RoundedCornerShape(9.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Surface(Modifier.widthIn(max = 300.dp), shape = RoundedCornerShape(20.dp), color = if (user) AuroraMint else MaterialTheme.colorScheme.surface,
            border = if (user) null else BorderStroke(1.dp, AuroraBorder)) {
            SelectionContainer { Text(message.text + if (message.complete) "" else "\n（未完成）", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
        }
    }
}
