package io.github.asagnc.aurora.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.asPainter
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.ImageArtifact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope

@Composable internal fun ArtifactImage(owner: String?, source: String, description: String) {
    val context = LocalContext.current
    val app = context.applicationContext as AuroraApplication
    var retry by remember(source) { mutableIntStateOf(0) }
    var full by remember(source) { mutableStateOf(false) }
    val result by produceState<Result<Pair<ImageArtifact, coil3.Image>>?>(null, owner, source, retry) {
        value = null
        value = try {
            val id = requireNotNull(owner) { "图片需要会话空间" }
            coroutineScope { app.tasks.run(id) {
                val image = app.images.resolve(id, source)
                Result.success(image to loadOwnedImage(app, id, image, 1200))
            } }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { Result.failure(IllegalStateException("图片无法读取或格式不受支持")) }
    }
    val image = result?.getOrNull()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = { if (image != null) full = true }, shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
                if (image != null) Image(painter = remember(image.second) { image.second.asPainter(context) },
                    contentDescription = description.ifBlank { image.first.description },
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else if (result == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图片加载失败", color = AuroraMuted)
                    TextButton(onClick = { retry++ }) { Text("重试") }
                }
            }
        }
        Text(description.ifBlank { image?.first?.description ?: "图片" }, color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
    }
    if (full && image != null && owner != null) ImageViewer(owner, image.first) { full = false }
}
