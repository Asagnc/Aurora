package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun BrowserDownloadDetails(encoded: String?, openArtifact: (String) -> Unit) {
    val result by produceState<BrowserDownloadArtifact?>(null, encoded) {
        value = withContext(Dispatchers.Default) { browserDownloadArtifact(encoded) }
    }
    val data = result ?: return
    val metadata = data.metadata
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(metadata.displayName, style = MaterialTheme.typography.titleSmall)
        Text("${metadata.mimeType} · ${data.bytes} 字节",
            color = AuroraMuted, style = MaterialTheme.typography.bodySmall)
        Text("网页提供的名称与类型，仅供参考", color = AuroraMuted, style = MaterialTheme.typography.labelSmall)
        TextButton(onClick = { openArtifact(data.path) }, contentPadding = PaddingValues(0.dp)) { Text("查看下载产物") }
    }
}
