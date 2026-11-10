package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.*
import com.itos.coui.layout.Item

@Composable internal fun ProviderModels(endpoint: String, token: String, protocol: Protocol,
    authentication: Authentication, selected: String, choose: (String) -> Unit,
    selectedModels: List<AvailableModel>, catalogue: (List<AvailableModel>) -> Unit,
    connectionId: String, storedCredential: Boolean,
    fetch: suspend (Connection, String) -> List<AvailableModel>) {
    val enabled = LocalFormEnabled.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var loading by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<AvailableModel>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    DisposableEffect(endpoint, token, protocol, authentication) {
        revision++; job?.cancel(); loading = false; entries = null; error = null; picker = false
        onDispose { job?.cancel() }
    }
    Column {
        Item(onClick = {
            if (!loading) job = scope.launch {
                val ticket = revision
                loading = true; error = null
                try {
                    entries = fetch(Connection(id = connectionId, name = "", baseUrl = endpoint.trim(),
                        protocol = protocol, model = "", authentication = authentication), token)
                    if (entries!!.isEmpty()) error = "服务返回的模型目录为空" else picker = true
                } catch (_: TimeoutCancellationException) { if (ticket == revision) error = "获取模型超时，可以重试" }
                catch (cancel: CancellationException) { throw cancel }
                catch (failure: ModelDirectoryFailure) { if (ticket == revision) error = failure.message }
                catch (_: Exception) { if (ticket == revision) error = "请检查基础地址和密钥格式后重试" }
                finally { if (ticket == revision) loading = false }
            }
        }, enabled = enabled && endpoint.isNotBlank() && (token.isNotBlank() || storedCredential) && !loading,
            summary = { Text(if (loading) "正在读取服务提供的模型目录" else "填写地址和密钥后，从服务获取可选模型",
                style = MaterialTheme.typography.bodySmall, color = AuroraMuted) },
            trailing = { if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else SettingsChevron() }) {
            Text(if (entries == null) "获取模型" else "重新获取模型")
        }
        if (loading) TextButton({ job?.cancel() }) { Text("取消获取") }
        if ((entries ?: selectedModels).isNotEmpty()) Item(onClick = { picker = true }, enabled = enabled,
            summary = { Text(selected.ifEmpty { "搜索并选择，不需要记住模型名称" }, color = AuroraMuted, style = MaterialTheme.typography.bodySmall) },
            trailing = { SettingsChevron() }) { Text("选择模型 · ${(entries ?: selectedModels).size} 个") }
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall) }
        TextButton({ manual = !manual }, Modifier.padding(horizontal = 8.dp), enabled = enabled) { Text(if (manual) "收起手动添加" else "手动添加模型") }
        if (manual) AuroraInput(selected, { choose(it); catalogue(emptyList()) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            singleLine = true, label = { Text("模型 ID") })
        else if (selected.isNotEmpty() && entries == null) Text(selected, Modifier.padding(16.dp), color = AuroraMuted)
    }
    if (picker) ModelDirectoryPicker(entries ?: selectedModels, selectedModels, { picked ->
        catalogue(picked); choose(picked.first().id); picker = false
    }) { picker = false }
}

@Composable private fun ModelDirectoryPicker(entries: List<AvailableModel>, selected: List<AvailableModel>,
    choose: (List<AvailableModel>) -> Unit, dismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(selected.map { it.id }.toSet()) }
    val filtered = remember(entries, query) { entries.filter { it.id.contains(query, true) || it.name.contains(query, true) } }
    FullPage("选择模型", dismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuroraInput(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("搜索模型名称或 ID") })
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("已选 ${picked.size} / 最多512", Modifier.weight(1f), color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                TextButton({ picked = picked + filtered.map { it.id } }, enabled = (picked + filtered.map { it.id }).size <= 512) { Text("全选结果") }
                TextButton({ picked = emptySet() }, enabled = picked.isNotEmpty()) { Text("清空") }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { entry ->
                    Item(onClick = { picked = if (entry.id in picked) picked - entry.id else picked + entry.id },
                        enabled = entry.id in picked || picked.size < 512,
                        summary = { Text(entry.id, style = MaterialTheme.typography.bodySmall, color = AuroraMuted) },
                        trailing = { if (entry.id in picked) Text("✓", color = MaterialTheme.colorScheme.primary) }) { Text(entry.name) }
                }
            }
            if (filtered.isEmpty()) Text("没有匹配的模型", Modifier.padding(16.dp), color = AuroraMuted)
            Button({ choose(entries.filter { it.id in picked }) }, Modifier.fillMaxWidth().padding(bottom = 16.dp),
                enabled = picked.isNotEmpty() && entries.any { it.id in picked }) { Text("使用 ${picked.size} 个模型") }
        }
    }
}
