package io.github.asagnc.aurora.data

import io.github.asagnc.aurora.core.*
import java.util.UUID
import kotlinx.serialization.json.JsonObject

class NativeHistoryStore(private val conversations: ConversationStore) {
    suspend fun begin(id: String, history: WireHistory): String = conversations.write { db ->
        conversations.requireActive(db, id)
        val scope = UUID.randomUUID().toString()
        db.insertOrThrow("wire_scopes", null, values("owner" to id, "id" to scope,
            "metadata" to WireJson.encodeToString(WireHistory.serializer(), history.copy(entries = emptyList()))))
        history.entries.forEachIndexed { index, item ->
            db.insertOrThrow("wire_entries", null, values("owner" to id, "scope" to scope, "seq" to index, "content" to item.toString()))
        }
        db.update("conversations", values("active_scope" to scope), "id = ?", arrayOf(id))
        scope
    }

    suspend fun append(id: String, scope: String, expectedCount: Long, entries: List<JsonObject>): Unit = conversations.write { db ->
        conversations.requireActive(db, id)
        val active = db.rawQuery("SELECT active_scope FROM conversations WHERE id = ?", arrayOf(id)).use { it.moveToFirst(); it.getString(0) }
        require(active == scope) { "Native history scope changed" }
        val count = db.rawQuery("SELECT COUNT(*) FROM wire_entries WHERE owner = ? AND scope = ?", arrayOf(id, scope))
            .use { it.moveToFirst(); it.getLong(0) }
        require(count == expectedCount) { "Native history append conflict" }
        entries.forEachIndexed { index, item ->
            db.insertOrThrow("wire_entries", null, values("owner" to id, "scope" to scope, "seq" to count + index, "content" to item.toString()))
        }
    }

    suspend fun load(id: String, scope: String): WireHistory? = conversations.read { db ->
        val metadata = db.rawQuery("SELECT metadata FROM wire_scopes WHERE owner = ? AND id = ?", arrayOf(id, scope))
            .use { if (it.moveToFirst()) it.getString(0) else null } ?: return@read null
        val entries = db.rawQuery("SELECT content FROM wire_entries WHERE owner = ? AND scope = ? ORDER BY seq", arrayOf(id, scope))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(WireJson.parseToJsonElement(cursor.getString(0)) as JsonObject) } }
        WireJson.decodeFromString(WireHistory.serializer(), metadata).copy(entries = entries)
    }
}
