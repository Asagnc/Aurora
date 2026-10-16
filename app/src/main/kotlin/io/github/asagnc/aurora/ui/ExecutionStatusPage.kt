package io.github.asagnc.aurora.ui

import android.os.Build
import android.os.Process
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun ExecutionStatusPage(available: Boolean, tools: Boolean, dismiss: () -> Unit) {
    FullPage(if (tools) "工具能力" else "执行环境", dismiss) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            CapabilitySection("Android 原生执行", if (available) "执行器已打包" else "当前 ABI 无可用执行器",
                "UID ${Process.myUid()} · API ${Build.VERSION.SDK_INT} · ${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}\n" +
                    "Agent 直接调用命令；参数按原值传入。标准输出和错误分开保存，超时与取消会处理同一进程组。")
            if (tools) {
                CapabilitySection("文件工具", "已接入 Agent", "批量读取、版本校验编辑与文件列举；会话空间、共享只读资源与保留工作区分开管理。")
                CapabilitySection("浏览器工具", "已接入 Agent", "开页、读页、元素操作、同步脚本、关页及批量操作。页面与用户浏览器共用；网站访问仍受实际网络和页面限制。")
            }
            CapabilitySection("Root 执行", "尚未接入", "Root 连接当前只验证 UID 0 和只读状态。普通命令不会因为连接 Root 而自动获得系统权限。")
            CapabilitySection("后台任务与终端", "尚未接入", "当前为前台监督命令，无交互式 PTY，也不保证系统终止应用后的持续执行。中断结果不会自动重放。")
            CapabilitySection("Skills、Linux 与编译链", "尚未接入", "Skills 安装/运行、PRoot/chroot、APK/NDK 编译工具包，以及抓包、发包和逆向插件仍需继续实现与验收。")
            Text("命令工作目录不等于安全沙箱。原生命令具有 Aurora 当前 UID 的实际访问能力；独立守护化的进程不受普通进程组完整约束。",
                style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
        }
    }
}

@Composable private fun CapabilitySection(title: String, status: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(detail, color = AuroraMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
