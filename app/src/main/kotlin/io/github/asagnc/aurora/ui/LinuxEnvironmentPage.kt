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
    val rootState by app.root.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf(emptyList<LinuxVersion>()) }
    var pendingRuns by remember { mutableIntStateOf(0) }
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
            catch (_: Exception) { notice = "操作未完成，请检查 Root 连接、网络、可用空间和环境完整性。" }
            finally { busy = false }
        }
    }
    LaunchedEffect(revision) {
        try { app.ready.await(); versions = manager.registry.versions(); current = manager.registry.current(); pendingRuns = manager.runs.pendingCount() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "环境状态读取失败，请重试。" }
    }
    FullPage("Linux 环境", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Ubuntu Base 26.10", style = MaterialTheme.typography.headlineSmall)
            Text("共享基础环境与已发布版本；会话文件和输出独立保存。基础包不包含完整编译及逆向工具。", color = AuroraMuted)
            Text(status, style = MaterialTheme.typography.titleMedium)
            Text(if (app.root.rootImagesAvailable && rootState.phase == io.github.asagnc.aurora.root.RootPhase.READY)
                "默认准备共享 Root 环境并保留 Linux 原始属主；清理需要 Root。普通环境只在需要时另备。"
                else if (app.root.linuxAvailable) "请断开并重新连接 Root，以更新环境安装协议。"
                else "普通环境需支持硬链接；也可先在设置连接 Root，准备独立的 Root 环境。安装不自动申请授权。", color = AuroraMuted)
            val targetBackend = if (app.root.rootImagesAvailable) io.github.asagnc.aurora.core.LinuxBackend.CHROOT else io.github.asagnc.aurora.core.LinuxBackend.PROOT
            if (versions.none { it.manifest.backend == targetBackend && it.manifest.image.sha256 == manager.image.sha256 }) {
                versions.firstOrNull { it.manifest.image.sha256 == manager.image.sha256 }?.let { existing ->
                    Text("现有环境会保留；另备环境约增加 ${existing.manifest.expandedBytes / (1024 * 1024)} MiB 文件，另需目录和暂存空间。", color = AuroraMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ run { manager.install() } }, enabled = !busy) { Text(if (current == null) "安装基础环境" else "检查基础环境") }
                if (busy) TextButton({ work?.cancel() }) { Text("取消") }
                else TextButton({ run { notice = "已清理 ${manager.cleanup()} 个未使用版本" } }) { Text("清理闲置") }
            }
            if (app.root.rootImagesAvailable) TextButton({ run { manager.install(io.github.asagnc.aurora.core.LinuxBackend.PROOT) } }, enabled = !busy) {
                Text("按需准备普通 PRoot 环境（额外存储）")
            }
            OutlinedButton({ run {
                val result = manager.metadata.collect()
                notice = "已回收 ${result["collected"]} 条旧运行记录；保留 ${result["retained"]} 条当前或待确认记录。"
            } }, enabled = !busy && app.root.metadataClient != null) { Text("清理已确认的旧运行记录") }
            if (rootState.phase == io.github.asagnc.aurora.root.RootPhase.READY && app.root.metadataClient == null)
                Text("重新连接 Root 后可清理旧运行记录。", color = AuroraMuted)
            if (pendingRuns > 0) {
                Text("有 $pendingRuns 个 Linux 任务等待确认退出，相关会话和环境暂时保留。", color = AuroraMuted)
                OutlinedButton({ run {
                    val result = manager.runs.recover()
                    pendingRuns = manager.runs.pendingCount()
                    app.spaces.recoverDeletes(app.browser::deleteOwner)
                    notice = "恢复结果：$result"
                } }, enabled = !busy && app.root.linuxRunClient != null) { Text("确认退出并恢复任务") }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("离开页面会取消当前操作，已完成的安装或清理会保留。", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
            }
            notice?.let { Text(it) }
            versions.forEach { version ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${version.manifest.image.distribution} ${version.manifest.image.version}", style = MaterialTheme.typography.titleMedium)
                        Text("${if (version.manifest.backend == io.github.asagnc.aurora.core.LinuxBackend.CHROOT) "Root" else "PRoot"} · ${version.manifest.image.architecture.abi} · ${version.manifest.expandedBytes / (1024 * 1024)} MiB 文件内容", color = AuroraMuted)
                        Text("${version.id.take(16)}${if (version.id == current) " · 当前版本" else ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HorizontalDivider()
            Text("执行状态", style = MaterialTheme.typography.titleSmall)
            Text("Agent 可执行 Root Linux 批量命令或启动交互终端；需要连接 Root 并安装对应环境。可在终端页面查看输出和停止任务；PRoot 仅支持基础能力检查。", color = AuroraMuted)
            OutlinedButton({ run {
                val result = manager.probe()
                notice = if (result["workloadPassed"] == kotlinx.serialization.json.JsonPrimitive(true))
                    "基础检查通过；完整执行能力仍待验收。" else "基础检查未通过：$result"
            } }, enabled = !busy && versions.any { it.id == current && it.manifest.backend == io.github.asagnc.aurora.core.LinuxBackend.PROOT }) { Text("检查 PRoot 执行能力") }
        }
    }
}
