package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.layout.Divider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.skills.SkillTrustManager
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun SkillSourcesPage(manager: SkillTrustManager, dismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<SkillSourceProfile>()) }
    var selected by remember { mutableStateOf<SkillSourceProfile?>(null) }
    var editing by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<SkillSourceProfile?>(null) }
    var repository by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var signatureUrl by remember { mutableStateOf("") }
    var removeCredential by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun edit(profile: SkillSourceProfile?) {
        selected = profile; repository = profile?.repository.orEmpty(); token = ""; notice = null
        publicKey = profile?.publicKey.orEmpty(); signatureUrl = profile?.signatureUrl.orEmpty(); removeCredential = false
        signing = publicKey.isNotBlank() || signatureUrl.isNotBlank()
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); entries = manager.list() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                notice = "操作未完成，请检查仓库、公钥、签名地址或凭据存储；清理恢复记录会保留，请核对当前配置。"
                try { entries = manager.list() }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { notice = "来源配置暂不可读取，恢复记录保留，请返回后重试。" }
            }
            finally { busy = false; token = "" }
        }
    }
    LaunchedEffect(Unit) { run { entries = manager.list() } }
    fun back() {
        token = ""
        if (editing) { editing = false; edit(null) } else dismiss()
    }
    FullPage("来源与信任", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalButton({ edit(null); editing = true }, enabled = !busy) { Text("添加来源") }
            if (busy && !editing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!editing) notice?.let { SettingsNotice(it) }
            if (entries.isEmpty() && !busy) SettingsNotice("尚未配置来源。公共工作流也可以直接从链接安装。")
            if (entries.isNotEmpty()) Group(title = "已配置来源") {
                entries.forEachIndexed { index, profile ->
                    if (index > 0) Divider(startIndent = 16.dp)
                    Item(onClick = { edit(profile); editing = true }, enabled = !busy,
                        summary = { Text(if (profile.revoked) "已撤销" else "${if (profile.credentialId == null) "公共访问" else "私有访问"} · ${if (profile.publicKey.isBlank()) "未要求签名" else "发布者验签"}",
                            color = AuroraMuted, style = MaterialTheme.typography.bodySmall) },
                        trailing = { SettingsChevron() }) {
                        Text(profile.repository, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            SettingsDetails("认证与签名说明", "凭据留在本机，发布者信任由你决定") {
                Text("私有仓库使用仓库 Contents 只读令牌，仅发送到 GitHub API，不交给模型。")
                Text("公钥必须从独立可信渠道取得。签名地址留空时需手动选择签名文件；缺少签名或验证失败将被拒绝。")
                Text("验签只证明发布者签名和字节一致性，不代表工作流安全，也不授予额外权限。")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (editing) FullPage(if (selected == null) "添加来源" else "编辑来源", ::back) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { SettingsNotice(it) }
            Group(title = "GitHub 仓库") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuroraInput(repository, { repository = it }, modifier = Modifier.fillMaxWidth(),
                        label = "仓库 owner/repo", enabled = !busy && selected == null, singleLine = true)
                    Text("只对这个仓库应用认证与签名规则。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Group(title = "私有访问 · 可选") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuroraInput(token, { token = it; if (it.isNotBlank()) removeCredential = false },
                        label = if (selected?.credentialId != null) "替换令牌（留空保留）" else "只读令牌",
                        modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        enabled = !busy, singleLine = true)
                    Text("公共仓库可以留空。令牌仅保存在本机凭据库。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (selected?.credentialId != null) Item(
                    modifier = Modifier.toggleable(removeCredential, enabled = !busy, role = Role.Checkbox,
                        onValueChange = { removeCredential = it; if (it) token = "" }), trailing = { if (removeCredential) Text("✓", color = MaterialTheme.colorScheme.primary) }) {
                    Text(if (removeCredential) "保存时移除原令牌" else "移除已保存的令牌")
                }
            }
            Group(title = "发布者签名 · 可选") {
                Item(onClick = { signing = !signing }, enabled = !busy,
                    summary = { Text(if (publicKey.isBlank()) "尚未设置发布者公钥" else "保存后强制验证签名", color = AuroraMuted,
                        style = MaterialTheme.typography.bodySmall) }, trailing = { Text(if (signing) "收起" else "设置", color = MaterialTheme.colorScheme.primary) }) { Text("公钥与签名文件") }
                if (signing) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AuroraInput(publicKey, { publicKey = it }, modifier = Modifier.fillMaxWidth(),
                        label = "Ed25519 公钥 · SPKI Base64", enabled = !busy)
                    if (publicKey.isNotBlank()) {
                        val fingerprint = runCatching { SkillSignatures.fingerprint(publicKey.trim()) }.getOrNull()
                        Text(fingerprint?.let { "请独立核对 SHA256 指纹：$it" } ?: "公钥格式尚不正确",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    AuroraInput(signatureUrl, { signatureUrl = it }, modifier = Modifier.fillMaxWidth(),
                        label = "签名文件 HTTPS 地址（可选）", enabled = !busy)
                    Text("填写公钥后强制验签；地址留空时需手动选择签名文件。请先独立核对指纹。", color = AuroraMuted,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            FilledTonalButton({ run {
                val profile = SkillSourceProfile(selected?.id ?: UUID.randomUUID().toString(), repository.trim().lowercase(java.util.Locale.ROOT),
                    selected?.credentialId, publicKey.trim(), signatureUrl.trim())
                manager.save(profile, token.takeIf { it.isNotBlank() }, removeCredential)
                edit(null); editing = false; notice = "来源已保存，新配置不会替换正在运行的版本。"
            } }, modifier = Modifier.fillMaxWidth(), enabled = !busy && repository.isNotBlank()) {
                Text(if (selected?.revoked == true) "保存并重新启用来源" else "保存来源")
            }
            selected?.takeIf { !it.revoked }?.let { profile ->
                TextButton({ revoking = profile }, enabled = !busy) { Text("撤销此来源", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    revoking?.let { profile ->
        AuroraPrompt("撤销此来源？", "撤销来源", {
            revoking = null
            run { manager.revoke(profile.id); edit(null); editing = false; notice = "来源已撤销，后续安装和更新被阻止；已运行版本保留。" }
        }, { revoking = null }, message = "${profile.repository} 的后续安装和更新将被阻止，已运行版本保留。", cancelLabel = "取消")
    }
}
