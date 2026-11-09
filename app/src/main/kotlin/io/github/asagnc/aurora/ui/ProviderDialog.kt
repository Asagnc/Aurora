package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ProviderDialog(connections: List<Connection>, choose: (Connection) -> Unit,
    save: (Connection, String) -> Unit, dismiss: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(Protocol.CHAT) }
    var auth by remember { mutableStateOf(Authentication.AUTOMATIC) }
    var contextLimit by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf<ProviderPreset?>(null) }
    var workspace by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    fun usePreset(selected: ProviderPreset?) {
        preset = selected; workspace = ""; key = ""; contextLimit = ""; adding = true; advanced = selected == null
        name = selected?.name.orEmpty(); model = selected?.models?.firstOrNull().orEmpty()
        endpoint = selected?.baseUrl?.takeUnless { selected.requiresWorkspace }.orEmpty()
        protocol = selected?.protocol ?: Protocol.CHAT; auth = selected?.authentication ?: Authentication.AUTOMATIC
    }
    fun back() { key = ""; if (adding) adding = false else dismiss() }
    FullPage(if (adding) "添加 API 连接" else "供应商", ::back) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (adding) {
                    ProviderPresetPicker(preset, ::usePreset)
                    preset?.let { selected ->
                        if (selected.requiresWorkspace) ProviderField("百炼业务空间 ID", workspace, { value ->
                            workspace = value.trim(); endpoint = runCatching { selected.endpoint(workspace) }.getOrDefault("")
                        })
                        PresetModelPicker(selected.models, model) { model = it }
                    }
                    ProviderField("连接名称", name, { name = it })
                    ProviderField("HTTPS API 基础地址", endpoint, { endpoint = it })
                    ProviderField("模型 ID", model, { model = it })
                    ProviderField("你的 API 密钥", key, { key = it }, true)
                    TextButton({ advanced = !advanced }, contentPadding = PaddingValues(0.dp)) { Text(if (advanced) "收起高级设置" else "协议、认证和上下文设置") }
                    if (advanced) {
                        Text("协议", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                        Protocol.entries.forEach { value -> FilterChip(value == protocol, { protocol = value }, label = { Text(when (value) {
                            Protocol.CHAT -> "OpenAI Chat Completions"; Protocol.RESPONSES -> "OpenAI Responses"; Protocol.ANTHROPIC -> "Anthropic Messages"
                        }) }) }
                        Text("认证方式", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                        Authentication.entries.forEach { value -> FilterChip(value == auth, { auth = value }, label = { Text(when (value) {
                            Authentication.AUTOMATIC -> "按协议自动选择"; Authentication.BEARER -> "Bearer"; Authentication.API_KEY -> "x-api-key"; Authentication.API_KEY_HEADER -> "api-key"
                        }) }) }
                        ProviderField("上下文上限（可留空）", contextLimit, { contextLimit = it.filter(Char::isDigit) })
                    }
                    Text("密钥在本机加密保存。", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    SettingsDetails("连接说明", "端点、型号与账号可用性") {
                        preset?.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text("型号目录核查：${ProviderCatalog.checkedOn}。实际可用性取决于账号和所选端点。")
                    }
                } else {
                    Text("使用自己的 API 密钥连接官方服务或中转。", style = MaterialTheme.typography.bodyMedium, color = AuroraMuted)
                    if (connections.isNotEmpty()) SettingsSection("已配置") {
                        connections.forEachIndexed { index, connection ->
                            SegmentedListItem(onClick = { choose(connection); dismiss() },
                                shapes = ListItemDefaults.segmentedShapes(index, connections.size),
                                leadingContent = { ModelBrand(connection.model, 32.dp) },
                                supportingContent = { Text(connection.model) }, trailingContent = { SettingsChevron() }) { Text(connection.name) }
                        }
                    }
                    SettingsSection("官方供应商") {
                        ProviderCatalog.presets.forEachIndexed { index, selected ->
                            SegmentedListItem(onClick = { usePreset(selected) },
                                shapes = ListItemDefaults.segmentedShapes(index, ProviderCatalog.presets.size),
                                leadingContent = { ModelBrand(selected.models.first(), 32.dp) },
                                trailingContent = { SettingsChevron() }) { Text(selected.name) }
                        }
                    }
                }
            }
            if (adding) Button({
                save(Connection(name = name.trim(), baseUrl = endpoint.trim(), protocol = protocol, model = model.trim(),
                    contextLimit = contextLimit.toLongOrNull()?.takeIf { it > 0 }, authentication = auth), key)
                key = ""; dismiss()
            }, Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank() && key.isNotBlank()) { Text("保存连接") }
            else OutlinedButton({ usePreset(null) }, Modifier.fillMaxWidth()) { Text("添加自定义中转 / 其他地域") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun ProviderField(label: String, value: String, change: (String) -> Unit, secret: Boolean = false) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium, label = { Text(label) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None)
}
