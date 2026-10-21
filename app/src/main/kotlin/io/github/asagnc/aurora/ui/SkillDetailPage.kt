package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SkillEntry
import io.github.asagnc.aurora.skills.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun SkillDetailPage(entry: SkillEntry, manager: SkillManager, dismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val revision by manager.version.collectAsStateWithLifecycle()
    var current by remember(entry.id) { mutableStateOf(entry) }
    var body by remember(current.hash) { mutableStateOf("读取工作流…") }
    var versions by remember { mutableStateOf(emptyList<SkillEntry>()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(false) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "操作未完成；请检查来源、资源状态与网络。" }
            finally { busy = false }
        }
    }
    LaunchedEffect(revision, entry.id) {
        try {
            val installed = manager.list().firstOrNull { it.id == entry.id }
            if (installed == null) dismiss() else {
                current = installed
                versions = manager.registry.versions(installed.id)
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "安装状态读取失败，请返回列表重试。" }
    }
    LaunchedEffect(current.hash) {
        try {
            val result = manager.read(current, "SKILL.md")
            body = result.text("text") ?: "无法按 UTF-8 显示"
            if (result["complete"].toString() != "true") body += "\n\n界面仅预览前 64 KiB；Agent 可继续读取。"
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { body = "资源读取失败或校验不通过。" }
    }
    FullPage(current.metadata.name, dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(current.metadata.description, style = MaterialTheme.typography.titleMedium)
            Text("${if (current.enabled) "已启用" else "已停用"} · ${current.bytes} 字节\n版本 ${current.hash}\n来源：${current.source.label.ifBlank { current.source.input.substringBefore('?') }}", color = AuroraMuted)
            current.metadata.compatibility.takeIf { it.isNotBlank() }?.let { Text("环境要求：$it") }
            current.metadata.license.takeIf { it.isNotBlank() }?.let { Text("许可：$it") }
            current.metadata.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton({ files = true }) { Text("查看工作流文件") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ run {
                    manager.remote(current.source, SkillInstallOptions(replaceId = current.id))
                    notice = "已检查并安装来源的最新版本；新任务生效。"
                } }, enabled = !busy && current.source.input.startsWith("https://")) { Text("检查更新") }
                TextButton({ run {
                    val references = manager.remove(current.id)
                    if (references.isEmpty()) dismiss() else notice = "已停用；${references.size} 个会话仍引用旧版，资源暂时保留。"
                } }, enabled = !busy) { Text("移除") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { Text(it) }
            if (versions.size > 1) {
                Text("已保留的版本", style = MaterialTheme.typography.titleSmall)
                versions.forEach { previous -> TextButton({ run {
                    manager.rollback(current.id, previous.hash); notice = "已选择 ${previous.hash.take(10)}；新任务生效。"
                } }, enabled = !busy && previous.hash != current.hash) { Text(previous.hash.take(16) + if (previous.hash == current.hash) " · 当前" else "") } }
            }
            HorizontalDivider()
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (files) SkillFilesPage(current, manager) { files = false }
}
