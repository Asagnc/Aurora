package io.github.asagnc.aurora.browser

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.asagnc.aurora.AuroraApplication
import io.github.asagnc.aurora.core.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises native callback/URI lifecycle. This does not fabricate a trusted DOM user gesture. */
@RunWith(AndroidJUnit4::class)
class BrowserChooserTest {
    private data class Fixture(val app:AuroraApplication,val owner:String,val root:Path,val browser:BrowserController,val tab:BrowserTab)
    private class Result : ValueCallback<Array<Uri>> {
        val answers=mutableListOf<Array<Uri>?>()
        override fun onReceiveValue(value:Array<Uri>?){answers+=value}
    }
    private fun params(mode:Int=WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)=object:WebChromeClient.FileChooserParams(){
        override fun getMode()=mode
        override fun getAcceptTypes()=arrayOf(".txt")
        override fun isCaptureEnabled()=false
        override fun getTitle():CharSequence="Fixture"
        override fun getFilenameHint():String?=null
        override fun createIntent()=Intent(Intent.ACTION_OPEN_DOCUMENT)
    }
    private fun request(f:Fixture,result:Result):BrowserUploadRequest {
        assertTrue(f.tab.view.webChromeClient!!.onShowFileChooser(f.tab.view,result,params()))
        return requireNotNull(f.browser.uploads.pending)
    }
    @Test fun selectedFilesAreReadOnlyStableSnapshotsAndReleasedOnClose()=runBlocking{fixture{f->
        withContext(Dispatchers.Main){
            val result=Result();val request=request(f,result)
            f.browser.uploads.select(request,listOf(BrowserUploadManager.Selection("conversation","one.txt"),BrowserUploadManager.Selection("conversation","empty.txt")))
            assertEquals(1,result.answers.size);assertNotNull(result.answers.single())
            val uris=requireNotNull(result.answers.single());assertEquals(2,uris.size)
            withContext(Dispatchers.IO){
                Files.write(f.root.resolve("one.txt"),"changed".toByteArray(Charsets.UTF_8))
                assertEquals("original",f.app.contentResolver.openInputStream(uris[0])!!.use{it.readBytes().decodeToString()})
                assertEquals(0,f.app.contentResolver.openInputStream(uris[1])!!.use{it.readBytes().size})
                assertEquals("text/plain",f.app.contentResolver.getType(uris[0]))
            }
            f.browser.close(f.tab);f.browser.uploads.awaitCleanup(f.owner)
            withContext(Dispatchers.IO){assertFailsOpen(f,uris[0])}
        }
    }}
    @Test fun replacementAndNavigationCancelPendingRequestExactlyOnce()=runBlocking{fixture{f->withContext(Dispatchers.Main){
        val first=Result();request(f,first)
        val second=Result();request(f,second)
        assertEquals(1,first.answers.size);assertNull(first.answers.single())
        f.browser.navigate(f.tab,"https://chooser.fixture/next");awaitBrowserLoad(f.tab.document)
        assertEquals(1,second.answers.size);assertNull(second.answers.single())
        f.browser.close(f.tab);assertEquals(1,second.answers.size)
    }}}
    @Test fun nativeSnapshotSurvivesNavigationUntilPageRelease()=runBlocking{fixture{f->withContext(Dispatchers.Main){
        val result=Result();val request=request(f,result)
        f.browser.uploads.select(request,listOf(BrowserUploadManager.Selection("conversation","one.txt")))
        val uri=requireNotNull(result.answers.single()).single()
        f.browser.navigate(f.tab,"https://chooser.fixture/next");awaitBrowserLoad(f.tab.document)
        withContext(Dispatchers.IO){assertEquals("original",f.app.contentResolver.openInputStream(uri)!!.use{it.readBytes().decodeToString()})}
        f.app.spaces.delete(f.owner,f.app.tasks::quiesce,f.browser::deleteOwner)
        withContext(Dispatchers.IO){assertFalse(Files.exists(f.root));assertFailsOpen(f,uri)}
    }}}
    @Test fun unsupportedModesCancelWithoutOpeningFiles()=runBlocking{fixture{f->withContext(Dispatchers.Main){
        val result=Result()
        f.tab.view.webChromeClient!!.onShowFileChooser(f.tab.view,result,params(WebChromeClient.FileChooserParams.MODE_SAVE))
        assertEquals(1,result.answers.size);assertNull(result.answers.single());assertNull(f.browser.uploads.pending)
    }}}
    @Test fun externalPickerCannotTrickAppIntoReadingItsOwnProvider()=runBlocking{fixture{f->withContext(Dispatchers.Main){
        val result=Result();val request=request(f,result)
        val uri=androidx.core.content.FileProvider.getUriForFile(f.app,f.app.packageName+".images",f.root.resolve("one.txt").toFile())
        f.browser.uploads.select(request,uris=listOf(uri))
        assertTrue(result.answers.isEmpty());assertNotNull(request.error)
        f.browser.uploads.cancelPending();assertEquals(1,result.answers.size);assertNull(result.answers.single())
    }}}
    private fun assertFailsOpen(f:Fixture,uri:Uri){
        try{f.app.contentResolver.openInputStream(uri)?.use{fail("Released snapshot still opened")}}
        catch(_:java.io.FileNotFoundException){}
    }
    private suspend fun fixture(block:suspend(Fixture)->Unit){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val app=context.applicationContext as AuroraApplication;app.ready.await()
        val owner=app.conversations.create("Browser chooser fixture")
        val root=app.spaces.commandRoot(owner)
        app.spaces.conversation(owner).editBatch(listOf(FileEdit("one.txt",null,"original"),FileEdit("empty.txt",null,"")))
        val browser=withContext(Dispatchers.Main){BrowserController(context,saveProxy=false,intercept={request->
            if(request.url.host=="chooser.fixture")WebResourceResponse("text/html","UTF-8","<input type='file' accept='.txt' multiple>".byteInputStream())else null
        })}
        try{
            val tools=BrowserToolExecutor(browser,owner)
            val result=WireJson.parseToJsonElement(tools.execute(ToolCall(UUID.randomUUID().toString(),"browser_open",objectOf("url" to string("https://chooser.fixture/"))))).jsonObject
            assertNull(result.toString(),result["error"])
            val tab=withContext(Dispatchers.Main){browser.tabs.single()}
            block(Fixture(app,owner,root,browser,tab))
        }finally{
            if(app.conversations.header(owner)!=null)app.spaces.delete(owner,app.tasks::quiesce,browser::deleteOwner)
        }
    }
}
