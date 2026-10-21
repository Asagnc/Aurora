package io.github.asagnc.aurora.ui

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil3.compose.asPainter
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.ImageArtifact
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ImageViewer(owner: String, artifact: ImageArtifact, dismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AuroraApplication
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val file by produceState<File?>(null, owner, artifact.id) {
        try { value = app.images.file(owner, artifact) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failure = "图片已不可用" }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(artifact.mime)) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output -> app.images.copyTo(owner, artifact, output) }
                }
                Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show()
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure = "保存未完成，目标文件可能不完整，请重试" }
            finally { busy = false }
        }
    }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = dismiss) { Text("关闭") }
                Text("${artifact.width} × ${artifact.height}", Modifier.weight(1f), color = AuroraMuted, style = MaterialTheme.typography.labelMedium)
                TextButton(enabled = file != null && !busy, onClick = {
                    val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(artifact.mime) ?: "img"
                    save.launch("Aurora-${artifact.id.take(8)}.$extension")
                }) { Text(if (busy) "保存中" else "保存") }
                TextButton(enabled = file != null, onClick = {
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.images", requireNotNull(file), "Aurora.${artifact.extension}")
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = artifact.mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("图片", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "分享图片"))
                    } catch (_: Exception) { failure = "无法打开分享面板" }
                }) { Text("分享") }
            }
            failure?.let { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
            ZoomedImage(owner, artifact, app, Modifier.weight(1f))
            Text(artifact.description, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun ZoomedImage(owner: String, artifact: ImageArtifact, app: AuroraApplication, modifier: Modifier) {
    val context = LocalContext.current
    var error by remember { mutableStateOf(false) }
    val image by produceState<coil3.Image?>(null, owner, artifact.id) {
        try { value = loadOwnedImage(app, owner, artifact, 3072, full = true) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = true }
    }
    var scale by remember(artifact.id) { mutableFloatStateOf(1f) }
    var offset by remember(artifact.id) { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxWidth().clipToBounds().pointerInput(artifact.id) {
        detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 8f)
            val limitX = size.width * (scale - 1f) / 2
            val limitY = size.height * (scale - 1f) / 2
            offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX), (offset.y + pan.y).coerceIn(-limitY, limitY))
        }
    }, contentAlignment = Alignment.Center) {
        image?.let { loaded -> Image(remember(loaded) { loaded.asPainter(context) }, "图片大图",
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            contentScale = ContentScale.Fit) }
        if (image == null) { if (error) Text("图片解码失败") else CircularProgressIndicator() }
    }
}
