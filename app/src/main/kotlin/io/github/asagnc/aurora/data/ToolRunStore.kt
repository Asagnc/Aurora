package io.github.asagnc.aurora.data

import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import android.database.Cursor

data class ToolRun(val id: String, val callId: String, val tool: String, val state: String, val result: String?,
    val artifactDirectory: String? = null, val presentation: String? = null)

class ToolRunStore(private val conversations: ConversationStore) {
    suspend fun count(owner: String): Long = conversations.read { db ->
        db.rawQuery("SELECT count(*) FROM tool_runs WHERE owner = ?", arrayOf(owner)).use { it.moveToFirst(); it.getLong(0) }
    }
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

    suspend fun finish(id: String, result: String): ToolRun = conversations.write { db ->
        val tool = db.rawQuery("SELECT tool FROM tool_runs WHERE id = ?", arrayOf(id)).use { require(it.moveToFirst()); it.getString(0) }
        val projection = commandProjection(tool, result)
        require(db.update("tool_runs", values("state" to "completed", "result" to result, "presentation" to projection), "id = ? AND state = 'started'", arrayOf(id)) == 1)
        db.rawQuery("SELECT id, call_id, tool, state, substr(result, 1, 8001), artifact_directory, presentation FROM tool_runs WHERE id = ?", arrayOf(id))
            .use { require(it.moveToFirst()); it.toolRun() }
    }

    suspend fun attachArtifact(owner: String, callId: String, directory: String): ToolRun = conversations.write { db ->
        require(commandArtifactId(directory) != null)
        conversations.requireActive(db, owner)
        require(db.update("tool_runs", values("artifact_directory" to directory),
            "owner = ? AND call_id = ? AND state = 'started' AND artifact_directory IS NULL AND tool IN ('command_execute','command_execute_root','command_execute_linux','linux_environment','packet_inspect')",
            arrayOf(owner, callId)) == 1)
        db.rawQuery("SELECT id, call_id, tool, state, result, artifact_directory, presentation FROM tool_runs WHERE owner = ? AND call_id = ?", arrayOf(owner, callId))
            .use { require(it.moveToFirst()); it.toolRun() }
    }

    suspend fun list(owner: String): List<ToolRun> = conversations.read { db ->
        db.rawQuery("SELECT id, call_id, tool, state, result, artifact_directory, presentation FROM tool_runs WHERE owner = ? ORDER BY rowid", arrayOf(owner))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toolRun()) } }
    }

    suspend fun markUncertain(owner: String): Unit = conversations.write { db ->
        db.update("tool_runs", values("state" to "uncertain"), "owner = ? AND state = 'started'", arrayOf(owner))
    }

    suspend fun recent(owner: String): List<ToolRun> = conversations.read { db ->
        db.rawQuery("SELECT id, call_id, tool, state, CASE WHEN tool IN ('command_execute','command_execute_root','command_execute_linux') AND presentation IS NULL THEN result ELSE substr(result, 1, 8001) END, artifact_directory, presentation FROM tool_runs WHERE owner = ? ORDER BY rowid DESC LIMIT 50", arrayOf(owner))
            .use { cursor -> buildList { while (cursor.moveToNext()) {
                val record = cursor.toolRun()
                add(record.copy(result = record.result?.take(8001), presentation = record.presentation ?: record.result?.let { commandProjection(record.tool, it) }))
            } }.asReversed() }
    }

    fun recording(owner: String, delegate: ToolExecutor, changed: suspend (ToolRun) -> Unit = {}): ToolExecutor = object : ToolExecutor {
        override val definitions get() = delegate.definitions
        override suspend fun execute(call: ToolCall): String {
            val id = start(owner, call)
            changed(ToolRun(id, call.id, call.name, "started", null))
            val result = try { delegate.execute(call) } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                objectOf("error" to string(failure.javaClass.simpleName)).toString()
            }
            changed(finish(id, result))
            return result
        }
    }
}

private fun Cursor.toolRun() = ToolRun(getString(0), getString(1), getString(2), getString(3), getString(4), getString(5), getString(6))

private fun commandProjection(tool: String, result: String): String? = when (tool) {
    "command_execute", "command_execute_root", "packet_inspect" -> compactCommandResult(result)
    "command_execute_linux" -> compactLinuxCommandResult(result)
    else -> null
}
