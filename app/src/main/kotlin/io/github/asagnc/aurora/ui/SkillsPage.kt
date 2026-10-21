package io.github.asagnc.aurora.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SkillEntry
import io.github.asagnc.aurora.skills.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun SkillsPage(dismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as AuroraApplication
    val manager = app.skills
    val scope = rememberCoroutineScope()
    val revision by manager.version.collectAsStateWithLifecycle()
    val status by manager.status.collectAsStateWithLifecycle()
    var entries by remember { mutableStateOf(emptyList<SkillEntry>()) }
    var query by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var ref by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SkillEntry?>(null) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { app.ready.await(); action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "操作未完成，请检查来源、网络、归档格式与可用空间。原有版本仍保留。" }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { manager.import(uri) }
    }
    LaunchedEffect(revision) { app.ready.await(); entries = manager.list() }
    FullPage("Skills", dismiss) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("给 Agent 装上工作流", style = MaterialTheme.typography.headlineSmall)
                    Text("按需读取，任务固定版本。共享资源不随会话删除；工作流需要的命令与运行环境必须实际可用。", color = AuroraMuted)
                    OutlinedTextField(query, { query = it }, label = { Text("搜索已安装") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("GitHub 仓库或 HTTPS ZIP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextButton({ advanced = !advanced }) { Text(if (advanced) "收起来源选项" else "指定子目录或分支") }
                    if (advanced) {
                        OutlinedTextField(selection, { selection = it }, label = { Text("子目录（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(ref, { ref = it }, label = { Text("GitHub 分支或标签（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("留空时，仓库链接使用最新正式发布；tree 链接跟随所指引用。含斜杠的分支请在此完整填写。", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ run { manager.remote(SkillSource(url.trim(), selection.trim('/'), ref = ref.trim())); url = ""; selection = ""; ref = "" } }, enabled = !busy && url.isNotBlank()) { Text("安装") }
                        TextButton({ picker.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy) { Text("导入 ZIP") }
                        TextButton({ run { notice = "已清理 ${manager.cleanup()} 字节未引用资源" } }, enabled = !busy) { Text("清理旧版") }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = AuroraMuted) }
                    notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            val matching = entries.filter { "${it.metadata.name} ${it.metadata.description}".contains(query, true) }
            if (matching.isEmpty()) item { Text(if (entries.isEmpty()) "还没有工作流。也可以让 Agent 从链接安装。" else "没有匹配的工作流", color = AuroraMuted) }
            items(matching, key = { it.id }) { entry ->
                OutlinedCard(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.metadata.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(entry.enabled, { enabled -> run { manager.enable(entry.id, enabled) } }, enabled = !busy)
                        }
                        Text(entry.metadata.description, style = MaterialTheme.typography.bodyMedium)
                        Text("${entry.hash.take(10)} · ${entry.bytes / 1024} KiB · ${entry.source.label.ifBlank { "已安装" }}", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    selected?.let { entry -> SkillDetailPage(entry, manager, { selected = null }) }
}
