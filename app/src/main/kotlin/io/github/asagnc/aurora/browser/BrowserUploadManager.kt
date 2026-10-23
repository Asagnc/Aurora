package io.github.asagnc.aurora.browser

import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class BrowserUploadRequest(val tab: BrowserTab, private val callback: ValueCallback<Array<Uri>>,
    val multiple: Boolean, val accept: List<String>) {
    val id = UUID.randomUUID().toString()
    private val revision = tab.revision
    private val address = tab.view.url
    private var answered = false
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var job: Job? = null
    fun current() = !answered && !tab.closed && !tab.loading && tab.revision == revision && tab.view.url == address
    fun answer(uris: Array<Uri>?) {
        if(answered) return
        answered = true
        runCatching { callback.onReceiveValue(uris) }
    }
}

/** Native chooser only; programmatic JS clicks are not promoted to trusted gestures. Main-thread state. */
internal class BrowserUploadManager(private val context: Context) {
    data class Selection(val namespace: String, val path: String)
    private data class Lease(val owner: String, val page: String, val snapshot: BrowserUploadSnapshot)
    var pending by mutableStateOf<BrowserUploadRequest?>(null)
        private set
    private val retained = mutableListOf<Lease>()
    private val preparing = Mutex()
    private val sources = BrowserUploadSources(context)
    private class Cleanup(val lease: Lease) { lateinit var job: Deferred<Result<Unit>> }
    private val cleanup = CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val cleaning = mutableMapOf<String, MutableList<Cleanup>>()

    fun request(tab: BrowserTab, callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        cancelPending()
        if(params.mode !in setOf(WebChromeClient.FileChooserParams.MODE_OPEN,WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) ||
            tab.closed || tab.loading || tab.view.url?.startsWith("https://") != true) {
            runCatching{callback.onReceiveValue(null)};return true
        }
        val accept=params.acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.take(20)
        pending=BrowserUploadRequest(tab,callback,params.mode==WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,accept)
        return true
    }
    fun cancelPending(owner: String? = null) {
        val request=pending ?: return
        if(owner != null && request.tab.owner != owner) return
        pending=null;request.job?.cancel();request.answer(null)
    }
    fun releasePage(tab: BrowserTab) {
        if(pending?.tab == tab) cancelPending()
        val leases=retained.filter { it.page==tab.id }
        retained.removeAll(leases.toSet())
        leases.forEach { lease ->
            val record=Cleanup(lease)
            record.job=cleanup.async(start=CoroutineStart.LAZY) {
                val result=withContext(Dispatchers.IO){runCatching{lease.snapshot.close()}}
                if(result.isSuccess){cleaning[lease.owner]?.remove(record);if(cleaning[lease.owner]?.isEmpty()==true)cleaning.remove(lease.owner)}
                result
            }
            cleaning.getOrPut(lease.owner){mutableListOf()}.add(record)
            record.job.start()
        }
    }
    suspend fun awaitCleanup(owner: String) {
        val records=cleaning[owner].orEmpty().toList()
        for(record in records) {
            val result=record.job.await()
            if(result.isFailure)withContext(Dispatchers.IO){record.lease.snapshot.close()}
        }
        cleaning[owner]?.removeAll(records.toSet())
        if(cleaning[owner]?.isEmpty()==true)cleaning.remove(owner)
    }
    suspend fun select(request: BrowserUploadRequest, selections: List<Selection> = emptyList(), uris: List<Uri> = emptyList()) {
        if(pending !== request || request.busy || !request.current()) { if(pending===request)cancelPending();return }
        require(selections.isEmpty() || uris.isEmpty())
        val count=selections.size+uris.size
        require(count in 1..if(request.multiple) 8 else 1)
        request.busy=true;request.error=null;request.job=currentCoroutineContext().job
        val app=context.applicationContext as AuroraApplication
        var snapshot: BrowserUploadSnapshot?=null
        var adopted=false
        try {
            app.tasks.run(request.tab.owner) { preparing.withLock {
              try {
                check(app.conversations.header(request.tab.owner)!=null)
                check(retained.count { it.page==request.tab.id } < 16) { "Close page to release upload snapshots" }
                val remaining=512L*1024*1024-retained.filter { it.owner==request.tab.owner }.sumOf { it.snapshot.bytes }-
                    cleaning[request.tab.owner].orEmpty().sumOf { it.lease.snapshot.bytes }
                check(remaining>0)
                val root=app.spaces.commandRoot(request.tab.owner)
                withContext(Dispatchers.IO) {
                    BrowserUploadSnapshot.recoverOld(root,PROCESS_EPOCH)
                    val files=BrowserUploadSnapshot(root,PROCESS_EPOCH,remaining)
                    snapshot=files
                    sources.copy(request,files,selections,uris)
                }
                check(pending===request && request.current())
                val ready=requireNotNull(snapshot)
                val result=withContext(Dispatchers.IO){
                    ready.entries.map { FileProvider.getUriForFile(context,context.packageName+".images",it.path.toFile(),it.name) }.toTypedArray()
                }
                check(pending===request && request.current())
                retained+=Lease(request.tab.owner,request.tab.id,ready);adopted=true
                pending=null;request.answer(result)
              } finally {
                if(!adopted)withContext(NonCancellable+Dispatchers.IO){snapshot?.close()}
                snapshot=null
              }
            } }
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { if(pending===request)request.error="文件准备失败：请检查类型、权限和剩余预算，或关闭页面释放旧快照后重试" }
        finally {
            request.busy=false;request.job=null
            if(!request.current() && pending===request)cancelPending()
        }
    }
    companion object { private val PROCESS_EPOCH=UUID.randomUUID().toString() }
}
