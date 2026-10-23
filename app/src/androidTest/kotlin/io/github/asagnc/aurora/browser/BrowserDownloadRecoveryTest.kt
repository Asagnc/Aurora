package io.github.asagnc.aurora.browser

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

/** Real SQLite/file integration; compiled only until final Android acceptance. */
@RunWith(AndroidJUnit4::class)
class BrowserDownloadRecoveryTest {
    @Test fun uncertainResultStaysUncertainWhilePublishedArtifactIsRetained()=runBlocking{fixture{app,owner,root->
        val directory=record(app,owner,true);val path=root.resolve(directory)
        withContext(Dispatchers.IO){Files.writeString(path.resolve("download.bin"),"published");Files.writeString(path.resolve("keep"),"sibling")}
        assertEquals(1,app.browserDownloads.recover(owner))
        val row=app.toolRuns.list(owner).single()
        assertEquals("uncertain",row.state)
        val note=WireJson.parseToJsonElement(requireNotNull(row.result)).jsonObject
        assertEquals("published_retained",note.text("downloadRecovery"));assertEquals("interrupted",note.text("error"))
        withContext(Dispatchers.IO){
            assertFalse(Files.exists(path.resolve("download.bin.partial")))
            assertEquals("published",Files.readString(path.resolve("download.bin")))
            assertEquals("sibling",Files.readString(path.resolve("keep")))
        }
        assertEquals(0,app.browserDownloads.recover(owner))
    }}
    @Test fun activeAndOtherOwnerRecordsAreNeverSelected()=runBlocking{fixture{app,owner,root->
        val active=record(app,owner,false)
        val other=app.conversations.create("Other recovery fixture")
        try{
            val stale=record(app,other,true)
            assertEquals(0,app.browserDownloads.recover(owner))
            assertEquals("started",app.toolRuns.list(owner).single().state)
            assertTrue(withContext(Dispatchers.IO){Files.exists(root.resolve(active).resolve("download.bin.partial"))})
            assertEquals(1,app.browserDownloads.recover(other))
            val otherRoot=app.spaces.commandRoot(other)
            assertFalse(withContext(Dispatchers.IO){Files.exists(otherRoot.resolve(stale).resolve("download.bin.partial"))})
        }finally{app.spaces.delete(other,app.tasks::quiesce)}
    }}
    @Test fun boundedRecoveryProgressesAndAmbiguousEntriesRemainRetryable()=runBlocking{fixture{app,owner,root->
        record(app,owner,true);record(app,owner,true)
        assertEquals(1,app.browserDownloads.recover(owner,1));assertEquals(1,app.browserDownloads.recover(owner,1))
        val directory=record(app,owner,true);val partial=root.resolve(directory).resolve("download.bin.partial")
        withContext(Dispatchers.IO){Files.delete(partial);Files.createDirectory(partial)}
        assertEquals(0,app.browserDownloads.recover(owner))
        assertNull(app.toolRuns.list(owner).last().result)
        withContext(Dispatchers.IO){Files.delete(partial)}
        assertEquals(1,app.browserDownloads.recover(owner))
        assertEquals("uncertain",app.toolRuns.list(owner).last().state)
    }}
    @Test fun schemaSixMigrationPreservesHistoryAndRecoveryUsesPendingIndex(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="browser-recovery-migration-${UUID.randomUUID()}.db"
        val owner=UUID.randomUUID().toString()
        try{
            io.github.asagnc.aurora.data.AuroraDatabase(context,name).use { helper ->
                val db=helper.writableDatabase
                db.execSQL("DROP INDEX browser_download_pending")
                db.execSQL("INSERT INTO conversations(id,title,updated_at) VALUES(?,?,?)",arrayOf<Any>(owner,"preserved",1))
                db.execSQL("INSERT INTO tool_runs(owner,id,call_id,tool,state,started_at,artifact_directory) VALUES(?,?,?,?,?,?,?)",
                    arrayOf<Any>(owner,"record","call","browser_download","uncertain",1,"runs/${UUID.randomUUID()}"))
                db.version=6
            }
            io.github.asagnc.aurora.data.AuroraDatabase(context,name).use { helper ->
                val db=helper.readableDatabase;assertEquals(8,db.version)
                db.rawQuery("SELECT title FROM conversations WHERE id=?",arrayOf(owner)).use{assertTrue(it.moveToFirst());assertEquals("preserved",it.getString(0))}
                for(filter in listOf(null,owner)){
                    val (sql,args)=io.github.asagnc.aurora.data.browserDownloadRecoveryQuery(filter,64)
                    db.rawQuery(sql,args).use{assertEquals(1,it.count)}
                    val plan=db.rawQuery("EXPLAIN QUERY PLAN "+sql,args).use{cursor->buildList{while(cursor.moveToNext())add(cursor.getString(3))}.joinToString("\n")}
                    assertTrue(plan,plan.contains("browser_download_pending"))
                    assertFalse(plan,plan.contains("USE TEMP B-TREE"))
                }
            }
        }finally{context.deleteDatabase(name)}
    }
    private suspend fun record(app:AuroraApplication,owner:String,uncertain:Boolean):String{
        val call=ToolCall(UUID.randomUUID().toString(),"browser_download",objectOf())
        app.toolRuns.start(owner,call)
        val directory="runs/${UUID.randomUUID()}"
        app.toolRuns.attachArtifact(owner,call.id,directory)
        val root=app.spaces.commandRoot(owner)
        withContext(Dispatchers.IO){Files.createDirectories(root.resolve(directory));Files.writeString(root.resolve(directory).resolve("download.bin.partial"),"stale")}
        if(uncertain)app.toolRuns.markUncertain(owner)
        return directory
    }
    private suspend fun fixture(block:suspend(AuroraApplication,String,Path)->Unit){
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AuroraApplication
        app.ready.await();val owner=app.conversations.create("Download recovery fixture")
        try{block(app,owner,app.spaces.commandRoot(owner))}finally{app.spaces.delete(owner,app.tasks::quiesce)}
    }
}
