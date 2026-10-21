package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SkillEntry
import io.github.asagnc.aurora.skills.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun SkillDetailPage(entry: SkillEntry, manager: SkillManager, dismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var body by remember(entry.hash) { mutableStateOf("读取工作流…") }
    var versions by remember { mutableStateOf(emptyList<SkillEntry>()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
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
    LaunchedEffect(entry.hash) {
        try {
            val result = manager.read(entry, "SKILL.md")
            body = result.text("text") ?: "无法按 UTF-8 显示"
            if (result["complete"].toString() != "true") body += "\n\n界面仅预览前 64 KiB；Agent 可继续读取。"
            versions = manager.registry.versions(entry.id)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { body = "资源读取失败或校验不通过。" }
    }
    FullPage(entry.metadata.name, dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(entry.metadata.description, style = MaterialTheme.typography.titleMedium)
            Text("版本 ${entry.hash}\n${entry.bytes} 字节\n来源：${entry.source.label.ifBlank { entry.source.input.substringBefore('?') }}", color = AuroraMuted)
            entry.metadata.compatibility.takeIf { it.isNotBlank() }?.let { Text("环境要求：$it") }
            entry.metadata.license.takeIf { it.isNotBlank() }?.let { Text("许可：$it") }
            entry.metadata.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ run {
                    manager.remote(entry.source, SkillInstallOptions(replaceId = entry.id))
                    notice = "已检查并安装来源的最新版本；新任务生效。"
                    versions = manager.registry.versions(entry.id)
                } }, enabled = !busy && entry.source.input.startsWith("https://")) { Text("检查更新") }
                TextButton({ run {
                    val references = manager.remove(entry.id)
                    if (references.isEmpty()) dismiss() else notice = "已停用；${references.size} 个会话仍引用旧版，资源暂时保留。"
                } }, enabled = !busy) { Text("移除") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { Text(it) }
            if (versions.size > 1) {
                Text("已保留的版本", style = MaterialTheme.typography.titleSmall)
                versions.forEach { previous -> TextButton({ run {
                    manager.rollback(entry.id, previous.hash); notice = "已选择 ${previous.hash.take(10)}；新任务生效。"
                } }, enabled = !busy) { Text(previous.hash.take(16)) } }
            }
            HorizontalDivider()
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
