package io.github.asagnc.aurora.uiqualification

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Group
import com.itos.coui.layout.Item
import com.itos.coui.shape.CouiCapsuleShape
import kotlinx.coroutines.launch

@Composable internal fun ChatPreview() {
    var draft by rememberSaveable { mutableStateOf("") }
    var copied by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val reply = "界面应该先让人读得舒服，再让操作顺手。\n\n消息时间放在正文外，复制等操作放到回复下方，输入区保留明确的层级。"
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("以下是资格夹具，不是模型回复", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.align(Alignment.End), horizontalAlignment = Alignment.End) {
            Surface(shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                SelectionContainer { Text("让对话更自然一点。", Modifier.padding(16.dp)) }
            }
            Text("2026/11/09 20:26 · 示例时间", Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Aurora", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        SelectionContainer { Text(reply, style = MaterialTheme.typography.bodyLarge) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("复制预览回复", reply))); copied = true
            } }) { Icon(painterResource(R.drawable.ic_copy), "复制预览回复", Modifier.size(18.dp)) }
            Text(if (copied) "已复制预览文本" else "2026/11/09 20:27 · 示例时间",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = CouiCapsuleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                TextField(draft, { draft = it }, placeholder = { Text("输入区测试，不发送请求") },
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth())
                Text("本地预览 · 无网络和执行权限", Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Group(title = "组件与可读性") {
        Item(title = { Text("设置列表") }, summary = { Text("COUI 原生 Item／Group，检查圆角、留白和触摸反馈") }, onClick = {})
        Item(title = { Text("长文字与大字体") }, summary = {
            Text("切换系统字体大小、旋转屏幕和打开输入法，检查顶部安全区、内容滚动与底部可达性。") })
    }
}
