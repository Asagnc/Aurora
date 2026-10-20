package io.github.asagnc.aurora.data

import java.util.UUID

internal data class AgentRun(val id: String, val owner: String, val state: String)

/** Durable lifecycle; protocol checkpoints and tool side effects remain in their existing ledgers. */
internal class AgentRunStore(private val conversations: ConversationStore) {
    suspend fun queue(id: String, owner: String) = conversations.write { db ->
        require(UUID.fromString(id).toString() == id)
        conversations.requireActive(db, owner)
        db.insertOrThrow("agent_runs", null, values("id" to id, "owner" to owner,
            "state" to "queued", "created_at" to System.currentTimeMillis()))
        db.update("conversations", values("run_state" to "running"), "id = ?", arrayOf(owner))
        Unit
    }

    suspend fun running(id: String) = conversations.write { db ->
        check(db.update("agent_runs", values("state" to "running"), "id = ? AND state = 'queued'", arrayOf(id)) == 1)
    }

    suspend fun finish(id: String, state: String) = conversations.write { db ->
        require(state in setOf("completed", "cancelled", "failed"))
        check(db.update("agent_runs", values("state" to state, "finished_at" to System.currentTimeMillis()),
            "id = ? AND state IN ('queued','running')", arrayOf(id)) == 1)
    }

    suspend fun latest(owner: String): AgentRun? = conversations.read { db ->
        db.rawQuery("SELECT id, owner, state FROM agent_runs WHERE owner = ? ORDER BY created_at DESC, rowid DESC LIMIT 1", arrayOf(owner))
            .use { if (it.moveToFirst()) AgentRun(it.getString(0), it.getString(1), it.getString(2)) else null }
    }
}
