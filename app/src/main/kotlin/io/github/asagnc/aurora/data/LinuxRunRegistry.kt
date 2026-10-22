package io.github.asagnc.aurora.data

import android.database.sqlite.SQLiteDatabase
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

/** A durable dispatch intent and its version lease are committed together. */
internal class LinuxRunRegistry(private val conversations: ConversationStore) {
    suspend fun begin(ticket: LinuxRunTicket) = conversations.write { db ->
        ticket.validate(); conversations.requireActive(db, ticket.owner)
        val manifest = db.rawQuery("SELECT manifest FROM linux_versions WHERE id=?", arrayOf(ticket.generation)).use {
            require(it.moveToFirst()) { "Linux generation is absent" }
            WireJson.decodeFromString<LinuxRootfsManifest>(it.getString(0))
        }
        require(manifest.backend == LinuxBackend.CHROOT && manifest.generationId() == ticket.generation)
        db.insertOrThrow("linux_leases", null, values("token" to ticket.runId, "owner" to ticket.owner, "version_id" to ticket.generation))
        db.insertOrThrow("linux_runs", null, values("id" to ticket.runId, "owner" to ticket.owner,
            "ticket" to WireJson.encodeToString(ticket)))
        Unit
    }
    suspend fun contains(id: String): Boolean = conversations.read { db ->
        db.rawQuery("SELECT 1 FROM linux_runs WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
    }
    suspend fun pendingIds(ids: List<String>): Set<String> = conversations.read { db ->
        require(ids.size <= 16)
        if (ids.isEmpty()) return@read emptySet()
        db.rawQuery("SELECT id FROM linux_runs WHERE id IN (${ids.joinToString(",") { "?" }})", ids.toTypedArray()).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }
    suspend fun count(owner: String? = null): Long = conversations.read { db ->
        android.database.DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM linux_runs" +
            (if (owner == null) "" else " WHERE owner=?"), owner?.let { arrayOf(it) })
    }
    suspend fun pending(owner: String? = null): List<LinuxRunTicket> = conversations.read { db ->
        db.rawQuery("SELECT ticket FROM linux_runs" + (if (owner == null) "" else " WHERE owner=?") + " ORDER BY id",
            owner?.let { arrayOf(it) }).use { cursor -> buildList {
            while (cursor.moveToNext()) add(WireJson.decodeFromString<LinuxRunTicket>(cursor.getString(0)).also { it.validate() })
        } }
    }
    suspend fun finish(ticket: LinuxRunTicket, response: JsonObject) = conversations.write { db ->
        require(ticket.confirms(response)) { "Linux exit is not confirmed" }
        db.rawQuery("SELECT ticket FROM linux_runs WHERE id=?", arrayOf(ticket.runId)).use {
            // PTY completion and explicit recovery may independently receive the same proof.
            // A previous exact completion removed the intent; do not touch any other lease.
            if (!it.moveToFirst()) return@write Unit
            require(WireJson.decodeFromString<LinuxRunTicket>(it.getString(0)) == ticket)
        }
        db.delete("linux_runs", "id=?", arrayOf(ticket.runId))
        db.delete("linux_leases", "token=?", arrayOf(ticket.runId)); Unit
    }
    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS linux_runs(id TEXT PRIMARY KEY REFERENCES linux_leases(token),owner TEXT NOT NULL REFERENCES conversations(id),ticket TEXT NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS linux_run_owner ON linux_runs(owner)")
        }
    }
}
