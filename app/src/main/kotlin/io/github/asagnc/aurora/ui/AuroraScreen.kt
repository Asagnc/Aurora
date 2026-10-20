package io.github.asagnc.aurora.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    val context = LocalContext.current
    var notificationRequested by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun sendOrStop() {
        if (state.running) model.stop()
        else if (model.send(draft)) {
            draft = ""
            if (!notificationRequested && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationRequested = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
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
            ChatHeader({ scope.launch { drawer.open() } }, { menu = true }, state.current?.title)
        }, bottomBar = {
            ChatComposer(state, draft, { draft = it }, { picker.launch(arrayOf("*/*")) }, ::choose, { models = true },
                ::sendOrStop)
        }) { padding ->
            ChatBody(state, Modifier.padding(padding), { providers = true }, model::loadOlderMessages)
        }
    }
    if (menu) ConversationMenuSheet(state.current != null, { draft = ""; model.newConversation() },
        { conversationFilesOnly = true; space = "conversation" }, { browser = true }) { menu = false }
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
