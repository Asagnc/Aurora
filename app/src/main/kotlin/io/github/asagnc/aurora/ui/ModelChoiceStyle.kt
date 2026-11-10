package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.shape.CouiCapsuleShape

@Composable internal fun ChoiceCapsule(label: String, selected: Boolean, click: () -> Unit,
    modifier: Modifier = Modifier, description: String = label, enabled: Boolean = true) {
    FilterChip(selected, click, modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
        enabled = enabled, shape = CouiCapsuleShape, border = null,
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
        label = { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) ({ Text("✓", color = MaterialTheme.colorScheme.primary) }) else null)
}

