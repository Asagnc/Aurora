package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.itos.coui.layout.Item
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor

/** Native COUI anchored preference menu, shared by connection and appearance pages. */
@Composable internal fun <T> AuroraChoice(title: String, selected: T, choices: List<T>,
    choose: (T) -> Unit, enabled: Boolean = LocalFormEnabled.current, label: (T) -> String) {
    var expanded by remember { mutableStateOf(false) }
    val anchor = rememberCouiMenuAnchor()
    BoxWithConstraints {
        Item(modifier = Modifier.couiMenuAnchor(anchor), onClick = { anchor.open { expanded = true } }, enabled = enabled,
            trailing = { Text(label(selected), Modifier.widthIn(max = maxWidth / 2), color = AuroraMuted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }) { Text(title) }
        Menu(expanded = expanded, onDismissRequest = { expanded = false }, anchor = anchor,
            items = choices.map { CouiMenuItem(it, label(it), selected = it == selected, enabled = enabled) },
            onItemClick = { expanded = false; choose(it.value) })
    }
}
