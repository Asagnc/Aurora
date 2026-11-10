package io.github.asagnc.aurora.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.itos.coui.switch.Switch as CouiSwitch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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
    var installing by remember { mutableStateOf(false) }
    var ref by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SkillEntry?>(null) }
    var sources by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(emptyList<SkillSourceProfile>()) }
    var selectedProfile by remember { mutableStateOf<String?>(null) }
    var profileMenu by remember { mutableStateOf(false) }
    var signature by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { app.ready.await(); action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "操作未完成，请检查来源、网络、归档格式与可用空间。原有版本仍保留。" }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { manager.import(uri, profileId = selectedProfile, signature = signature); signature = null; installing = false }
    }
    val signaturePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run { signature = manager.signature(uri) }
    }
    LaunchedEffect(revision, sources) { app.ready.await(); entries = manager.list(); profiles = manager.trust.list() }
    FullPage("Skills", dismiss) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ installing = !installing }, enabled = !busy) { Text("安装工作流") }
                        TextButton({ sources = true }, enabled = !busy) { Text("来源与信任") }
                    }
                    if (entries.isNotEmpty()) AuroraInput(query, { query = it }, label = { Text("搜索已安装") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AuroraMuted) }
                    notice?.let { SettingsNotice(it) }
                }
            }
            val matching = entries.filter { "${it.metadata.name} ${it.metadata.description}".contains(query, true) }
            if (matching.isEmpty()) item { Text(if (entries.isEmpty()) "还没有工作流。也可以让 Agent 从链接安装。" else "没有匹配的工作流", color = AuroraMuted) }
            items(matching, key = { it.id }) { entry ->
                Card(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.metadata.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            CouiSwitch(checked = entry.enabled, onCheckedChange = { enabled -> run { manager.enable(entry.id, enabled) } }, enabled = !busy)
                        }
                        Text(entry.metadata.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${entry.hash.take(10)} · ${entry.bytes / 1024} KiB · ${entry.source.label.ifBlank { "已安装" }}", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                        if (!skillSourcePermitted(entry.source, profiles)) Text("来源策略阻止新任务；请检查签名或来源配置。", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                SettingsDetails("维护", "清理未引用的旧版本") {
                    Text("保留已安装版本和任务引用的内容。", color = AuroraMuted)
                    OutlinedButton({ run { notice = "已清理 ${manager.cleanup()} 字节未引用资源" } }, enabled = !busy) { Text("清理旧版") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (installing) FullPage("安装工作流", { installing = false }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let { Text(it, color = AuroraMuted) }
            notice?.let { SettingsNotice(it) }
            androidx.compose.runtime.CompositionLocalProvider(LocalFormEnabled provides !busy) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuroraInput(url, { url = it }, label = { Text("GitHub 仓库或 HTTPS ZIP") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextButton({ advanced = !advanced }, enabled = !busy) { Text(if (advanced) "收起来源选项" else "指定子目录或分支") }
                    if (advanced) {
                        AuroraInput(selection, { selection = it }, label = { Text("子目录（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        AuroraInput(ref, { ref = it }, label = { Text("GitHub 分支或标签（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("留空时，仓库链接使用最新正式发布；tree 链接跟随所指引用。含斜杠的分支请在此完整填写。", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                        Box {
                            TextButton({ profileMenu = true }, enabled = !busy) { Text("来源策略：${profiles.firstOrNull { it.id == selectedProfile }?.repository ?: "自动匹配（本地ZIP默认未认证）"}") }
                            DropdownMenu(profileMenu, { profileMenu = false }) {
                                DropdownMenuItem(text = { Text("自动匹配") }, onClick = { selectedProfile = null; profileMenu = false })
                                profiles.filter { !it.revoked }.forEach { profile ->
                                    DropdownMenuItem(text = { Text(profile.repository) }, onClick = { selectedProfile = profile.id; profileMenu = false })
                                }
                            }
                        }
                        TextButton({ signaturePicker.launch(arrayOf("text/plain", "application/octet-stream")) }, enabled = !busy) {
                            Text(if (signature == null) "选择签名旁路文件（本地验签可用）" else "重新选择签名旁路文件")
                        }
                        if (signature != null) TextButton({ signature = null }, enabled = !busy) { Text("清除已选择的旁路签名") }
                        Text("本地签名 ZIP 还需选择已有可信来源策略；不会信任 ZIP 自带公钥。", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton({ run { manager.remote(SkillSource(url.trim(), selection.trim('/'), ref = ref.trim()), SkillInstallOptions(profileId = selectedProfile, signature = signature)); url = ""; selection = ""; ref = ""; signature = null; installing = false } }, enabled = !busy && url.isNotBlank()) { Text("安装") }
                        TextButton({ picker.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy) { Text("导入 ZIP") }
                    }
                }
            }
        }
    }
    selected?.let { entry -> SkillDetailPage(entry, manager, { selected = null }) }
    if (sources) SkillSourcesPage(manager.trust) { sources = false }
}
