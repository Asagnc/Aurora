package io.github.asagnc.aurora.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.markdown.compose.LazyMarkdownSuccess
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import com.mikepenz.markdown.utils.resolveImageAlt
import com.mikepenz.markdown.utils.resolveImageLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import io.github.asagnc.aurora.core.replyPreviewEnd

@Composable internal fun RichReply(text: String, owner: String?, streaming: Boolean, openLink: (String) -> Unit) {
    var reader by remember { mutableStateOf(false) }
    val end = remember(text) { replyPreviewEnd(text) }
    val long = end < text.length
    val preview = if (long) text.take(end) else text
    val uriHandler = remember(openLink) { object : UriHandler { override fun openUri(uri: String) = openLink(uri) } }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        if (streaming) StreamingReply(preview, owner) else CompletedReply(preview, owner)
        if (long) TextButton(onClick = { reader = true }) { Text("阅读完整回复") }
        if (reader) Dialog(onDismissRequest = { reader = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.systemBarsPadding().padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("完整回复", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        CopyTextButton(text, "复制完整回复")
                        TextButton(onClick = { reader = false }) { Text("关闭") }
                    }
                    CompletedReply(text, owner, lazy = true)
                }
            }
        }
    }
}

@Composable private fun CompletedReply(text: String, owner: String?, lazy: Boolean = false) {
    val markdown = rememberMarkdownState(text, retainState = true, immediate = false)
    val components = replyComponents(owner)
    SelectionContainer {
        if (lazy) Markdown(markdownState = markdown, components = components, modifier = Modifier.fillMaxSize(),
            success = { state, parts, modifier -> LazyMarkdownSuccess(state, parts, modifier, PaddingValues(bottom = 24.dp)) },
            error = { Text("排版失败，可使用复制完整回复查看原文。") })
        else Markdown(markdownState = markdown, components = components, modifier = Modifier.fillMaxWidth(),
            error = { Text(text, style = MaterialTheme.typography.bodyLarge) })
    }
}

@Composable private fun StreamingReply(text: String, owner: String?) {
    var epoch by remember { mutableIntStateOf(0) }
    val latest by rememberUpdatedState(text)
    key(epoch) {
        val parser = rememberStreamingMarkdownState(lookupLinks = false)
        val markdown = remember(parser) { FrozenStreamingMarkdown(parser) }
        var failed by remember { mutableStateOf(false) }
        LaunchedEffect(markdown) {
            var previous = ""
            snapshotFlow { latest }.collect { current ->
                if (!current.startsWith(previous)) { epoch++; return@collect }
                if (!failed && current.length > previous.length) {
                    try { withContext(Dispatchers.Default) { markdown.appendSnapshot(current, current.substring(previous.length)) } }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { failed = true }
                }
                previous = current
            }
        }
        SelectionContainer {
            if (failed) Text(text, style = MaterialTheme.typography.bodyLarge)
            else Markdown(streamingMarkdownState = markdown, components = replyComponents(owner), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun replyComponents(owner: String?) = markdownComponents(
    codeFence = { model -> MarkdownCodeFence(model.content, model.node, block = { code, language, style -> ReplyCode(code, language, style) }) },
    codeBlock = { model -> MarkdownCodeBlock(model.content, model.node, block = { code, language, style -> ReplyCode(code, language, style) }) },
    image = { model ->
        model.node.resolveImageLink(model.content, LocalReferenceLinkHandler.current)?.let { source ->
            ArtifactImage(owner, source, model.node.resolveImageAlt(model.content).orEmpty())
        }
    },
    inlineImage = { model ->
        model.node.resolveImageLink(model.content, LocalReferenceLinkHandler.current)?.let { source ->
            ArtifactImage(owner, source, model.node.resolveImageAlt(model.content).orEmpty())
        }
    },
)

@Composable private fun ReplyCode(code: String, language: String?, style: TextStyle) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(language?.take(40) ?: "代码", Modifier.weight(1f), color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
                CopyTextButton(code, "复制代码")
            }
            SelectionContainer { Text(code, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                style = style.copy(fontFamily = FontFamily.Monospace)) }
        }
    }
}
