package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import io.github.asagnc.aurora.data.SkillEntry
import io.github.asagnc.aurora.skills.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

@Composable internal fun SkillFilesPage(entry: SkillEntry, manager: SkillManager, dismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(0) }
    var listing by remember { mutableStateOf<JsonObject?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(0L) }
    var chunk by remember { mutableStateOf<JsonObject?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.hash, query, page, selected, offset) {
        failure = null; chunk = null; listing = null
        try {
            selected?.let { chunk = manager.read(entry, it, offset) }
                ?: run { listing = manager.files(entry, query, page) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failure = "读取失败或内容校验不通过，请检查当前安装版本。" }
    }
    FullPage(selected ?: "工作流文件", {
        if (selected == null) dismiss() else { selected = null; offset = 0 }
    }) {
        if (selected == null) LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(query, { query = it.take(200); page = 0 }, label = { Text("搜索文件") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            listing?.let { result ->
                val files = result["files"]?.jsonArray.orEmpty()
                if (files.isEmpty()) item { Text("没有匹配的文件", color = AuroraMuted) }
                items(files, key = { it.jsonObject.text("path").orEmpty() }) { file ->
                    val data = file.jsonObject
                    TextButton({ selected = data.text("path"); offset = 0 }, Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(data.text("path").orEmpty())
                            Text("${data["size"]} 字节", style = MaterialTheme.typography.bodySmall, color = AuroraMuted)
                        }
                    }
                }
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton({ page = maxOf(0, page - 64) }, enabled = page > 0) { Text("上一页") }
                    TextButton({ page = result["next"]!!.jsonPrimitive.int }, enabled = result["more"]?.jsonPrimitive?.boolean == true) { Text("下一页") }
                    Text("共 ${result["total"]} 项", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                } }
            }
            failure?.let { item { Text(it) } }
            if (listing == null && failure == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            chunk?.let { result ->
                Text("版本 ${entry.hash.take(12)} · 字节 $offset / ${result["size"]}", color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
                Text(result.text("text") ?: "此段无法按 UTF-8 显示，可由 Agent 使用文件或命令工具进一步处理。")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton({ offset = 0 }, enabled = offset > 0) { Text("回到开头") }
                    TextButton({ offset = result["next"]!!.jsonPrimitive.long }, enabled = result["complete"]?.jsonPrimitive?.boolean != true) { Text("继续读取") }
                }
            }
            failure?.let { Text(it) }
            if (chunk == null && failure == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
