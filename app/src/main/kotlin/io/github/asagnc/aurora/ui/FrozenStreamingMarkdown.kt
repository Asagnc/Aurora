package io.github.asagnc.aurora.ui

import com.mikepenz.markdown.model.StreamingMarkdownState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Publish immutable source text with the parsed AST; the UI never reads a background StringBuilder. */
internal class FrozenStreamingMarkdown(private val parser: StreamingMarkdownState) : StreamingMarkdownState {
    @Volatile private var source = ""
    private val published = MutableStateFlow(parser.snapshot.value)
    override val content: CharSequence get() = source
    override val snapshot = published.asStateFlow()
    override val links get() = parser.links
    override val referenceLinkHandler get() = parser.referenceLinkHandler

    suspend fun appendSnapshot(current: String, delta: String) {
        val parsed = parser.append(delta)
        source = current
        published.value = parsed
    }

    override suspend fun append(chunk: String): StreamingMarkdownState.Snapshot {
        appendSnapshot(source + chunk, chunk)
        return published.value
    }
}
