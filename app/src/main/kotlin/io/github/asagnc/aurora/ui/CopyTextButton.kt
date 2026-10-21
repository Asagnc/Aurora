package io.github.asagnc.aurora.ui

import android.content.ClipData
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.R
import kotlinx.coroutines.launch

@Composable internal fun CopyTextButton(text: String, label: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    IconButton(onClick = { scope.launch {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
    } }) {
        Icon(painterResource(R.drawable.ic_copy), label, Modifier.size(18.dp), tint = AuroraMuted)
    }
}
