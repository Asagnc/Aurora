package io.github.asagnc.aurora.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import io.github.asagnc.aurora.runtime.ChatState

@Composable internal fun ComposerContext(state: ChatState, click: () -> Unit) {
    val used = state.usage.input
    val limit = state.selected?.contextLimit?.takeIf { it > 0 }
    val fraction = if (used != null && limit != null) used.toFloat() / limit else null
    val description = fraction?.let { "上下文用量：%.0f%%".format(it * 100) } ?: "上下文用量：暂无数据"
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val width = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val showPercent = width >= 350.dp && density.fontScale <= 1.2f
    IconButton(click, Modifier.width(if (showPercent) 64.dp else 48.dp).height(48.dp).semantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, -90f, 360f, false, style = stroke)
            fraction?.let { drawArc(accent, -90f, it.coerceIn(0f, 1f) * 360, false, style = stroke) }
        }
        if (showPercent) Text(fraction?.let { "%.0f%%".format(it * 100) } ?: "—", style = MaterialTheme.typography.labelMedium, color = AuroraMuted)
        }
    }
}

@Composable internal fun ComposerMetrics(state: ChatState, metric: (String) -> Unit) {
    val speed = state.totalUsage.output?.takeIf { state.seconds > 0 }?.let { "≈%.1f tok/s".format(it / state.seconds) } ?: "— tok/s"
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MetricButton(speed, R.drawable.ic_gauge, Modifier.weight(1f)) { metric("speed") }
        MetricButton(state.usage.input?.let { compactTokens(it) + " tok" } ?: "— tok", R.drawable.ic_database,
            Modifier.weight(1f)) { metric("context") }
        MetricButton(state.usage.cacheShare?.let { "缓存 %.0f%%".format(it * 100) } ?: "缓存 —",
            R.drawable.ic_database, Modifier.weight(1f)) { metric("cache") }
    }
}

private fun compactTokens(count: Long): String = when {
    count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

@Composable private fun MetricButton(text: String, icon: Int, modifier: Modifier, click: () -> Unit) {
    TextButton(click, modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 2.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = AuroraMuted)) {
        Icon(painterResource(icon), null, Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
