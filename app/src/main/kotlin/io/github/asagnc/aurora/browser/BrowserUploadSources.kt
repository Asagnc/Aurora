package io.github.asagnc.aurora.browser

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.BrowserUploadSnapshot
import kotlinx.coroutines.*

/** Called on IO. Foreign document URIs are copied; they are never handed directly to WebView. */
internal class BrowserUploadSources(private val context: Context) {
    suspend fun copy(request: BrowserUploadRequest, files: BrowserUploadSnapshot,
        selections: List<BrowserUploadManager.Selection>, uris: List<Uri>) {
        val app=context.applicationContext as AuroraApplication
        val job=currentCoroutineContext().job
        for(selection in selections) {
            val source=when(selection.namespace) {
                "conversation" -> app.spaces.conversation(request.tab.owner)
                "resources" -> app.spaces.sharedResources()
                else -> error("Unsupported upload namespace")
            }
            checkAccept(request,selection.path.substringAfterLast('/'),null)
            files.add(source,selection.path){job.ensureActive()}
        }
        for(uri in uris) {
            require(uri.scheme=="content")
            val authority=requireNotNull(uri.authority)
            val provider=context.packageManager.resolveContentProvider(authority,0)
            require(provider?.applicationInfo?.uid != context.applicationInfo.uid) { "Choose app-owned files through their conversation namespace" }
            require(context.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED) { "Selected document has no read grant" }
            val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)
                ?.use { if(it.moveToFirst())it.getString(0) else null } ?: "upload"
            val mime=context.contentResolver.getType(uri)
            checkAccept(request,name,mime)
            val extension=mime?.let{MimeTypeMap.getSingleton().getExtensionFromMimeType(it.substringBefore(';').lowercase())}
            requireNotNull(context.contentResolver.openInputStream(uri)).use { files.add(name,it,extension){job.ensureActive()} }
        }
    }
    private fun checkAccept(request: BrowserUploadRequest, name: String, supplied: String?) {
        val extension=name.substringAfterLast('.',"").lowercase()
        val mime=supplied ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        require(request.accept.isEmpty() || request.accept.any {
            when { it=="*/*" -> true;it.startsWith('.') -> name.lowercase().endsWith(it)
                it.endsWith("/*") -> mime.startsWith(it.dropLast(1));else -> mime==it }
        })
    }
}
