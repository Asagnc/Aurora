package io.github.asagnc.aurora.ui

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.browser.*
import io.github.asagnc.aurora.core.OwnedFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BrowserUploadSheet(manager: BrowserUploadManager, request: BrowserUploadRequest) {
    val app=LocalContext.current.applicationContext as AuroraApplication
    val scope=rememberCoroutineScope()
    var namespace by remember { mutableStateOf("conversation") }
    var directory by remember { mutableStateOf("") }
    var entries by remember(namespace, directory) { mutableStateOf(emptyList<Pair<String,Boolean>>()) }
    var selected by remember { mutableStateOf(emptyList<BrowserUploadManager.Selection>()) }
    var listingError by remember(namespace, directory) { mutableStateOf<String?>(null) }
    suspend fun source(space: String): OwnedFiles = when(space) {
        "conversation" -> app.spaces.conversation(request.tab.owner)
        else -> app.spaces.sharedResources()
    }
    fun external(uris: List<Uri>) {
        if(uris.isEmpty())return
        if(uris.size>8 || (!request.multiple && uris.size>1)){request.error="本次最多选择 ${if(request.multiple)8 else 1} 个文件";return}
        scope.launch { manager.select(request,uris=uris) }
    }
    val single=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->external(listOfNotNull(uri))}
    val multiple=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->external(uris)}
    val mimeTypes=remember(request.id){request.accept.mapNotNull { type->
        if(type.startsWith('.'))MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1))
        else type.takeIf{it.matches(Regex("[a-z0-9!#$&^_.+*-]+/[a-z0-9!#$&^_.+*-]+"))}
    }.ifEmpty{listOf("*/*")}.distinct().toTypedArray()}
    DisposableEffect(request.id){onDispose{if(manager.pending===request)manager.cancelPending(request.tab.owner)}}
    LaunchedEffect(namespace,directory){
        val targetSpace = namespace; val targetDirectory = directory
        try{
            entries=app.tasks.run(request.tab.owner){source(targetSpace).list(targetDirectory).filterNot{it.first.substringAfterLast('/').startsWith(".aurora-")}}
            listingError=null
        }catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){entries=emptyList();listingError="无法读取此空间"}
    }
    FullPage("选择上传文件", { manager.cancelPending(request.tab.owner) }) {
        Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("文件按需复制为只读上传快照，关闭页面后释放。最多 ${if(request.multiple)8 else 1} 个，保留快照总量不超过 512 MiB。",
                color=AuroraMuted,style=MaterialTheme.typography.bodySmall)
            AuroraTabs(listOf("conversation" to "会话文件", "resources" to "共享资源"), namespace, !request.busy) {
                namespace = it; directory = ""
            }
            TextButton({if(request.multiple)multiple.launch(mimeTypes)else single.launch(mimeTypes)},enabled=!request.busy){Text("从系统文件选择")}
            if(directory.isNotEmpty())TextButton({directory=directory.substringBeforeLast('/',"")},enabled=!request.busy){Text("返回上级")}
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                items(entries,key={it.first}){(path,isDirectory)->
                    val selection=BrowserUploadManager.Selection(namespace,path)
                    Surface(onClick={
                        if(isDirectory)directory=path
                        else if(selection in selected)selected=selected-selection
                        else if(!request.multiple)selected=listOf(selection)
                        else if(selected.size<8)selected=selected+selection
                    },enabled=!request.busy,shape=MaterialTheme.shapes.medium,
                        color=if(selection in selected)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow){
                        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            Text(if(isDirectory)"目录" else if(selection in selected)"已选" else "文件",color=AuroraMuted,style=MaterialTheme.typography.labelMedium)
                            Text(path.substringAfterLast('/'),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            listingError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            request.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(request.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(bottom=24.dp),horizontalArrangement=Arrangement.SpaceBetween){
                TextButton({manager.cancelPending(request.tab.owner)}){Text("取消")}
                Button({scope.launch{manager.select(request,selected)}},enabled=selected.isNotEmpty()&&!request.busy){Text(if(request.busy)"正在准备…" else "上传所选 ${selected.size} 个")}
            }
            Text("目录每次显示前 200 项。Root 文件请先导出为会话产物。",color=AuroraMuted,style=MaterialTheme.typography.bodySmall)
        }
    }
}
