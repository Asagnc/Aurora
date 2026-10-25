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
                CapabilitySection("浏览器工具", "已接入 Agent", "开页、读页、元素操作、同步与异步脚本、文件上传下载、关页及批量操作。页面与用户浏览器共用；网站访问仍受实际网络和页面限制。")
                CapabilitySection("HTTP 请求", "已接入 Agent", "显式直连或 HTTP/SOCKS 代理，构造单次请求并保存有界响应。保留 TLS 校验，不自动重定向、携带浏览器登录态或重放未知结果。")
                CapabilitySection("图片产物", "已接入 Agent", "发布会话中的图片或下载 HTTPS 图片；回复中可展示，支持放大、保存与分享。图片随会话删除；视觉模型输入和模型生图 API 尚未接入。")
            }
            CapabilitySection("Root 执行", "连接后提供独立命令工具", "手动连接后，Agent 可调用 Root 原生命令。支持取消、断开清理与服务退出检测；普通命令保持原有权限。实际访问能力取决于系统 SELinux 和 Root 授权状态。")
            CapabilitySection("后台 Agent 任务", "已接入前台服务", "任务运行时可离开页面或切换会话，通过通知返回或停止。系统终止应用后会标记中断，不自动重放工具。")
            CapabilitySection("命令日志", "已接入事件监听", "展开详情可看实时输出，收起或离开后停止监听；每流显示末尾64 KiB，最终产物受保存预算约束。")
            CapabilitySection("交互式终端", "已接入 Agent · 待设备验收", "普通与 Root PTY、游标读取、输入、窗口调整及停止；原生终端查看页。切换页面继续执行，进程死亡标记中断；日志受16 MiB预算约束，未实现日志轮转。")
            CapabilitySection("Skills", "已接入 Agent · 待设备验收", "标准 SKILL.md、ZIP/公共 HTTPS 导入、按需读取、版本固定与更新管理。工作流不等于已安装执行环境。")
            CapabilitySection("Linux 环境", "已接入 Agent · 以环境资格为准", "共享版本、会话工作区与受管执行；Root chroot 和普通 PRoot 分别验证，探测失败不会自动换运行器。")
            CapabilitySection("抓包与逆向", "已接入 Agent · 待集中设备验收", "Root 有界抓包、PCAP 离线分析、静态逆向工具包与 Frida 受管任务。能力需要对应环境和实际自测通过。")
            CapabilitySection("APK 构建与网络进阶", "开发中", "ARM64 Java/Compose 构建和代理主机夹具已通过；JNI、完整工具链安装与代理修改工具仍在接续，尚未作为手机可用能力开放。")
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
