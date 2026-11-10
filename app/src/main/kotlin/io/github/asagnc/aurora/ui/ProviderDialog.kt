package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Divider
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ProviderDialog(connections: List<Connection>,
    save: suspend (Connection, String) -> Unit,
    fetch: suspend (Connection, String) -> List<AvailableModel>,
    remove: suspend (String) -> Unit, dismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Connection?>(null) }
    var draftId by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<AvailableModel>>(emptyList()) }
    var key by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(Protocol.CHAT) }
    var auth by remember { mutableStateOf(Authentication.AUTOMATIC) }
    var contextLimit by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf<ProviderPreset?>(null) }
    var workspace by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var modelTab by remember { mutableStateOf(false) }
    fun usePreset(selected: ProviderPreset?) {
        editing = null; draftId = java.util.UUID.randomUUID().toString(); models = emptyList(); failure = null
        preset = selected; workspace = ""; key = ""; contextLimit = ""; adding = true; advanced = false; modelTab = false
        name = selected?.name.orEmpty(); model = ""
        endpoint = selected?.baseUrl?.takeUnless { selected.requiresWorkspace }.orEmpty()
        protocol = selected?.protocol ?: Protocol.CHAT; auth = selected?.authentication ?: Authentication.AUTOMATIC
    }
    fun edit(connection: Connection) {
        modelTab = false; editing = connection; draftId = connection.id; preset = null; workspace = ""; key = ""
        name = connection.name; endpoint = connection.baseUrl; protocol = connection.protocol; auth = connection.authentication
        model = connection.model; models = connection.models; contextLimit = connection.contextLimit?.toString().orEmpty()
        failure = null; advanced = false; adding = true
    }
    fun back() { if (!busy) { key = ""; failure = null; if (adding) adding = false else dismiss() } }
    FullPage(if (adding) { if (editing != null) "编辑连接" else preset?.name ?: "自定义中转" } else "供应商", ::back, compact = adding) {
      CompositionLocalProvider(LocalFormEnabled provides !busy) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (adding) {
                    if (preset != null) Box(Modifier.padding(horizontal = 16.dp)) { ProviderPresetPicker(preset, ::usePreset) }
                    ProviderTabs(modelTab, !busy) { modelTab = it }
                    if (!modelTab) {
                    Group(title = "连接信息") {
                      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    preset?.let { selected ->
                        if (selected.requiresWorkspace) ProviderField("百炼业务空间 ID", workspace, { value ->
                            workspace = value.trim(); endpoint = runCatching { selected.endpoint(workspace) }.getOrDefault("")
                        })
                    }
                    ProviderField("连接名称", name, { name = it })
                    ProviderField("HTTPS API 基础地址", endpoint, { endpoint = it; model = ""; models = emptyList() })
                    ProviderField(if (editing == null) "你的 API 密钥" else "替换密钥（留空保留）", key, { key = it; models = emptyList(); model = "" }, true)
                      }
                    }
                    Group(title = "连接方式") {
                        ProviderChoice("API 协议", protocol, Protocol.entries, { protocol = it; model = ""; models = emptyList() }) { when (it) {
                            Protocol.CHAT -> "Chat Completions"; Protocol.RESPONSES -> "Responses"; Protocol.ANTHROPIC -> "Anthropic Messages"
                        } }
                        Divider()
                        Item(onClick = { advanced = !advanced }, enabled = !busy, summary = { Text("认证方式与上下文上限", color = AuroraMuted, style = MaterialTheme.typography.bodySmall) },
                            trailing = { SettingsChevron() }) { Text("高级设置") }
                    }
                    }
                    ProviderModels(endpoint, key, protocol, auth, model, { model = it }, models, { models = it },
                        draftId, editing != null, fetch, visible = modelTab)
                    if (!modelTab && advanced) Group(title = "高级设置") {
                      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProviderChoice("认证方式", auth, Authentication.entries, { auth = it; model = ""; models = emptyList() }) { when (it) {
                            Authentication.AUTOMATIC -> "自动"; Authentication.BEARER -> "Bearer"; Authentication.API_KEY -> "x-api-key"; Authentication.API_KEY_HEADER -> "api-key"
                        } }
                        ProviderField("上下文上限（可留空）", contextLimit, { contextLimit = it.filter(Char::isDigit) })
                    }
                    }
                    if (!modelTab) Text("密钥在本机加密保存。", Modifier.padding(horizontal = 32.dp), style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    if (!modelTab) Box(Modifier.padding(horizontal = 16.dp)) { SettingsDetails("连接说明", "端点与型号") {
                        preset?.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text("型号目录核查：${ProviderCatalog.checkedOn}。实际可用性取决于账号和所选端点。")
                    } }
                } else {
                    Text("连接自己的模型服务", Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium, color = AuroraMuted)
                    if (connections.isNotEmpty()) Group(title = "我的连接") {
                        connections.forEachIndexed { index, connection ->
                            if (index > 0) Divider(startIndent = 60.dp)
                            Item(onClick = { edit(connection) },
                                icon = { ModelBrand(connection.model, 28.dp) },
                                summary = { Text(connection.model, color = AuroraMuted, style = MaterialTheme.typography.bodySmall) },
                                trailing = { SettingsChevron() }) { Text(connection.name) }
                        }
                    }
                    Group(title = "官方供应商") {
                        ProviderCatalog.presets.forEachIndexed { index, selected ->
                            if (index > 0) Divider(startIndent = 60.dp)
                            Item(onClick = { usePreset(selected) },
                                icon = { ModelBrand(selected.models.first(), 28.dp) },
                                trailing = { SettingsChevron() }) { Text(selected.name) }
                        }
                    }
                }
            }
            failure?.let { Text(it, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
            if (adding) {
                if (editing != null) TextButton({ deleting = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除连接") }
                if (!modelTab) Button({ modelTab = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !busy && name.isNotBlank() && endpoint.isNotBlank() && (key.isNotBlank() || editing != null)) {
                    Text("下一步：选择模型")
                } else Button({
                    busy = true; failure = null
                    scope.launch {
                        try {
                            save(Connection(id = draftId, name = name.trim(), baseUrl = endpoint.trim(), protocol = protocol,
                                model = model.trim(), contextLimit = contextLimit.toLongOrNull()?.takeIf { it > 0 },
                                authentication = auth, effort = editing?.effort ?: "auto", enabled = editing?.enabled ?: true,
                                official = preset?.let { runCatching { it.endpoint(workspace) == endpoint.trim() }.getOrDefault(false) }
                                    ?: (editing?.official == true && editing?.baseUrl == endpoint.trim()), models = models), key)
                            key = ""; dismiss()
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (caught: Exception) { failure = providerError(caught) }
                        finally { busy = false }
                    }
                }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !busy && name.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank() && (key.isNotBlank() || editing != null)) {
                    Text(if (busy) "正在保存" else "保存连接 · ${models.size.coerceAtLeast(1)} 个模型")
                }
            }
            else OutlinedButton({ usePreset(null) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("添加自定义中转 / 其他地域") }
            Spacer(Modifier.height(16.dp))
        }
      }
    }
    if (deleting) AuroraPrompt("删除这个连接？", "删除", {
        val id = editing?.id ?: return@AuroraPrompt
        deleting = false; busy = true; failure = null
        scope.launch {
            try { remove(id); key = ""; adding = false; editing = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (caught: Exception) { failure = providerError(caught) }
            finally { busy = false }
        }
    }, { deleting = false }, message = "会移除这个连接和它保存在本机的密钥。已有对话不会删除。", cancelLabel = "取消")
}

@Composable private fun ProviderField(label: String, value: String, change: (String) -> Unit, secret: Boolean = false) {
    var revealed by remember { mutableStateOf(false) }
    AuroraInput(value, change, Modifier.fillMaxWidth(), singleLine = true, label = label,
        keyboardOptions = KeyboardOptions(keyboardType = when {
            secret -> KeyboardType.Password
            label.contains("地址") -> KeyboardType.Uri
            else -> KeyboardType.Text
        }, autoCorrectEnabled = !secret),
        trailingIcon = if (secret) ({ TextButton({ revealed = !revealed }) { Text(if (revealed) "隐藏" else "显示") } }) else null,
        visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None)
}

@Composable private fun <T> ProviderChoice(title: String, selected: T, options: List<T>, choose: (T) -> Unit,
    label: (T) -> String) {
    AuroraChoice(title, selected, options, choose, label = label)
}

private fun providerError(failure: Exception): String = when (failure) {
    is IllegalStateException, is IllegalArgumentException -> failure.message?.takeIf { message ->
        message.startsWith("请") || message.startsWith("网关") || message.startsWith("连接") ||
            message.startsWith("模型") || message.startsWith("密钥")
    } ?: "配置格式不正确，请检查后重试"
    else -> "操作未完成，请稍后重试；填写的内容已保留"
}
