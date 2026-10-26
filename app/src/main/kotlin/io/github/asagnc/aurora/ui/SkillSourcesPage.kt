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
        scope.launch {
            busy = true
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
    FullPage("Skills 来源与信任", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("私有仓库使用该仓库 Contents 只读令牌。令牌只发送到 GitHub API，不交给模型。")
            Text("公钥必须从你独立信任的渠道取得。验签不代表工作流安全，也不授予额外权限。", color = AuroraMuted)
            TextButton({ edit(null) }, enabled = !busy) { Text("新建来源配置") }
            entries.forEach { profile ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(profile.repository + if (profile.revoked) " · 已撤销" else "")
                        Text("${if (profile.credentialId == null) "公共访问" else "已配置私有凭据"} · ${if (profile.publicKey.isBlank()) "未要求签名" else "要求发布者签名"}", color = AuroraMuted)
                        if (profile.publicKey.isNotBlank()) Text("公钥 SHA256：${SkillSignatures.fingerprint(profile.publicKey)}", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton({ edit(profile) }, enabled = !busy) { Text("编辑") }
                            TextButton({ run { manager.revoke(profile.id); edit(null); notice = "来源已撤销；后续安装/更新被阻止，已运行版本保留。" } }, enabled = !busy && !profile.revoked) { Text("撤销") }
                        }
                    }
                }
            }
            HorizontalDivider()
            OutlinedTextField(repository, { repository = it }, label = { Text("GitHub 仓库 owner/repo") }, enabled = !busy && selected == null, singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text(if (selected?.credentialId != null) "替换令牌（留空保留）" else "只读令牌（公共仓库留空）") },
                visualTransformation = PasswordVisualTransformation(), enabled = !busy, singleLine = true)
            if (selected?.credentialId != null) Row {
                Checkbox(removeCredential, { removeCredential = it }, enabled = !busy); Text("移除已保存的令牌")
            }
            OutlinedTextField(publicKey, { publicKey = it }, label = { Text("可信 Ed25519 公钥（SPKI Base64，可选）") }, enabled = !busy)
            if (publicKey.isNotBlank()) {
                val fingerprint = runCatching { SkillSignatures.fingerprint(publicKey.trim()) }.getOrNull()
                Text(fingerprint?.let { "请独立核对公钥 SHA256：$it" } ?: "公钥格式尚不正确", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(signatureUrl, { signatureUrl = it }, label = { Text("旁路签名 HTTPS 地址（自动更新可选）") }, enabled = !busy)
            Text("填写公钥即要求验签。签名地址留空时，安装需选择旁路文件；缺签名或验签失败都会被拒绝。", color = AuroraMuted)
            FilledTonalButton({ run {
                val profile = SkillSourceProfile(selected?.id ?: UUID.randomUUID().toString(), repository.trim().lowercase(java.util.Locale.ROOT),
                    selected?.credentialId, publicKey.trim(), signatureUrl.trim())
                manager.save(profile, token.takeIf { it.isNotBlank() }, removeCredential)
                edit(null); notice = "已保存来源策略；不会替换正在运行的旧版本。"
            } }, enabled = !busy && repository.isNotBlank()) { Text(if (selected?.revoked == true) "保存并重新启用来源" else "保存来源策略") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            notice?.let { Text(it) }
        }
    }
}
