package io.github.asagnc.aurora.data

import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class ToolRun(val id: String, val callId: String, val tool: String, val state: String, val result: String?)

class ToolRunStore(private val conversations: ConversationStore) {
    suspend fun start(owner: String, call: ToolCall): String = conversations.write { db ->
        conversations.requireActive(db, owner)
        db.rawQuery("SELECT 1 FROM tool_runs WHERE owner = ? AND call_id = ? LIMIT 1", arrayOf(owner, call.id)).use {
            check(!it.moveToFirst()) { "Tool call already recorded; inspect its outcome before replaying" }
        }
        val id = UUID.randomUUID().toString()
        db.insertOrThrow("tool_runs", null, values("owner" to owner, "id" to id, "call_id" to call.id,
            "tool" to call.name, "state" to "started", "started_at" to System.currentTimeMillis()))
        id
    }

    suspend fun finish(id: String, result: String): Unit = conversations.write { db ->
        require(db.update("tool_runs", values("state" to "completed", "result" to result), "id = ? AND state = 'started'", arrayOf(id)) == 1)
    }

    suspend fun list(owner: String): List<ToolRun> = conversations.read { db ->
        db.rawQuery("SELECT id, call_id, tool, state, result FROM tool_runs WHERE owner = ? ORDER BY rowid", arrayOf(owner))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(ToolRun(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4))) } }
    }

    suspend fun markUncertain(owner: String): Unit = conversations.write { db ->
        db.update("tool_runs", values("state" to "uncertain"), "owner = ? AND state = 'started'", arrayOf(owner))
    }

    suspend fun recent(owner: String): List<ToolRun> = conversations.read { db ->
        db.rawQuery("SELECT id, call_id, tool, state, substr(result, 1, 8001) FROM tool_runs WHERE owner = ? ORDER BY rowid DESC LIMIT 50", arrayOf(owner))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(ToolRun(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4))) }.asReversed() }
    }

    fun recording(owner: String, delegate: ToolExecutor, changed: suspend () -> Unit = {}): ToolExecutor = object : ToolExecutor {
        override val definitions get() = delegate.definitions
        override suspend fun execute(call: ToolCall): String {
            val id = start(owner, call)
            changed()
            val result = try { delegate.execute(call) } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                objectOf("error" to string(failure.javaClass.simpleName)).toString()
            }
            finish(id, result)
            changed()
            return result
        }
    }
}
