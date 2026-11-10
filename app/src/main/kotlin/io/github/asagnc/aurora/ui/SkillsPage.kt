package io.github.asagnc.aurora.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Divider
import com.itos.coui.switch.Switch as CouiSwitch
import com.itos.coui.theme.CouiTheme
import com.itos.coui.layout.CouiPageDefaults
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
    var profiles by remember { mutableStateOf(emptyList<SkillSourceProfile>()) }
    var query by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var sources by rememberSaveable { mutableStateOf(false) }
    var signedImportPending by rememberSaveable { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<String?>(null) }
    var signature by remember { mutableStateOf<String?>(null) }
    var linkProfile by remember { mutableStateOf<String?>(null) }
    var linkSignature by remember { mutableStateOf<String?>(null) }
    var signatureForLink by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SkillEntry?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; notice = null
        scope.launch {
            try { app.ready.await(); action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { notice = "操作未完成，请检查来源、网络或文件；原有 Skill 保留。" }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val signed = signedImportPending
        signedImportPending = false
        if (uri != null) {
            if (signed && (selectedProfile == null || signature == null)) notice = "验证材料已失效，请重新选择后导入。"
            else run {
                val added = manager.import(uri, profileId = selectedProfile.takeIf { signed }, signature = signature.takeIf { signed })
                signature = null; notice = "已添加 ${added.size} 个 Skill"
            }
        }
    }
    val signaturePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run {
            val value = manager.signature(uri)
            if (signatureForLink) linkSignature = value else signature = value
        }
    }
    LaunchedEffect(revision, sources) {
        try { app.ready.await(); entries = manager.list(); profiles = manager.trust.list() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { notice = "暂时无法读取 Skills，请稍后重新打开。" }
        finally { loading = false }
    }
    FullPage("Skills", dismiss) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("add") {
                Group(title = "添加 Skill") {
                    Item(onClick = { signedImportPending = false; picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                        enabled = !busy, summary = { Text("选择包含 SKILL.md 的 ZIP", color = AuroraMuted,
                            style = MaterialTheme.typography.bodySmall) }, trailing = { SettingsChevron() }) { Text("导入 ZIP") }
                    Divider(startIndent = 16.dp)
                    Item(onClick = { notice = null; link = true }, enabled = !busy, trailing = { SettingsChevron() }) { Text("从链接添加") }
                }
            }
            item("feedback") {
                if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (busy) status?.let { Text(it, color = AuroraMuted, style = MaterialTheme.typography.bodySmall) }
                notice?.let { SettingsNotice(it) }
                if (entries.size > 1) SearchField(query, { query = it }, "搜索已添加的 Skill")
            }
            val matching = entries.filter { "${it.metadata.name} ${it.metadata.description}".contains(query, true) }
            if (matching.isEmpty() && !loading) item("empty") {
                Text(if (entries.isEmpty()) "还没有 Skill。添加后，Aurora 会在需要时读取它的说明。" else "没有匹配的 Skill",
                    Modifier.padding(vertical = 24.dp), color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
            }
            items(matching, key = { it.id }) { entry ->
                Surface(shape = CouiPageDefaults.shape, color = CouiTheme.colorScheme.card) {
                    Column {
                        Item(onClick = { selected = entry }, summary = {
                            Text(entry.metadata.description, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                        }, trailing = { CouiSwitch(checked = entry.enabled,
                            onCheckedChange = { enabled -> run { manager.enable(entry.id, enabled) } }, enabled = !busy) }) {
                            Text(entry.metadata.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (!skillSourcePermitted(entry.source, profiles)) Text("来源已受限，不能用于新任务。",
                            Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item("advanced") {
                TextButton({ advanced = true }, enabled = !busy) { Text("高级设置") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (link) FullPage("从链接添加 Skill", { link = false }, compact = true) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (busy) status?.let { Text(it, color = AuroraMuted, style = MaterialTheme.typography.bodySmall) }
            notice?.let { SettingsNotice(it) }
            Group(title = "安装链接") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuroraInput(url, { url = it }, label = "GitHub 或 HTTPS ZIP 链接", modifier = Modifier.fillMaxWidth(), singleLine = true,
                        enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false))
                    TextButton({ options = !options }, enabled = !busy) { Text(if (options) "收起可选设置" else "可选设置") }
                    if (options) {
                        AuroraInput(selection, { selection = it }, label = "子目录（可选）", modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                        AuroraInput(ref, { ref = it }, label = "分支或标签（可选）", modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
                        Text("仓库链接默认读取最新正式发布；tree 链接使用所指引用。", color = AuroraMuted,
                            style = MaterialTheme.typography.bodySmall)
                        AuroraChoice("来源策略", linkProfile, listOf<String?>(null) + profiles.filter { !it.revoked }.map { it.id },
                            { linkProfile = it }, enabled = !busy) { id ->
                            if (id == null) "自动匹配" else profiles.firstOrNull { it.id == id }?.repository ?: "来源不可用"
                        }
                        TextButton({ signatureForLink = true; signaturePicker.launch(arrayOf("text/plain", "application/octet-stream")) }, enabled = !busy) {
                            Text(if (linkSignature == null) "选择签名文件（可选）" else "重新选择签名文件")
                        }
                        if (linkSignature != null) TextButton({ linkSignature = null }, enabled = !busy) { Text("清除签名文件") }
                    }
                }
            }
            Button({ run {
                val added = manager.remote(SkillSource(url.trim(), selection.trim('/'), ref = ref.trim()), SkillInstallOptions(profileId = linkProfile, signature = linkSignature))
                url = ""; selection = ""; ref = ""; linkProfile = null; linkSignature = null; link = false
                notice = "已添加 ${added.size} 个 Skill"
            } }, Modifier.fillMaxWidth(), enabled = !busy && url.isNotBlank()) { Text(if (busy) "正在添加" else "添加 Skill") }
        }
    }
    if (advanced) FullPage("Skill 高级设置", { advanced = false }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { SettingsNotice(it) }
            Group(title = "来源与认证") {
                Item(onClick = { sources = true }, enabled = !busy,
                    summary = { Text("私有仓库令牌、发布者公钥与撤销", color = AuroraMuted,
                        style = MaterialTheme.typography.bodySmall) }, trailing = { SettingsChevron() }) { Text("来源与信任") }
            }
            SettingsDetails("验证签名并导入", "仅在你需要核验发布者签名时使用") {
                AuroraChoice("可信来源", selectedProfile, profiles.filter { !it.revoked && it.publicKey.isNotBlank() }.map { it.id },
                    { selectedProfile = it }, enabled = !busy) { id -> profiles.firstOrNull { it.id == id }?.repository ?: "请选择" }
                TextButton({ signatureForLink = false; signaturePicker.launch(arrayOf("text/plain", "application/octet-stream")) }, enabled = !busy) {
                    Text(if (signature == null) "选择签名文件" else "重新选择签名文件")
                }
                Button({ signedImportPending = true; picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !busy && selectedProfile != null && signature != null) { Text("选择 ZIP 并验签导入") }
                Text("仍会检查来源当前状态，不信任 ZIP 自带的公钥。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            }
            SettingsDetails("清理旧版本", "保留已安装版本和任务引用的内容") {
                OutlinedButton({ run { notice = "已清理 ${manager.cleanup()} 字节未引用资源" } }, enabled = !busy) { Text("清理未引用内容") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    selected?.let { entry -> SkillDetailPage(entry, manager) { selected = null } }
    if (sources) SkillSourcesPage(manager.trust) { sources = false }
}
