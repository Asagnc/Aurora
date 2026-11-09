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

@Composable internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

/** Display-only status; connection or qualification is never inferred from a tap. */
@Composable internal fun SettingsStatus(title: String, status: String, icon: Int) {
    Card(Modifier.fillMaxWidth()) {
        ListItem(overlineContent = { Text(title) }, leadingContent = { Icon(painterResource(icon), null) }) {
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable internal fun SettingsDetails(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val angle by animateFloatAsState(if (expanded) 180f else 0f, animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
    Card(Modifier.fillMaxWidth()) {
        ListItem(onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = if (expanded) "已展开" else "已收起" },
            supportingContent = { Text(summary) }, trailingContent = {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, Modifier.rotate(angle))
            }) { Text(title) }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable internal fun SettingsChevron() {
    Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, Modifier.rotate(-90f))
}

@Composable internal fun SettingsNotice(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        ListItem(leadingContent = { Icon(painterResource(R.drawable.ic_info), null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text(message) }
    }
}
