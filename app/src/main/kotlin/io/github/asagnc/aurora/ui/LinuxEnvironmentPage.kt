package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.data.LinuxVersion
import kotlinx.coroutines.*

@Composable internal fun LinuxEnvironmentPage(dismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as AuroraApplication
    val manager = app.linux
    val revision by manager.revision.collectAsStateWithLifecycle()
    val status by manager.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf(emptyList<LinuxVersion>()) }
    var current by remember { mutableStateOf<String?>(null) }
    var work by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        work = scope.launch {
            notice = null
            try { app.ready.await(); action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "操作未完成，请检查网络、可用空间和环境完整性。" }
            finally { busy = false }
        }
    }
    LaunchedEffect(revision) {
        try { app.ready.await(); versions = manager.registry.versions(); current = manager.registry.current() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "环境状态读取失败，请重试。" }
    }
    FullPage("Linux 环境", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Ubuntu Base 26.10", style = MaterialTheme.typography.headlineSmall)
            Text("共享基础环境与已发布版本；会话文件和输出独立保存。基础包不包含完整编译及逆向工具。", color = AuroraMuted)
            Text(status, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ run { manager.install() } }, enabled = !busy) { Text(if (current == null) "安装基础环境" else "检查基础环境") }
                if (busy) TextButton({ work?.cancel() }) { Text("取消") }
                else TextButton({ run { notice = "已清理 ${manager.cleanup()} 个未使用版本" } }) { Text("清理旧版") }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("离开页面会取消未完成的安装；也可以让 Agent 安装并在任务中查看进度。", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
            }
            notice?.let { Text(it) }
            versions.forEach { version ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${version.manifest.image.distribution} ${version.manifest.image.version}", style = MaterialTheme.typography.titleMedium)
                        Text("${version.manifest.image.architecture.abi} · ${version.manifest.expandedBytes / (1024 * 1024)} MiB 文件内容", color = AuroraMuted)
                        Text("${version.id.take(16)}${if (version.id == current) " · 当前版本" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HorizontalDivider()
            Text("执行状态", style = MaterialTheme.typography.titleSmall)
            Text("PRoot 已随应用打包，可检查基础执行能力。完整命令、终端生命周期与 Root chroot 尚未接入。", color = AuroraMuted)
            OutlinedButton({ run {
                val result = manager.probe()
                notice = if (result["workloadPassed"] == kotlinx.serialization.json.JsonPrimitive(true))
                    "基础检查通过；完整执行能力仍待验收。" else "基础检查未通过：$result"
            } }, enabled = !busy && current != null) { Text("检查 PRoot 执行能力") }
        }
    }
}
