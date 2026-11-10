package io.github.asagnc.aurora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop
import com.itos.coui.chip.ChipTabRow
import com.itos.coui.chip.CouiChipTabItem

/** Both destinations edit one unsaved connection and share its selected model catalog. */
@Composable internal fun ProviderTabs(models: Boolean, enabled: Boolean, select: (Boolean) -> Unit) {
    AuroraTabs(listOf("connection" to "连接配置", "models" to "模型"),
        if (models) "models" else "connection", enabled) { select(it == "models") }
}

@Composable internal fun AuroraTabs(options: List<Pair<String, String>>, selected: String,
    enabled: Boolean = true, select: (String) -> Unit) {
    val backdrop = rememberCouiBackdrop()
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(Modifier.matchParentSize().couiBackdrop(backdrop).background(MaterialTheme.colorScheme.background))
        ChipTabRow(items = options.map { CouiChipTabItem(it.first, it.second) },
            selectedKey = selected, onSelectedKeyChange = { select(it as String) },
            backdrop = backdrop, enabled = enabled)
    }
}
