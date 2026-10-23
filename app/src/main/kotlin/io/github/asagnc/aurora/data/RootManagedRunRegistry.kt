package io.github.asagnc.aurora.data

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.encodeToString

/** Only active/unconfirmed intents. No Linux image dependency and no history cascade. */
internal class RootManagedRunRegistry(private val conversations: ConversationStore) {
    suspend fun begin(ticket: RootManagedRunTicket, toolRunId: String? = null) = conversations.write { db ->
        ticket.validate(); conversations.requireActive(db, ticket.owner)
        if (toolRunId != null) db.rawQuery("SELECT owner FROM tool_runs WHERE id=?", arrayOf(toolRunId)).use {
            require(it.moveToFirst() && it.getString(0) == ticket.owner) { "Tool run owner mismatch" }
        }
        db.insertOrThrow("root_managed_runs", null, values("id" to ticket.runId, "owner" to ticket.owner,
            "kind" to ticket.kind, "ticket" to WireJson.encodeToString(ticket), "tool_run_id" to toolRunId))
        Unit
    }
    suspend fun pending(owner: String): List<RootManagedRunTicket> = conversations.read { db ->
        db.rawQuery("SELECT id,owner,kind,ticket FROM root_managed_runs WHERE owner=? ORDER BY id", arrayOf(owner)).use { cursor ->
            buildList { while (cursor.moveToNext()) {
                val ticket = WireJson.decodeFromString<RootManagedRunTicket>(cursor.getString(3)).also { it.validate() }
                require(ticket.runId == cursor.getString(0) && ticket.owner == cursor.getString(1) && ticket.kind == cursor.getString(2))
                add(ticket)
            } }
        }
    }
    suspend fun count(owner: String): Long = conversations.read { db ->
        DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM root_managed_runs WHERE owner=?", arrayOf(owner))
    }
    suspend fun finish(ticket: RootManagedRunTicket, proof: RootManagedExitProof) = finishConfirmed(ticket, ticket.confirms(proof))
    suspend fun finish(ticket: RootManagedRunTicket, proof: RootManagedRetirementProof) = finishConfirmed(ticket, proof.matches(ticket))

    private suspend fun finishConfirmed(ticket: RootManagedRunTicket, confirmed: Boolean) = conversations.write { db ->
        require(confirmed) { "Root task injection cleanup or retirement is unconfirmed" }
        db.rawQuery("SELECT owner,kind,ticket FROM root_managed_runs WHERE id=?", arrayOf(ticket.runId)).use {
            if (!it.moveToFirst()) return@write Unit
            require(it.getString(0) == ticket.owner && it.getString(1) == ticket.kind &&
                WireJson.decodeFromString<RootManagedRunTicket>(it.getString(2)) == ticket)
        }
        db.delete("root_managed_runs", "id=?", arrayOf(ticket.runId)); Unit
    }
    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS root_managed_runs(
                id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES conversations(id),
                kind TEXT NOT NULL CHECK(kind='frida'), ticket TEXT NOT NULL, tool_run_id TEXT)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS root_managed_owner ON root_managed_runs(owner,id)")
        }
    }
}
