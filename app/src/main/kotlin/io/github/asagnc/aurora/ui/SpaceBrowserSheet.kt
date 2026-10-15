package io.github.asagnc.aurora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.runtime.ChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun SpaceBrowserSheet(model: ChatViewModel, initial: String, dismiss: () -> Unit) {
    var space by remember { mutableStateOf(initial) }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(emptyList<Pair<String, Boolean>>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(space, path) {
        try { entries = model.listSpace(space, path); error = null }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { entries = emptyList(); error = "无法读取此空间；会话空间需要先创建会话" }
    }
    ModalBottomSheet(dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxHeight(0.72f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (space == "resources") "Skills 与共享资源" else "空间与工具", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("conversation" to "会话", "resources" to "共享读", "workspace" to "共享写").forEach { (name, label) ->
                    FilterChip(space == name, { space = name; path = "" }, label = { Text(label) })
                }
            }
            Text(if (space == "resources") "只读资源目录。此页面可浏览资源，Skills 安装和运行尚未接入。" else
                if (space == "conversation") "文件随会话删除，原件保留。" else "此工作区跨会话保留。", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            if (path.isNotEmpty()) TextButton({ path = path.substringBeforeLast('/', "") }) { Text("上一级 · $path") }
            error?.let { Text(it, color = AuroraMuted, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.isEmpty() && error == null) item { Text("空间中暂无文件", Modifier.padding(vertical = 24.dp), color = AuroraMuted) }
                items(entries, key = { it.first }) { (name, directory) ->
                    Surface(onClick = {
                        if (directory) path = name else scope.launch {
                            try {
                                val file = model.readSpace(space, name)
                                preview = file.text?.let { it + if (file.next != null) "\n\n（仅显示前 64 KiB）" else "" }
                                    ?: "无法显示为文本。文件大小：${file.size} 字节；状态：${file.error ?: "二进制文件"}"
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { error = "文件读取失败" }
                        }
                    }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, AuroraBorder)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp)) {
                            LocalIcon(if (directory) R.drawable.ic_folder_open else R.drawable.ic_chat_bubble, if (directory) "目录" else "文件")
                            Spacer(Modifier.width(12.dp)); Text(name.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Text("每页最多列出 200 项", Modifier.padding(bottom = 24.dp), color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
    preview?.let { content -> AlertDialog(onDismissRequest = { preview = null }, title = { Text("文件预览") },
        text = { LazyColumn { item { SelectionContainer { Text(content, style = MaterialTheme.typography.bodySmall) } } } },
        confirmButton = { TextButton({ preview = null }) { Text("关闭") } }) }
}
