package io.github.asagnc.aurora.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import com.itos.coui.text.Text as CouiText
import com.itos.coui.text.CouiTextKind
import com.itos.coui.layout.Item
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.theme.CouiTheme

@Composable internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CouiText(text = title, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            color = AuroraMuted, kind = CouiTextKind.GroupTitle)
        content()
    }
}

/** Display-only status; connection or qualification is never inferred from a tap. */
@Composable internal fun SettingsStatus(title: String, status: String, icon: Int) {
    Surface(Modifier.fillMaxWidth(), shape = CouiPageDefaults.shape, color = CouiTheme.colorScheme.card) {
        Item(icon = { Icon(painterResource(icon), null) }, summary = { CouiText(text = title, kind = CouiTextKind.PreferenceSummary, color = AuroraMuted) }) {
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable internal fun SettingsDetails(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
    Surface(Modifier.fillMaxWidth(), shape = CouiPageDefaults.shape, color = CouiTheme.colorScheme.card) {
      Column {
        Item(onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = if (expanded) "已展开" else "已收起" },
            summary = { CouiText(text = summary, kind = CouiTextKind.PreferenceSummary, color = AuroraMuted) }, trailing = {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, Modifier.rotate(angle))
            }) { CouiText(text = title, kind = CouiTextKind.PreferenceTitle) }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
      }
    }
}

@Composable internal fun SettingsChevron() {
    Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, Modifier.rotate(-90f))
}

/** A compact notice shares the same card surface as preferences. */
@Composable internal fun SettingsNotice(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = CouiPageDefaults.shape, color = CouiTheme.colorScheme.card) {
        Item(icon = { Icon(painterResource(R.drawable.ic_info), null) }) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
