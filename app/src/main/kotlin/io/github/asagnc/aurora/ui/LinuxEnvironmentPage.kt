package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Divider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.root.RootPhase
import io.github.asagnc.aurora.core.LinuxBackend
import io.github.asagnc.aurora.data.LinuxVersion
import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonArray
import io.github.asagnc.aurora.core.AndroidBuildTools
import io.github.asagnc.aurora.core.string

@Composable internal fun LinuxEnvironmentPage(dismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as AuroraApplication
    val manager = app.linux
    val revision by manager.revision.collectAsStateWithLifecycle()
    val status by manager.status.collectAsStateWithLifecycle()
    val rootState by app.root.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var versions by remember { mutableStateOf(emptyList<LinuxVersion>()) }
    var pendingRuns by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf<String?>(null) }
    var work by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var androidInstalled by remember { mutableStateOf(false) }
    var stateLoaded by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        work = scope.launch {
            notice = null
            try { app.ready.await(); action() }
            catch (cancel: CancellationException) { notice = "已取消，已完成的内容保留。"; throw cancel }
            catch (_: Exception) { notice = "操作未完成，请检查 Root 连接、网络、可用空间和环境完整性。" }
            finally { busy = false }
        }
    }
    val nativePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            app.sharedToolchain.installAndroidArchive(uri)
            androidInstalled = true
            notice = "构建工具已安装，执行前仍需通过环境自检。"
        }
    }
    LaunchedEffect(revision, rootState.phase, refresh) {
        stateLoaded = false
        try { app.ready.await(); versions = manager.registry.versions(); current = manager.registry.current(); pendingRuns = manager.runs.pendingCount()
            androidInstalled = string(AndroidBuildTools.bundleId) in app.sharedToolchain.status(current, "android-build")["installed"]!!.jsonArray
            stateLoaded = true
        }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "环境状态读取失败，请重试。" }
    }
    val targetBackend = if (app.root.linuxClient != null) LinuxBackend.CHROOT else LinuxBackend.PROOT
    val existingImage = versions.firstOrNull { it.manifest.image.sha256 == manager.image.sha256 }
    val extraEnvironment = stateLoaded && existingImage != null && versions.none {
        it.manifest.image.sha256 == manager.image.sha256 && it.manifest.backend == targetBackend
    }
    val targetName = if (targetBackend == LinuxBackend.CHROOT) "Root" else "普通 PRoot"
    FullPage("Linux 环境", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsStatus("Ubuntu Base 26.10", when {
                !stateLoaded -> "状态尚未确认"
                current == null -> "未安装基础环境"
                else -> "已安装基础环境"
            }, R.drawable.ic_terminal)
            if (!stateLoaded && notice == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (extraEnvironment) Text("另备 $targetName 环境约增加 ${requireNotNull(existingImage).manifest.expandedBytes / (1024 * 1024)} MiB 文件，原环境会保留。", color = AuroraMuted)
            Button({ run { manager.install() } }, modifier = Modifier.fillMaxWidth(), enabled = !busy && stateLoaded) {
                Text(if (extraEnvironment) "准备 $targetName 环境" else if (current == null) "安装基础环境" else "检查基础环境")
            }
            if (rootState.phase != RootPhase.READY) Text("Root 未连接；普通环境需要设备支持硬链接。", color = AuroraMuted)
            if (!stateLoaded && notice != null) TextButton({ refresh++ }, enabled = !busy) { Text("重试读取状态") }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("正在处理，离开页面会取消当前操作。", style = MaterialTheme.typography.bodyMedium, color = AuroraMuted)
                TextButton({ work?.cancel() }) { Text("取消当前操作") }
            }
            notice?.let { SettingsNotice(it) }
            if (pendingRuns > 0) {
                SettingsNotice("有 $pendingRuns 个任务等待确认退出，相关会话和环境暂时保留。")
                OutlinedButton({ run {
                    val result = manager.runs.recover()
                    pendingRuns = manager.runs.pendingCount()
                    app.spaces.recoverDeletes(app.browser::deleteOwner)
                    notice = "恢复结果：$result"
                } }, modifier = Modifier.fillMaxWidth(), enabled = !busy && app.root.linuxRunClient != null) { Text("恢复待确认任务") }
            }
            Group(title = "APK 构建") {
                Item(icon = { LocalIcon(R.drawable.ic_extension, "构建工具") },
                    summary = { Text(if (!stateLoaded) "状态尚未确认" else if (androidInstalled) "当前版本已安装" else "尚未安装",
                        color = AuroraMuted, style = MaterialTheme.typography.bodySmall) }) { Text("构建工具") }
                if (stateLoaded && !androidInstalled) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择 android-host-arm64.tar.gz。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton({ nativePicker.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("导入构建工具") }
                }
            }
            Group(title = "环境管理") {
                Item(onClick = { page = "versions" }, enabled = !busy,
                    summary = { Text(if (stateLoaded) "${versions.size} 个已安装版本" else "等待状态读取", color = AuroraMuted,
                        style = MaterialTheme.typography.bodySmall) }, trailing = { SettingsChevron() }) { Text("环境版本") }
                Divider(startIndent = 16.dp)
                Item(onClick = { page = "maintenance" }, enabled = !busy,
                    summary = { Text("闲置版本与待确认记录", color = AuroraMuted, style = MaterialTheme.typography.bodySmall) },
                    trailing = { SettingsChevron() }) { Text("维护与恢复") }
                Divider(startIndent = 16.dp)
                Item(onClick = { page = "help" }, trailing = { SettingsChevron() }) { Text("安装与运行说明") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    page?.let { destination ->
        val title = when (destination) { "versions" -> "环境版本"; "maintenance" -> "维护与恢复"; else -> "安装与运行说明" }
        FullPage(title, { page = null }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在处理，离开 Linux 环境会取消操作。", color = AuroraMuted)
                    TextButton({ work?.cancel() }) { Text("取消当前操作") }
                }
                notice?.let { SettingsNotice(it) }
                when (destination) {
                    "versions" -> {
                        if (versions.isEmpty()) Text("尚无可显示的版本。", color = AuroraMuted)
                        else Group(title = "已安装版本") {
                            versions.forEachIndexed { index, version ->
                                if (index > 0) Divider(startIndent = 16.dp)
                                Item(summary = {
                                    Text("${if (version.id == current) "当前版本 · " else ""}${if (version.manifest.backend == LinuxBackend.CHROOT) "Root" else "PRoot"} · ${version.manifest.image.architecture.abi} · ${version.manifest.expandedBytes / (1024 * 1024)} MiB\n${version.id.take(16)}")
                                }) { Text("${version.manifest.image.distribution} ${version.manifest.image.version}") }
                            }
                        }
                    }
                    "maintenance" -> {
                        Text("只回收已确认闲置的内容，活动任务或未知状态会保留。", color = AuroraMuted)
                        OutlinedButton({ run { notice = "已清理 ${manager.cleanup()} 个未使用版本" } }, enabled = !busy && stateLoaded) { Text("清理闲置版本") }
                        OutlinedButton({ run {
                            val result = manager.metadata.collect()
                            notice = "已回收 ${result["collected"]} 条旧记录，保留 ${result["retained"]} 条当前或待确认记录。"
                        } }, enabled = !busy && app.root.metadataClient != null) { Text("清理已确认的旧记录") }
                        if (rootState.phase == RootPhase.READY && app.root.metadataClient == null)
                            Text("重新连接 Root 后可维护旧记录。", color = AuroraMuted)
                        if (app.root.rootImagesAvailable) {
                            val ordinary = versions.firstOrNull { it.manifest.backend == LinuxBackend.PROOT && it.manifest.image.sha256 == manager.image.sha256 }
                            if (ordinary == null) Text("普通 PRoot 使用独立环境，会额外占用存储空间。", color = AuroraMuted)
                            TextButton({ run { manager.install(LinuxBackend.PROOT) } }, enabled = !busy && stateLoaded) { Text("准备普通 PRoot 环境") }
                        }
                        if (versions.any { it.id == current && it.manifest.backend == LinuxBackend.PROOT }) {
                            OutlinedButton({ run {
                                val result = manager.probe()
                                notice = if (result["workloadPassed"] == kotlinx.serialization.json.JsonPrimitive(true))
                                    "基础检查通过；不等于完整运行资格。" else "基础检查未通过：$result"
                            } }, enabled = !busy) { Text("检查 PRoot 基础能力") }
                        }
                    }
                    "help" -> {
                        Text("基础环境与工具共享安装，会话文件和输出独立保存。基础包不包含完整编译和逆向工具。")
                        Text("构建工具仅接受固定审查版归档，其余组件使用官方固定版本。安装不会自动安装 APK 或授予执行资格。")
                        Text("Root Linux 命令与终端需要对应环境及当前连接自检；PRoot 单独检查，不自动切换执行方式。")
                        if (app.root.linuxAvailable && !app.root.rootImagesAvailable) Text("现有 Root 服务需重新连接以更新安装能力。")
                        Text(status, style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

}
