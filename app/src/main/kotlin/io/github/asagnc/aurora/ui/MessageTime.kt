package io.github.asagnc.aurora.ui

import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow

/** Formats persisted wall-clock time in the device locale and 12/24-hour preference. */
@Composable internal fun MessageTime(timestamp: Long) {
    LocalConfiguration.current
    val text = DateUtils.formatDateTime(LocalContext.current, timestamp,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or
            DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME)
    Text(text, color = AuroraMuted, style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}
