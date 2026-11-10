package io.github.asagnc.aurora.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeInput() { focus.clearFocus(); keyboard?.hide() }
    LaunchedEffect(drawer.targetValue) { closeInput() }
    var providers by remember { mutableStateOf(false) }
    var settingsReturn by remember { mutableStateOf<String?>(null) }
    var skills by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var browser by remember { mutableStateOf(false) }
    var terminal by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(false) }
    var metrics by remember { mutableStateOf<String?>(null) }
    var space by remember { mutableStateOf<String?>(null) }
    var artifactFolder by remember { mutableStateOf("") }
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
    fun returnToSettings(destination: String) {
        if (settingsReturn == destination) { settingsReturn = null; metrics = "settings" }
    }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    ModalNavigationDrawer(drawerState = drawer, scrimColor = AuroraInk.copy(alpha = 0.28f), drawerContent = {
        ConversationDrawer(state.sessions, model, onOpen = { id -> closeInput(); draft = ""; model.selectConversation(id); scope.launch { drawer.close() } },
            onProviders = { closeInput(); providers = true }, onSettings = { closeInput(); metrics = "settings" }, onSpace = {
                closeInput()
                if (it == "resources") skills = true else { conversationFilesOnly = false; space = it }
            })
    }) {
        com.itos.coui.layout.Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
            ChatHeader({ scope.launch { drawer.open() } }, { menu = true }, state.current?.title)
        }, bottomBar = {
            ChatComposer(state, draft, { draft = it }, { picker.launch(arrayOf("*/*")) }, ::choose, { models = true },
                ::sendOrStop)
        }) { padding ->
            ChatBody(state, Modifier.padding(padding), { providers = true }, model::loadOlderMessages, { path ->
                artifactFolder = path.substringBeforeLast('/', "")
                conversationFilesOnly = true
                space = "conversation"
            }, { address -> state.current?.id?.let { owner ->
                model.browser.open(owner)?.let { tab -> model.browser.navigate(tab, address); browser = true }
            } })
        }
    }
    if (menu) ConversationMenuSheet(state.current != null, { draft = ""; model.newConversation() },
        { conversationFilesOnly = true; space = "conversation" }, { browser = true }, { terminal = true }) { menu = false }
    if (models) ModelPickerSheet(state.connections, state.selected, ::choose, { models = false; providers = true }) { models = false }
    if (providers) ProviderDialog(state.connections, model::storeConnection, model::connectionModels, model::removeConnection) { providers = false; returnToSettings("providers") }
    if (skills) SkillsPage { skills = false }
    space?.let { SpaceBrowserSheet(model, it, conversationFilesOnly, artifactFolder) { space = null; artifactFolder = ""; returnToSettings("files") } }
    if (browser) state.current?.id?.let { BrowserPage(model.browser, it) { browser = false } }
    if (terminal) state.current?.id?.let { TerminalPage(it) { terminal = false } }
    metrics?.let { kind -> if (kind == "settings") SettingsDialog(model,
        { settingsReturn = "providers"; metrics = null; providers = true }, { settingsReturn = "files"; metrics = null; conversationFilesOnly = false; space = "workspace" }) { metrics = null }
        }
    pending?.let { next -> AuroraPrompt("切换配置", "确认切换",
        { model.selectConnection(next); pending = null }, { pending = null },
        message = CachePolicy.switchExplanation(state.selected?.let { CachePolicy.impact(it, next) } ?: CacheImpact.NEW_SCOPE),
        cancelLabel = "取消") }
    state.error?.let { error -> AuroraPrompt("操作未完成", "知道了", model::clearError, model::clearError, message = error) }

}

@Composable internal fun LocalIcon(resource: Int, label: String) { Icon(painterResource(resource), label, Modifier.size(22.dp)) }
