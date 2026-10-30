package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
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
    var detail by remember { mutableStateOf("尚未验证当前 Root 连接") }
    LaunchedEffect(root) { detail = "Root 连接变化后须重新验证，旧状态不代表当前资格" }
    fun execute(activate: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val connection = requireNotNull(app.root.fridaConnection) { "请先连接 Root" }
                val result = connection.client.policy(connection.identity, activate)
                detail = if (result["policyQualified"]?.jsonPrimitive?.booleanOrNull == true)
                    "当前启动与 Root 连接已验证。Frida native / ART 自测仍须另行通过。" else "当前连接尚未验证策略适配。"
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { detail = failure.message ?: "策略资格未确认" }
            finally { busy = false }
        }
    }
    FullPage("Frida 策略适配", dismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("适配会修改本次启动的全局 SELinux 策略，保持 Enforcing。仅增加 Frida 专用 memfd 类型及当前 App / Root 域的必要文件权限；重启后失效。", style = MaterialTheme.typography.bodyMedium)
            Text("仅由你点击启用。若已有未知适配或策略变化，验证会停止；不覆盖未知记录、不回滚整个策略，也不安装持久模块。", color = AuroraMuted)
            Text(detail)
            if (!FridaPolicyAvailability.releaseQualified) Text("设备验收中：重启、撤权及剩余故障验证完成前，暂不开放启用。", color = AuroraMuted)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button({ execute(true) }, enabled = FridaPolicyAvailability.releaseQualified && !busy && root.phase == RootPhase.READY && !app.agentTasks.busy) {
                Text("验证并启用本次适配")
            }
            OutlinedButton({ execute(false) }, enabled = !busy && root.phase == RootPhase.READY) { Text("检查当前资格") }
        }
    }
}
