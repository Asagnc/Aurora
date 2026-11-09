package io.github.asagnc.aurora.ui

import android.os.Build
import android.os.Process
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
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.root.RootPhase

@Composable internal fun ExecutionStatusPage(available: Boolean, dismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as AuroraApplication
    val root by app.root.state.collectAsStateWithLifecycle()
    val rootStatus = if (root.phase == RootPhase.READY) "Root 已连接" else "需要连接 Root"
    FullPage("工具与执行", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("点开能力查看说明。实际可用性以当前权限、环境和自检结果为准。", color = AuroraMuted)
            SettingsSection("基础能力") {
                CapabilityItem("原生命令与终端", if (available) "执行器已打包" else "当前设备架构不支持",
                    "Agent 可执行普通命令和交互终端；日志、超时与取消按任务管理。命令拥有 Aurora 当前 UID 的实际访问能力，不是安全沙箱。\nUID ${Process.myUid()} · API ${Build.VERSION.SDK_INT} · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}")
                CapabilityItem("文件与图片", "已接入 Agent",
                    "会话文件、共享资源与保留工作区分别管理。支持批量读写及会话图片展示、保存和分享；模型视觉输入与生图 API 尚未接入。")
                CapabilityItem("浏览器与 HTTP", "已接入 Agent",
                    "支持网页交互、脚本、上传下载及单次 HTTP 请求。网站能力取决于网络和页面；HTTP 请求不自动继承浏览器登录态或重放未知结果。")
                CapabilityItem("后台任务与日志", "前台服务管理任务",
                    "离开页面可通过通知返回或停止。进程死亡后记录中断，不自动重放工具；日志和终端输出受保存预算限制。")
                CapabilityItem("Skills", "已接入 Agent",
                    "导入、固定版本、来源管理和签名验证。工作流需要的命令与环境仍须实际可用；签名信任由你显式配置。")
            }
            SettingsSection("Root 与共享环境") {
                CapabilityItem("Root 命令", rootStatus,
                    "连接已在管理器中授权的 Root 服务后使用。普通命令仍保持普通权限；访问能力取决于系统 SELinux 和实际授权。")
                CapabilityItem("Linux 环境", "需要安装环境与当前资格",
                    "支持共享 Root chroot 版本、会话工作区和受管终端；普通 PRoot 单独检查，不自动切换运行器。")
                CapabilityItem("抓包与网络代理", "需要 Root 和工具自检",
                    "有界抓包、轮转、PCAP 分析、重放和按需 TLS／HTTP 代理。UID 仅为当前 socket 快照；不会自动安装系统 CA 或修改全局路由。")
                CapabilityItem("静态逆向与 Frida", "需要对应工具与自检",
                    "JADX／Apktool 分析和 Frida 受管 attach／spawn。Frida 策略启用入口仍在验收；未知任务保留记录，不重放。")
                CapabilityItem("APK 构建", "需要共享工具与环境自检",
                    "已接入 Java／Compose／JNI 构建和 APK 导出，使用共享只读工具与会话私有缓存。构建成功不自动安装，正式签名由你配置。")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun CapabilityItem(title: String, status: String, detail: String) {
    SettingsDetails(title, status) { Text(detail, style = MaterialTheme.typography.bodyMedium, color = AuroraMuted) }
}
