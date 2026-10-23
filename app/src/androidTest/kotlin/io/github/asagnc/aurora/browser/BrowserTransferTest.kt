package io.github.asagnc.aurora.browser

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

/** Compiled on host. Actual ArrayBuffer/DOM semantics require final Android execution. */
@RunWith(AndroidJUnit4::class)
class BrowserTransferTest {
    private data class Fixture(val browser: BrowserController, val pages: ToolExecutor, val transfer: ToolExecutor,
        val page: JsonObject, val root: Path, val files: OwnedFiles)

    @Test fun responseBlobAndEmptyFilesPublishExactBytes() = runBlocking { fixture { f ->
        for ((script, size) in listOf("new Response(Uint8Array.from({length:150000},(_,i)=>i%251))" to 150000,
            "new Blob([])" to 0)) {
            val result=transfer(f,"browser_download","script" to string(script))
            assertNull(result.toString(),result["error"])
            val bytes=withContext(Dispatchers.IO){Files.readAllBytes(f.root.resolve(requireNotNull(result.text("path"))))}
            assertEquals(size,bytes.size)
            assertArrayEquals(ByteArray(size){(it%251).toByte()},bytes)
        }
    } }

    @Test fun responseFilenameStarAndMimeBecomeDisplayHintsOnly() = runBlocking { fixture { f ->
        val result = transfer(f, "browser_download", "script" to string(
            "new Response(null,{headers:{'Content-Type':'application/pdf; x=1','Content-Disposition':\"attachment; filename=fallback.pdf; filename*=UTF-8''%E6%8A%A5%E5%91%8A.pdf\"}})"))
        assertNull(result.toString(), result["error"])
        assertEquals("报告.pdf", result.text("displayName"))
        assertEquals("application/pdf", result.text("mimeType"))
        assertTrue(requireNotNull(result.text("path")).endsWith("/download.bin"))
        assertNull(result["disposition"])
    } }

    @Test fun selectedFilesPopulateRealInputAndFormDataIncludingEmptyFile() = runBlocking { fixture { f ->
        val text="Aurora文件".repeat(10000)
        f.files.editBatch(listOf(FileEdit("first.txt",null,text), FileEdit("empty.txt",null,"")))
        val result=transfer(f,"browser_upload","selector" to string("#file"),"files" to JsonArray(listOf("first.txt","empty.txt").map {
            objectOf("namespace" to string("conversation"),"path" to string(it),"mime" to string("text/plain"))
        }))
        assertNull(result.toString(),result["error"])
        val observation=call(f.pages,"browser_evaluate",*(common(f)+arrayOf("script" to string(
            "Promise.all(new FormData(document.querySelector('form')).getAll('upload').map(async f=>({size:f.size,matches:(await f.text())===(f.name==='first.txt'?'Aurora文件'.repeat(10000):'')}))).then(files=>({files,events:window.events}))"),
            "awaitPromise" to JsonPrimitive(true))))
        assertNull(observation.toString(),observation["error"])
        val value=observation["result"]!!.jsonObject["value"]!!.jsonObject
        val files=value["files"]!!.jsonArray
        assertTrue(files.all { it.jsonObject["matches"]!!.jsonPrimitive.boolean })
        assertEquals(0L,files[1].jsonObject.number("size"))
        assertEquals(listOf("input","change"),value["events"]!!.jsonArray.map{it.jsonPrimitive.content})
    } }

    @Test fun oversizeFailureAndOuterCancellationRemoveTemporaryFiles() = runBlocking { fixture { f ->
        val failure=transfer(f,"browser_download","script" to string("new Blob([new Uint8Array(100)])"),"maxBytes" to JsonPrimitive(64))
        assertNotNull(failure.text("error"));assertNoFiles(f.root)
        try {
            withTimeout(100){transfer(f,"browser_download","script" to string("new Promise(()=>{})"))}
            fail("Parent cancellation was swallowed")
        }catch(_:TimeoutCancellationException){}
        assertNoFiles(f.root)
    } }

    @Test fun navigationRejectsOldDocumentAndForeignOwnerCannotTransfer() = runBlocking { fixture { f ->
        val result=transfer(f,"browser_download","script" to string("new Promise(()=>setTimeout(()=>location.href='/next',10))"))
        assertNotNull(result.text("error"));assertNoFiles(f.root)
        val foreign=BrowserTransferTool(f.browser,UUID.randomUUID().toString(),f.root,emptyMap())
        val rejected=call(foreign,"browser_download",*(common(f)+arrayOf("script" to string("new Blob([])"))))
        assertNotNull(rejected.text("error"))
    } }

    @Test fun artifactIntentMustBeRecordedBeforeAnyFileOrPageRequest()=runBlocking{fixture{f->
        val owner=withContext(Dispatchers.Main){f.browser.tabs.single().owner}
        var reserved=false
        val tool=BrowserTransferTool(f.browser,owner,f.root,mapOf("conversation" to f.files),prepared={_,directory->
            assertFalse(withContext(Dispatchers.IO){Files.exists(f.root.resolve(directory))})
            reserved=true
            throw java.io.IOException("simulate ledger failure")
        })
        val result=call(tool,"browser_download",*(common(f)+arrayOf("script" to string("window.downloadAttempt=1;new Blob([])"))))
        assertTrue(reserved);assertNotNull(result.text("error"))
        assertFalse(withContext(Dispatchers.IO){Files.exists(f.root.resolve("runs"))})
        val observed=call(f.pages,"browser_evaluate",*(common(f)+arrayOf("script" to string("window.downloadAttempt||0"))))
        assertEquals(0L,observed["result"]!!.jsonObject.number("value"))
    }}

    private suspend fun assertNoFiles(root: Path)=withContext(Dispatchers.IO){
        Files.walk(root.resolve("runs")).use{assertFalse(it.anyMatch{file->Files.isRegularFile(file)})}
    }
    private fun common(f:Fixture)=arrayOf("page" to f.page.getValue("page"),"url" to f.page.getValue("url"),"revision" to f.page.getValue("revision"))
    private suspend fun transfer(f:Fixture,name:String,vararg extra:Pair<String,JsonElement>)=call(f.transfer,name,*(common(f)+extra))
    private suspend fun call(tool:ToolExecutor,name:String,vararg args:Pair<String,JsonElement>)=
        WireJson.parseToJsonElement(tool.execute(ToolCall(UUID.randomUUID().toString(),name,objectOf(*args)))).jsonObject

    private suspend fun fixture(block:suspend(Fixture)->Unit){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as AuroraApplication).ready.await()
        val owner=UUID.randomUUID().toString()
        val root=withContext(Dispatchers.IO){Files.createTempDirectory(context.cacheDir.toPath(),"browser-transfer-")}
        val browser=withContext(Dispatchers.Main){BrowserController(context,saveProxy=false,intercept={request->
            if(request.url.host=="transfer.fixture")WebResourceResponse("text/html","UTF-8",
                "<form><input id='file' type='file' name='upload' multiple accept='.txt'></form><script>window.events=[];const f=document.querySelector('#file');for(const e of ['input','change'])f.addEventListener(e,()=>window.events.push(e));</script>".byteInputStream())else null
        })}
        try{
            val pages=BrowserToolExecutor(browser,owner)
            val page=call(pages,"browser_open","url" to string("https://transfer.fixture/"))
            assertNull(page.toString(),page["error"])
            val files=withContext(Dispatchers.IO){OwnedFiles(root)}
            block(Fixture(browser,pages,BrowserTransferTool(browser,owner,root,mapOf("conversation" to files)),page,root,files))
        }finally{
            browser.deleteOwner(owner)
            withContext(Dispatchers.IO){root.toFile().deleteRecursively()}
        }
    }
}
