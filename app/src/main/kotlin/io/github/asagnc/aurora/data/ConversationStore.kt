package io.github.asagnc.aurora.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionHeader(val id: String, val title: String, val pinned: Boolean, val updatedAt: Long,
    val runState: String, val activeScope: String?)
data class MessageRow(val sequence: Long, val message: ChatMessage)
data class HistoryCursor(val pinned: Boolean, val updatedAt: Long, val id: String)

class ConversationStore(private val database: AuroraDatabase) {
    private val writer = Mutex()

    suspend fun create(title: String = "新对话"): String = write { db ->
        require(title.isNotBlank() && title.length <= 200)
        val id = UUID.randomUUID().toString()
        db.insertOrThrow("conversations", null, values("id" to id, "title" to title, "updated_at" to System.currentTimeMillis()))
        id
    }

    suspend fun list(after: HistoryCursor? = null, limit: Int = 100): List<SessionHeader> = withContext(Dispatchers.IO) {
        require(limit in 1..200)
        val suffix = if (after == null) "" else " AND (pinned < ? OR (pinned = ? AND (updated_at < ? OR (updated_at = ? AND id > ?))))"
        val args = after?.let { arrayOf(if (it.pinned) "1" else "0", if (it.pinned) "1" else "0", it.updatedAt.toString(), it.updatedAt.toString(), it.id) }
        database.readableDatabase.rawQuery("SELECT * FROM conversations WHERE deleting = 0$suffix ORDER BY pinned DESC, updated_at DESC, id LIMIT $limit", args)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.header()) } }
    }

    suspend fun searchTitle(query: String): List<SessionHeader> = read { db ->
        require(query.length <= 200)
        db.rawQuery("SELECT * FROM conversations WHERE deleting = 0 AND instr(lower(title), lower(?)) > 0 ORDER BY pinned DESC, updated_at DESC, id LIMIT 100", arrayOf(query))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.header()) } }
    }

    suspend fun header(id: String): SessionHeader? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery("SELECT * FROM conversations WHERE id = ? AND deleting = 0", arrayOf(id))
            .use { if (it.moveToFirst()) it.header() else null }
    }

    suspend fun messages(id: String, before: Long = Long.MAX_VALUE, limit: Int = 100): List<MessageRow> = withContext(Dispatchers.IO) {
        require(limit in 1..200)
        database.readableDatabase.rawQuery("SELECT * FROM messages WHERE owner = ? AND seq < ? ORDER BY seq DESC LIMIT $limit", arrayOf(id, before.toString()))
            .use { cursor -> buildList {
                while (cursor.moveToNext()) add(MessageRow(cursor.getLong(cursor.getColumnIndexOrThrow("seq")), ChatMessage(
                    id = cursor.text("id"), role = cursor.text("role"), text = cursor.text("content"), complete = cursor.getInt(cursor.getColumnIndexOrThrow("complete")) != 0)))
            }.reversed() }
    }

    suspend fun appendMessage(id: String, message: ChatMessage): Long = write { db ->
        requireActive(db, id)
        val sequence = db.rawQuery("SELECT COALESCE(MAX(seq), -1) + 1 FROM messages WHERE owner = ?", arrayOf(id))
            .use { it.moveToFirst(); it.getLong(0) }
        db.insertOrThrow("messages", null, values("owner" to id, "seq" to sequence, "id" to message.id,
            "role" to message.role, "content" to message.text, "complete" to if (message.complete) 1 else 0))
        db.update("conversations", values("updated_at" to System.currentTimeMillis()), "id = ?", arrayOf(id))
        sequence
    }

    suspend fun rename(id: String, title: String): Unit = write { db ->
        require(title.isNotBlank() && title.length <= 200)
        requireActive(db, id)
        db.update("conversations", values("title" to title), "id = ?", arrayOf(id))
    }

    suspend fun pin(ids: List<String>, pinned: Boolean): Unit = write { db ->
        require(ids.size in 1..200)
        ids.forEach { id -> requireActive(db, id); db.update("conversations", values("pinned" to if (pinned) 1 else 0), "id = ?", arrayOf(id)) }
    }

    suspend fun setRunState(id: String, state: String): Unit = write { db ->
        require(state in setOf("idle", "running", "interrupted"))
        requireActive(db, id)
        db.update("conversations", values("run_state" to state), "id = ?", arrayOf(id))
    }

    suspend fun recoverInterruptedRuns(): Unit = write { db ->
        db.execSQL("UPDATE agent_runs SET state = 'interrupted', finished_at = ? WHERE state IN ('queued','running')", arrayOf(System.currentTimeMillis()))
        db.execSQL("UPDATE conversations SET run_state = 'interrupted' WHERE run_state = 'running'")
        db.execSQL("UPDATE tool_runs SET state = 'uncertain' WHERE state = 'started'")
    }

    suspend fun saveConnection(connection: Connection): Unit = write { db ->
        db.insertWithOnConflict("connections", null, values("id" to connection.id,
            "configuration" to WireJson.encodeToString(Connection.serializer(), connection)), SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun connections(): List<Connection> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery("SELECT configuration FROM connections ORDER BY id", null).use { cursor -> buildList {
            while (cursor.moveToNext()) add(WireJson.decodeFromString(Connection.serializer(), cursor.getString(0)))
        } }
    }

    internal suspend fun <T> write(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        writer.withLock {
            val db = database.writableDatabase
            db.beginTransactionNonExclusive()
            try { block(db).also { db.setTransactionSuccessful() } } finally { db.endTransaction() }
        }
    }

    internal suspend fun <T> read(block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) { block(database.readableDatabase) }

    internal fun requireActive(db: SQLiteDatabase, id: String) {
        db.rawQuery("SELECT deleting FROM conversations WHERE id = ?", arrayOf(id)).use {
            require(it.moveToFirst() && it.getInt(0) == 0) { "Conversation is absent or being deleted" }
        }
    }
}

internal fun values(vararg fields: Pair<String, Any?>): ContentValues = ContentValues().apply {
    fields.forEach { (key, value) -> when (value) {
        null -> putNull(key)
        is String -> put(key, value)
        is Long -> put(key, value)
        is Int -> put(key, value)
        else -> error("Unsupported database value")
    } }
}
internal fun Cursor.text(name: String): String = getString(getColumnIndexOrThrow(name))
private fun Cursor.header() = SessionHeader(text("id"), text("title"), getInt(getColumnIndexOrThrow("pinned")) != 0,
    getLong(getColumnIndexOrThrow("updated_at")), text("run_state"), getString(getColumnIndexOrThrow("active_scope")))
