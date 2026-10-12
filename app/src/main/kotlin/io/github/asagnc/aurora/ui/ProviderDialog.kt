package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.R

@Composable internal fun ProviderDialog(connections: List<Connection>, choose: (Connection) -> Unit,
    save: (Connection, String) -> Unit, dismiss: () -> Unit) {
    var adding by remember { mutableStateOf(connections.isEmpty()) }
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(Protocol.CHAT) }
    var auth by remember { mutableStateOf(Authentication.AUTOMATIC) }
    var contextLimit by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (adding) "添加 API 连接" else "选择模型") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (adding) {
                    Text("官方或中转均可。填写 API 基础地址（含版本前缀），模型 ID 使用供应商当前在售型号。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it }, label = { Text("连接名称") }, singleLine = true)
                    OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS API 基础地址") }, singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("模型 ID") }, singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text("API 密钥") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Text("协议", style = MaterialTheme.typography.labelMedium)
                    Protocol.entries.forEach { value -> FilterChip(selected = value == protocol, onClick = { protocol = value }, label = { Text(when (value) {
                        Protocol.CHAT -> "OpenAI Chat Completions"
                        Protocol.RESPONSES -> "OpenAI Responses"
                        Protocol.ANTHROPIC -> "Anthropic Messages"
                    }) }) }
                    Text("认证方式", style = MaterialTheme.typography.labelMedium)
                    Authentication.entries.forEach { value -> FilterChip(selected = value == auth, onClick = { auth = value }, label = { Text(value.name) }) }
                    OutlinedTextField(contextLimit, { contextLimit = it.filter(Char::isDigit) }, label = { Text("上下文上限（可留空）") }, singleLine = true)
                } else connections.forEach { connection ->
                    TextButton(onClick = { choose(connection); dismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Column { Text(connection.name); Text(connection.model, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }, confirmButton = {
            if (adding) TextButton(onClick = {
                save(Connection(name = name.trim(), baseUrl = endpoint.trim(), protocol = protocol, model = model.trim(),
                    contextLimit = contextLimit.toLongOrNull()?.takeIf { it > 0 }, authentication = auth), key)
                key = ""; dismiss()
            }, enabled = name.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank() && key.isNotBlank()) { Text("保存") }
            else TextButton(onClick = { adding = true }) { Text("添加连接") }
        }, dismissButton = { TextButton(onClick = { key = ""; dismiss() }) { LocalIcon(R.drawable.ic_close, "关闭"); Text("关闭") } })
}
