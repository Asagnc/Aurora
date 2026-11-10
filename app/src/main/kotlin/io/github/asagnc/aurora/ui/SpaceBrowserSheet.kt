package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Item
import com.itos.coui.theme.CouiTheme
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.runtime.ChatViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable internal fun SpaceBrowserSheet(model: ChatViewModel, initial: String, conversationOnly: Boolean = false,
    initialPath: String = "", dismiss: () -> Unit) {
    var space by rememberSaveable(initial) { mutableStateOf(initial) }
    var path by rememberSaveable(initialPath) { mutableStateOf(initialPath) }
    var entries by remember(space, path) { mutableStateOf(emptyList<Pair<String, Boolean>>()) }
    var error by remember(space, path) { mutableStateOf<String?>(null) }
    var loading by remember(space, path) { mutableStateOf(true) }
    var query by remember(space, path) { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(space, path) {
        readJob?.cancel(); preview = null
        try { entries = model.listSpace(space, path); error = null }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "无法读取此空间；会话文件需要先创建会话。" }
        finally { loading = false }
    }
    fun back() { if (path.isNotEmpty()) path = path.substringBeforeLast('/', "") else dismiss() }
    val visible = remember(entries, query) { entries.filter { it.first.substringAfterLast('/').contains(query, true) }
        .sortedWith(compareByDescending<Pair<String, Boolean>> { it.second }.thenBy { it.first.lowercase(java.util.Locale.ROOT) }) }
    FullPage(if (conversationOnly) "本会话文件" else "空间与文件", ::back) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (!conversationOnly) item("spaces") { AuroraTabs(listOf("conversation" to "会话文件", "resources" to "共享资源", "workspace" to "工作区"), space) {
                space = it; path = ""
            } }
            item("directory-controls") {
              Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (space) {
                    "resources" -> "共享只读资源；Skills 请在工作流页面管理。"
                    "conversation" -> "文件随会话删除，导入原件保留。"
                    else -> "工作区跨会话保留。"
                }, color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                if (path.isNotEmpty()) TextButton({ back() }) {
                    Text("上一级", maxLines = 1)
                    Text(" · $path", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SearchField(query, { query = it }, "搜索当前目录")
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { SettingsNotice(it) }
            }
            }
                if (visible.isEmpty() && !loading && error == null) item {
                    Text(if (query.isBlank()) "目录中暂无文件" else "没有匹配的文件", Modifier.padding(24.dp), color = AuroraMuted)
                }
                itemsIndexed(visible, key = { _, entry -> entry.first }) { index, (name, directory) ->
                    Surface(Modifier.fillMaxWidth(), color = CouiTheme.colorScheme.card,
                        shape = RoundedCornerShape(topStart = if (index == 0) 18.dp else 0.dp,
                            topEnd = if (index == 0) 18.dp else 0.dp,
                            bottomStart = if (index == visible.lastIndex) 18.dp else 0.dp,
                            bottomEnd = if (index == visible.lastIndex) 18.dp else 0.dp)) {
                        Item(onClick = {
                            if (directory) path = name else {
                                readJob?.cancel()
                                val requestedSpace = space; val requestedPath = path
                                readJob = scope.launch {
                                    try {
                                        val file = model.readSpace(requestedSpace, name)
                                        if (space == requestedSpace && path == requestedPath) preview = file.text?.let {
                                            it + if (file.next != null) "\n\n（仅显示前 64 KiB）" else ""
                                        } ?: "无法显示为文本。文件大小：${file.size} 字节；状态：${file.error ?: "二进制文件"}"
                                    } catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { if (space == requestedSpace && path == requestedPath) error = "文件读取失败" }
                                }
                            }
                        }, icon = { LocalIcon(if (directory) R.drawable.ic_folder_open else R.drawable.ic_document,
                            if (directory) "文件夹" else "文件") }, trailing = { if (directory) SettingsChevron() }) {
                            Text(name.substringAfterLast('/'), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            item("directory-budget") {
                Text("当前目录最多显示 200 项", Modifier.padding(vertical = 16.dp),
                    color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    preview?.let { content -> AuroraPrompt("文件预览", "关闭", { preview = null }, { preview = null }) {
        SelectionContainer { Text(content, style = MaterialTheme.typography.bodySmall) }
    } }
}
