package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
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
    var repository by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var signatureUrl by remember { mutableStateOf("") }
    var removeCredential by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun edit(profile: SkillSourceProfile?) {
        selected = profile; repository = profile?.repository.orEmpty(); token = ""
        publicKey = profile?.publicKey.orEmpty(); signatureUrl = profile?.signatureUrl.orEmpty(); removeCredential = false
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
    FullPage(if (editing) "编辑来源" else "来源与信任", ::back) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!editing) {
                FilledTonalButton({ edit(null); editing = true }, enabled = !busy) { Text("添加来源") }
                if (entries.isEmpty() && !busy) Text("尚未配置来源。公共工作流也可以直接从链接安装。", color = AuroraMuted)
                entries.forEach { profile ->
                    Card(Modifier.fillMaxWidth()) {
                        ListItem(supportingContent = {
                            Text(if (profile.revoked) "已撤销" else "${if (profile.credentialId == null) "公共访问" else "已配置私有凭据"} · ${if (profile.publicKey.isBlank()) "未要求签名" else "要求发布者签名"}")
                        }) { Text(profile.repository) }
                        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton({ edit(profile); editing = true }, enabled = !busy) { Text("编辑") }
                            TextButton({ run { manager.revoke(profile.id); edit(null); notice = "来源已撤销，后续安装和更新被阻止；已运行版本保留。" } }, enabled = !busy && !profile.revoked) { Text("撤销") }
                        }
                    }
                }
            } else {
                AuroraInput(repository, { repository = it }, modifier = Modifier.fillMaxWidth(), label = { Text("GitHub 仓库 owner/repo") }, enabled = !busy && selected == null, singleLine = true)
                AuroraInput(token, { token = it }, label = { Text(if (selected?.credentialId != null) "替换令牌（留空保留）" else "只读令牌（公共仓库留空）") },
                    modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
                if (selected?.credentialId != null) Row {
                    Checkbox(removeCredential, { removeCredential = it }, enabled = !busy); Text("移除已保存的令牌")
                }
                AuroraInput(publicKey, { publicKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ed25519 公钥（SPKI Base64，可选）") }, enabled = !busy)
                if (publicKey.isNotBlank()) {
                    val fingerprint = runCatching { SkillSignatures.fingerprint(publicKey.trim()) }.getOrNull()
                    Text(fingerprint?.let { "请独立核对公钥 SHA256：$it" } ?: "公钥格式尚不正确", style = MaterialTheme.typography.bodySmall)
                }
                AuroraInput(signatureUrl, { signatureUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("签名文件 HTTPS 地址（可选）") }, enabled = !busy)
                Text("填写公钥后将强制验签，请独立核对指纹。", color = AuroraMuted)
                FilledTonalButton({ run {
                    val profile = SkillSourceProfile(selected?.id ?: UUID.randomUUID().toString(), repository.trim().lowercase(java.util.Locale.ROOT),
                        selected?.credentialId, publicKey.trim(), signatureUrl.trim())
                    manager.save(profile, token.takeIf { it.isNotBlank() }, removeCredential)
                    edit(null); editing = false; notice = "来源已保存，新配置不会替换正在运行的版本。"
                } }, enabled = !busy && repository.isNotBlank()) { Text(if (selected?.revoked == true) "保存并重新启用来源" else "保存来源策略") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { SettingsNotice(it) }
            SettingsDetails("认证与签名说明", "凭据留在本机，发布者信任由你决定") {
                Text("私有仓库使用仓库 Contents 只读令牌，仅发送到 GitHub API，不交给模型。")
                Text("公钥必须从独立可信渠道取得。签名地址留空时需手动选择签名文件；缺少签名或验证失败将被拒绝。")
                Text("验签只证明发布者签名和字节一致性，不代表工作流安全，也不授予额外权限。")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
