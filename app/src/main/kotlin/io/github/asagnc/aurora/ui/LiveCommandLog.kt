package io.github.asagnc.aurora.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.CommandLogSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun LiveCommandLog(owner: String, directory: String, live: Boolean) {
    val app = LocalContext.current.applicationContext as AuroraApplication
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var stream by remember(owner, directory) { mutableStateOf("stdout") }
    val snapshot by produceState(CommandLogSnapshot(pending = true), owner, directory, live, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { app.commandLogs.observe(owner, directory, live).collect { value = it } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { value = value.copy(error = "日志暂不可读，可查看最终产物或重新展开。") }
        }
    }
    val output = if (stream == "stdout") snapshot.stdout else snapshot.stderr
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(stream == "stdout", { stream = "stdout" }, label = { Text("标准输出") })
            FilterChip(stream == "stderr", { stream = "stderr" }, label = { Text("错误输出") })
        }
        Text(if (live) "实时日志 · ${output.bytes} 字节已读取" else "输出末尾 · ${output.bytes} 字节",
            color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
        if (output.previewClipped) Text("仅显示末尾 64 KiB；产物文件另受执行器保存预算约束。", color = AuroraMuted, style = MaterialTheme.typography.labelSmall)
        if (output.reset) Text("文件被替换或截短，已重新读取。", color = AuroraMuted, style = MaterialTheme.typography.labelSmall)
        snapshot.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        key(stream) { LogText(output.text, if (live) "等待输出…" else if (snapshot.pending) "执行未生成日志文件" else "（无输出）") }
        if (output.text.isNotEmpty()) CopyTextButton(output.text, "复制当前日志末尾")
    }
}

@Composable private fun LogText(text: String, empty: String) {
    val vertical = rememberScrollState()
    val scope = rememberCoroutineScope()
    var following by remember { mutableStateOf(true) }
    val away by remember { derivedStateOf { vertical.value < vertical.maxValue } }
    LaunchedEffect(vertical) {
        vertical.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = false }
    }
    LaunchedEffect(text) {
        withFrameNanos { }
        if (following) vertical.scrollTo(vertical.maxValue)
    }
    SelectionContainer {
        Text(text.ifEmpty { empty },
            Modifier.fillMaxWidth().heightIn(max = 320.dp).horizontalScroll(rememberScrollState()).verticalScroll(vertical),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    }
    if (!following && away) TextButton(onClick = {
        following = true
        scope.launch { vertical.animateScrollTo(vertical.maxValue) }
    }) { Text("回到末尾") }
}

