package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.asagnc.aurora.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.root.RootPhase
import io.github.asagnc.aurora.root.FridaPolicyAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable internal fun FridaPolicyPage(dismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as AuroraApplication
    val root by app.root.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }
    var qualified by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(root) { detail = null; qualified = null }
    fun execute(activate: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val connection = requireNotNull(app.root.fridaConnection) { "请先连接 Root" }
                val result = connection.client.policy(connection.identity, activate)
                qualified = result["policyQualified"]?.jsonPrimitive?.booleanOrNull == true
                detail = if (qualified == true)
                    "当前启动与 Root 连接已验证。Frida native / ART 自测仍须另行通过。" else "当前连接尚未验证策略适配。"
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { qualified = null; detail = failure.message ?: "策略资格未确认" }
            finally { busy = false }
        }
    }
    FullPage("Frida 策略适配", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsStatus("当前策略", when (qualified) {
                true -> "本次连接已验证"
                false -> "资格未确认"
                null -> if (root.phase == RootPhase.READY) "尚未检查" else "请先连接 Root"
            }, R.drawable.ic_shield)
            if (!FridaPolicyAvailability.releaseQualified) SettingsNotice("设备验收中，暂未开放启用。")
            Text("启用会修改本次启动的 SELinux 策略，保持 Enforcing，重启后失效。", color = AuroraMuted)
            Button({ execute(true) }, modifier = Modifier.fillMaxWidth(),
                enabled = FridaPolicyAvailability.releaseQualified && !busy && root.phase == RootPhase.READY && !app.agentTasks.busy) {
                Text("验证并启用本次适配")
            }
            OutlinedButton({ execute(false) }, modifier = Modifier.fillMaxWidth(),
                enabled = !busy && root.phase == RootPhase.READY) { Text("检查当前资格") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            detail?.let { SettingsNotice(it) }
            SettingsDetails("适配范围与限制", "仅本次启动，需单独通过 Frida 自检") {
                Text("仅增加 Frida 专用 memfd 类型和当前 App／Root 域所需权限，不安装持久模块。")
                Text("未知适配或策略变化会停止验证，不覆盖未知记录、不回滚整份策略。")
                Text("Root 重连后须重新检查。策略验证不等于 native／ART 自测通过。")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
