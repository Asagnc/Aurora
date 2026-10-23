package io.github.asagnc.aurora.data

import io.github.asagnc.aurora.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Bounded recovery from existing ledger intents; never changes uncertain into completed. */
internal class BrowserDownloadMaintenance(private val conversations: ConversationStore, private val spaces: SpaceStore) {
    private data class Pending(val id: String,val owner: String,val directory: String)
    suspend fun recover(owner: String? = null, limit: Int = 64): Int {
        require(limit in 1..128)
        val pending=conversations.read { db ->
            val (sql,args)=browserDownloadRecoveryQuery(owner,limit)
            db.rawQuery(sql,args).use { cursor ->
                buildList{while(cursor.moveToNext())add(Pending(cursor.getString(0),cursor.getString(1),cursor.getString(2)))}
            }
        }
        var recovered=0
        for(record in pending){
            currentCoroutineContext().ensureActive()
            try {
                val state=spaces.recoverBrowserDownload(record.owner,record.directory)
                val note=objectOf("error" to string("interrupted"),"outcome" to string("unknown; inspect before retrying"),
                    "downloadRecovery" to string(state),"artifactDirectory" to string(record.directory)).toString()
                conversations.write { db ->
                    recovered += db.update("tool_runs",values("result" to note),
                        "id=? AND owner=? AND tool='browser_download' AND state='uncertain' AND result IS NULL AND artifact_directory=?",
                        arrayOf(record.id,record.owner,record.directory))
                }
            }catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){ /* Ambiguous paths/failed cleanup remain pending; no error text or request secrets persisted. */ }
        }
        return recovered
    }
}

internal const val BROWSER_DOWNLOAD_PENDING = "tool='browser_download' AND state='uncertain' AND result IS NULL AND artifact_directory IS NOT NULL"

internal fun browserDownloadRecoveryQuery(owner: String?,limit: Int): Pair<String,Array<String>> {
    require(limit in 1..128)
    val filter=if(owner==null) "" else " AND owner=?"
    val sql="SELECT p.id,p.owner,p.artifact_directory FROM (SELECT id,owner,artifact_directory FROM tool_runs INDEXED BY browser_download_pending WHERE "+
        BROWSER_DOWNLOAD_PENDING+filter+" ORDER BY owner,rowid LIMIT ?) p "+
        "WHERE EXISTS (SELECT 1 FROM conversations c WHERE c.id=p.owner AND c.deleting=0)"
    return sql to if(owner==null)arrayOf(limit.toString())else arrayOf(owner,limit.toString())
}
