package io.github.asagnc.aurora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.R
import androidx.activity.compose.BackHandler

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
    ModalBottomSheet(onDismissRequest = { key = ""; dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        BackHandler(enabled = adding) { back() }
        Column(Modifier.fillMaxHeight(0.88f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (adding) IconButton(::back) { LocalIcon(R.drawable.ic_arrow_back, "返回供应商") }
                Text(if (adding) "添加 API 连接" else "供应商", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton({ key = ""; dismiss() }) { LocalIcon(R.drawable.ic_close, "关闭") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (adding) {
                    ProviderPresetPicker(preset, ::usePreset)
                    preset?.let { selected ->
                        if (selected.note.isNotEmpty()) Text(selected.note, style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
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
                    Text("密钥仅在本机加密保存。型号核查：${ProviderCatalog.checkedOn}；实际可用性取决于账户权限。", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                } else {
                    Text("使用自己的 API 密钥连接官方服务或中转。", style = MaterialTheme.typography.bodyMedium, color = AuroraMuted)
                    if (connections.isNotEmpty()) Text("已配置", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    connections.forEach { connection -> Surface(onClick = { choose(connection); dismiss() }, shape = RoundedCornerShape(16.dp), color = AuroraMint) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            ModelBrand(connection.model, 32.dp); Spacer(Modifier.width(12.dp))
                            Column { Text(connection.name, style = MaterialTheme.typography.titleMedium); Text(connection.model, style = MaterialTheme.typography.bodySmall, color = AuroraMuted) }
                        }
                    } }
                    Text("官方供应商", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    ProviderCatalog.presets.forEach { selected -> Surface(onClick = { usePreset(selected) }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, AuroraBorder)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            ModelBrand(selected.models.first(), 32.dp); Spacer(Modifier.width(12.dp))
                            Text(selected.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("添加连接", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                        }
                    } }
                }
            }
            if (adding) Button({
                save(Connection(name = name.trim(), baseUrl = endpoint.trim(), protocol = protocol, model = model.trim(),
                    contextLimit = contextLimit.toLongOrNull()?.takeIf { it > 0 }, authentication = auth), key)
                key = ""; dismiss()
            }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp),
                enabled = name.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank() && key.isNotBlank()) { Text("保存连接") }
            else OutlinedButton({ usePreset(null) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text("添加自定义中转 / 其他地域") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun ProviderField(label: String, value: String, change: (String) -> Unit, secret: Boolean = false) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium, label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = AuroraBorder, focusedBorderColor = MaterialTheme.colorScheme.primary))
}
