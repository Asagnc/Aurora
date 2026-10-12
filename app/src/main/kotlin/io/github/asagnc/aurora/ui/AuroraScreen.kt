package io.github.asagnc.aurora.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.runtime.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AuroraScreen(model: ChatViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(false) }
    var metrics by remember { mutableStateOf<String?>(null) }
    var draft by remember(state.current?.id) { mutableStateOf("") }
    var pending by remember { mutableStateOf<Connection?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) model.importFiles(uris) }
    fun choose(connection: Connection) {
        if (state.messages.isNotEmpty() && state.selected?.let { CachePolicy.impact(it, connection) } != CacheImpact.UNCHANGED) pending = connection
        else model.selectConnection(connection)
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ConversationDrawer(state.sessions, model, onOpen = { id -> model.selectConversation(id); scope.launch { drawer.close() } },
            onProviders = { providers = true }, onSettings = { metrics = "settings" })
    }) {
        Scaffold(topBar = { TopAppBar(title = { Text("Aurora") }, navigationIcon = {
            IconButton(onClick = { scope.launch { drawer.open() } }) { LocalIcon(R.drawable.ic_menu, "会话抽屉") }
        }, actions = { IconButton(onClick = { model.newConversation() }) { LocalIcon(R.drawable.ic_add, "新对话") } }) },
            bottomBar = {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 1.dp) {
                        Column(Modifier.padding(12.dp)) {
                            if (state.attachments.isNotEmpty()) Text("已导入 ${state.attachments.size} 个文件", style = MaterialTheme.typography.labelMedium)
                            TextField(value = draft, onValueChange = { draft = it }, placeholder = { Text("给 Aurora 发消息…") },
                                modifier = Modifier.fillMaxWidth(), maxLines = 6,
                                colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                IconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.running) { LocalIcon(R.drawable.ic_attach_file, "导入文件") }
                                EffortMenu(state.selected, ::choose)
                                TextButton(onClick = { providers = true }) { Text(state.selected?.model ?: "选择模型", maxLines = 1) }
                                FilledIconButton(onClick = { if (state.running) model.stop() else if (model.send(draft)) draft = "" }) {
                                    LocalIcon(if (state.running) R.drawable.ic_stop else R.drawable.ic_send, if (state.running) "停止生成" else "发送")
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val speed = state.usage.output?.takeIf { state.seconds > 0 }?.let { "≈%.1f tok/s".format(it / state.seconds) } ?: "— tok/s"
                        TextButton(onClick = { metrics = "speed" }) { Text(speed, style = MaterialTheme.typography.labelMedium) }
                        TextButton(onClick = { metrics = "cache" }) { Text(state.usage.cacheShare?.let { "缓存 %.1f%%".format(it * 100) } ?: "缓存 —", style = MaterialTheme.typography.labelMedium) }
                        TextButton(onClick = { metrics = "context" }) { Text(state.usage.input?.let { "上下文 $it" } ?: "上下文 —", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.olderBefore != null) item { TextButton(onClick = { model.loadOlderMessages() }) { Text("更早的消息") } }
                if (state.messages.isEmpty()) item { Text("让 Aurora 帮你处理手机上的任务。先添加自己的 API 连接。", Modifier.padding(vertical = 32.dp)) }
                items(state.messages, key = { it.id }) { message ->
                    Surface(shape = MaterialTheme.shapes.large, color = if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        SelectionContainer { Text(message.text + if (message.complete) "" else "\n（未完成）", Modifier.fillMaxWidth().padding(16.dp)) }
                    }
                }
                if (state.streaming.isNotEmpty()) item { SelectionContainer { Text(state.streaming, Modifier.padding(16.dp)) } }
                if (state.stage.isNotEmpty()) item { Text(state.stage, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 16.dp)) }
            }
        }
    }
    if (providers) ProviderDialog(state.connections, ::choose, model::saveConnection) { providers = false }
    pending?.let { next -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("切换配置") },
        text = { Text("此变更可能使缓存重新建立；中转的保留行为尚不能确认。当前任务保持原配置，变更用于下一轮。") },
        confirmButton = { TextButton(onClick = { model.selectConnection(next); pending = null }) { Text("确认切换") } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }) }
    metrics?.let { kind -> MetricDialog(kind, state) { metrics = null } }
    state.error?.let { error -> AlertDialog(onDismissRequest = model::clearError, title = { Text("操作未完成") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = model::clearError) { Text("知道了") } }) }
}

@Composable internal fun LocalIcon(resource: Int, label: String) { Icon(painterResource(resource), label) }

@Composable private fun EffortMenu(connection: Connection?, choose: (Connection) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = connection != null) { Text("思考 · ${connection?.effort ?: "auto"}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            connection?.let { current -> CachePolicy.supportedEfforts(current).forEach { effort ->
                DropdownMenuItem(text = { Text(effort) }, onClick = { choose(current.copy(effort = effort)); expanded = false })
            } }
        }
    }
}
