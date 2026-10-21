package io.github.asagnc.aurora.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.terminal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.sample
import io.github.asagnc.aurora.core.text

@OptIn(FlowPreview::class)
@Composable internal fun TerminalPage(owner: String, dismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as AuroraApplication
    val manager = app.terminals
    val rootState by app.root.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val ticks = remember(manager) { manager.version.sample(80) }
    val revision by ticks.collectAsStateWithLifecycle(initialValue = 0L)
    var history by remember(owner) { mutableStateOf<List<TerminalRecord>>(emptyList()) }
    var selected by remember(owner) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var canvas by remember { mutableStateOf<TerminalCanvasView?>(null) }
    var snapshot by remember { mutableStateOf<String?>(null) }
    val snapshotLines by produceState<List<String>>(emptyList(), snapshot) {
        value = withContext(Dispatchers.Default) { snapshot.orEmpty().lineSequence().flatMap { it.chunked(2000).asSequence() }.toList() }
    }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(owner) { history = manager.list(owner) }
    val records = remember(history, revision) {
        (history.associateBy { it.id } + manager.current(owner).associateBy { it.id }).values.sortedByDescending { it.started }
    }
    val record = records.firstOrNull { it.id == selected } ?: records.firstOrNull()
    val session = record?.let { manager.session(owner, it.id) }
    fun action(block: suspend () -> Unit) { scope.launch { try { block() } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName } } }
    fun start(root: Boolean) = action {
        creating = true
        try { selected = manager.start(owner, PtyRequest(), root).id } finally { creating = false }
    }
    FullPage("交互终端", dismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ start(false) }, enabled = !creating && manager.available) { Text("新终端") }
                OutlinedButton({ start(true) }, enabled = !creating && rootState.phase == io.github.asagnc.aurora.root.RootPhase.READY) { Text("Root 终端") }
                TextButton({ record?.let { current -> action {
                    snapshot = session?.let { withContext(Dispatchers.Default) { it.screen.transcript() } }
                        ?: manager.read(owner, current.id, TerminalRead(limit = 65536)).text("text")
                } } }, enabled = record != null) { Text("查看与复制") }
            }
            if (records.isEmpty()) Column(Modifier.padding(24.dp)) {
                Text("让 Agent 使用终端", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text("需要交互时，Agent 会自行启动终端。你可以查看过程、接手输入或停止；普通命令继续使用轻量执行器。", color = AuroraMuted)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                records.forEachIndexed { index, item -> FilterChip(record?.id == item.id, { selected = item.id; snapshot = null },
                    { Text("${if (item.backend == "android-root") "Root" else "终端"} ${records.size - index} · ${terminalState(item.state)}") }) }
            }
            record?.let { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("合并输出 · ${item.bytes / 1024} KiB${item.exitCode?.let { " · 退出 $it" }.orEmpty()}", style = MaterialTheme.typography.labelMedium, color = AuroraMuted)
                    TextButton({ action { manager.stop(owner, item.id) } }, enabled = item.state in setOf("starting", "running")) { Text("停止") }
                }
                if (item.bytes > item.stored) Text("日志已达 16 MiB；后续输出只保留实时尾部预览。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                item.reason?.let { Text(it, color = AuroraMuted, style = MaterialTheme.typography.labelSmall) }
                if (snapshot != null) {
                    Column(Modifier.weight(1f)) {
                        Row { CopyTextButton(requireNotNull(snapshot), "复制输出"); TextButton({ snapshot = null }) { Text("返回终端") } }
                        androidx.compose.foundation.lazy.LazyColumn { items(snapshotLines) { line -> SelectionContainer {
                            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        } } }
                    }
                } else if (session != null) {
                    AndroidView(factory = { context -> TerminalCanvasView(context).also { canvas = it } },
                        modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)), update = { view ->
                            val changed = view.screen !== session.screen
                            view.screen = session.screen
                            view.send = { input -> action { manager.write(owner, item.id, input) } }
                            view.resized = { columns, rows -> if (item.state == "running") action { manager.resize(owner, item.id, columns, rows) } }
                            if (changed) { view.follow(); view.fit() }
                            view.postInvalidateOnAnimation()
                        })
                } else Column(Modifier.weight(1f).padding(12.dp)) {
                    Text("这是已结束的历史终端。可查看保存的原始日志预览；原进程不会自动重启。", color = AuroraMuted)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Ctrl-C" to "\u0003", "Tab" to "\t", "Esc" to "\u001b", "↑" to "\u001b[A", "↓" to "\u001b[B").forEach { (label, input) ->
                        TextButton({ action { manager.write(owner, item.id, input) } }, enabled = item.state == "running") { Text(label) }
                    }
                    TextButton({ canvas?.follow() }) { Text("最新") }
                }
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), enabled = item.state == "running", singleLine = true,
                    placeholder = { Text("接手输入，发送后执行") }, trailingIcon = { TextButton({ val input = text; action {
                        manager.write(owner, item.id, "$input\n"); if (text == input) text = ""
                    } }) { Text("发送") } })
            }
            error?.let { message -> Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun terminalState(state: String) = when (state) { "starting" -> "启动中"; "running" -> "运行中"; "exited" -> "已退出"; "failed" -> "失败"; "timed_out" -> "已超时"; else -> "已中断" }
