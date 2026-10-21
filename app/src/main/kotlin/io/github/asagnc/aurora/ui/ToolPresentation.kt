package io.github.asagnc.aurora.ui

import io.github.asagnc.aurora.core.WireJson
import io.github.asagnc.aurora.core.summarizeToolResult
import io.github.asagnc.aurora.data.ToolRun
import kotlinx.serialization.json.*

internal data class ToolPresentation(val event: ToolRun, val title: String, val status: String, val failed: Boolean)

internal fun presentTool(event: ToolRun): ToolPresentation {
    val summary = summarizeToolResult(event.result)
    val failed = summary?.error != null || (summary?.exitCode ?: 0L) != 0L
    val uncertain = event.state == "uncertain" || summary?.uncertain == true
    val title = when (event.tool) {
        "command_execute" -> "执行原生命令"
        "command_execute_root" -> "执行 Root 命令"
        "files_list" -> "查看文件列表"
        "files_read_batch" -> "批量读取文件"
        "files_edit_batch" -> "批量编辑文件"
        "browser_list" -> "查看浏览器页面"
        "browser_open" -> "打开网页"
        "browser_open_batch" -> "批量打开网页"
        "browser_read" -> "读取网页"
        "browser_read_batch" -> "批量读取网页"
        "browser_action" -> "操作网页元素"
        "browser_evaluate" -> "执行页面脚本"
        "browser_close" -> "关闭网页"
        else -> event.tool
    }
    val status = when {
        event.state == "started" -> "进行中"
        uncertain -> "结果待核实"
        summary?.error == "command_timeout" -> "已超时"
        summary?.error == "command_stopped" -> "已停止"
        failed -> "返回错误"
        else -> "已返回"
    }
    return ToolPresentation(event, title, status, failed)
}

private val resultJson = Json { prettyPrint = true }

internal fun toolResultPreview(result: String?): String {
    if (result == null) return "尚无返回结果"
    val text = result.take(8000)
    if (result.length > 8000) return "$text\n（预览已截断，原记录保留）"
    if (text.count { it == '{' || it == '[' } > 64) return text
    val formatted = runCatching { resultJson.encodeToString(JsonElement.serializer(), WireJson.parseToJsonElement(text)) }.getOrDefault(text)
    return formatted.take(8000) + if (formatted.length > 8000) "\n（格式化预览已截断，原记录保留）" else ""
}
