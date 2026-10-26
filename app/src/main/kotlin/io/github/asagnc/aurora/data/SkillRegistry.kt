package io.github.asagnc.aurora.data

import android.database.sqlite.SQLiteDatabase
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable internal data class SkillEntry(val id: String, val hash: String, val enabled: Boolean,
    val source: SkillSource, val metadata: SkillMetadata, val bytes: Long) {
    fun summary() = objectOf("id" to string(id), "version" to string(hash), "name" to string(metadata.name),
        "description" to string(metadata.description), "compatibility" to string(metadata.compatibility),
        "source" to string(source.label.ifBlank { source.input.substringBefore('?') }), "revision" to string(source.revision),
        "enabled" to JsonPrimitive(enabled), "bytes" to JsonPrimitive(bytes),
        "sourceProfile" to (source.profileId?.let(::string) ?: JsonNull),
        "signatureVerifiedAtInstall" to JsonPrimitive(source.verification != null),
        "signingKeySha256" to (source.verification?.keySha256?.let(::string) ?: JsonNull))
}

internal class SkillRegistry(private val conversations: ConversationStore) : SkillPublicationJournal {
    suspend fun entries(): List<SkillEntry> = conversations.read { db ->
        db.rawQuery("SELECT s.id,s.current_hash,s.enabled,s.source,v.metadata,v.bytes FROM skills s JOIN skill_versions v ON v.hash=s.current_hash ORDER BY s.id", null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(SkillEntry(cursor.getString(0), cursor.getString(1), cursor.getInt(2) != 0,
                WireJson.decodeFromString(cursor.getString(3)), WireJson.decodeFromString(cursor.getString(4)), cursor.getLong(5))) } }
    }
    suspend fun versions(id: String): List<SkillEntry> = conversations.read { db ->
        db.rawQuery("SELECT r.hash,s.enabled,CASE WHEN s.current_hash=r.hash THEN s.source ELSE r.source END,v.metadata,v.bytes FROM skill_releases r JOIN skills s ON s.id=r.skill_id JOIN skill_versions v ON v.hash=r.hash WHERE s.id=? ORDER BY r.installed_at DESC,r.hash", arrayOf(id))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(SkillEntry(id, cursor.getString(0), cursor.getInt(1) != 0,
                WireJson.decodeFromString(cursor.getString(2)), WireJson.decodeFromString(cursor.getString(3)), cursor.getLong(4))) } }
    }
    override suspend fun begin(id: String, hashes: List<String>) = conversations.write { db ->
        db.insertOrThrow("skill_operations", null, values("id" to id, "hashes" to JsonArray(hashes.map(::string)).toString()))
        Unit
    }
    suspend fun finish(id: String, entries: List<SkillEntry>) = conversations.write { db ->
        entries.forEach { entry ->
            db.insertWithOnConflict("skill_versions", null, values("hash" to entry.hash, "metadata" to WireJson.encodeToString(entry.metadata),
                "bytes" to entry.bytes), SQLiteDatabase.CONFLICT_IGNORE)
            val fields = values("current_hash" to entry.hash, "source" to WireJson.encodeToString(entry.source), "updated_at" to System.currentTimeMillis())
            if (db.update("skills", fields, "id=?", arrayOf(entry.id)) == 0) {
                fields.put("id", entry.id); fields.put("enabled", 1); db.insertOrThrow("skills", null, fields)
            }
            db.insertWithOnConflict("skill_releases", null, values("skill_id" to entry.id, "hash" to entry.hash,
                "source" to WireJson.encodeToString(entry.source), "installed_at" to System.currentTimeMillis()), SQLiteDatabase.CONFLICT_IGNORE)
        }
        db.delete("skill_operations", "id=?", arrayOf(id)); Unit
    }
    override suspend fun pending(): List<Pair<String, List<String>>> = conversations.read { db ->
        db.rawQuery("SELECT id,hashes FROM skill_operations", null).use { cursor -> buildList { while (cursor.moveToNext())
            add(cursor.getString(0) to WireJson.parseToJsonElement(cursor.getString(1)).jsonArray.map { it.jsonPrimitive.content }) } }
    }
    override suspend fun known(hash: String): Boolean = conversations.read { db ->
        db.rawQuery("SELECT 1 FROM skill_versions WHERE hash=?", arrayOf(hash)).use { it.moveToFirst() }
    }
    override suspend fun clearOperation(id: String) = conversations.write { it.delete("skill_operations", "id=?", arrayOf(id)); Unit }
    suspend fun pin(owner: String, entries: List<SkillEntry>) = conversations.write { db ->
        conversations.requireActive(db, owner)
        entries.forEach { entry -> db.insertWithOnConflict("skill_pins", null, values("owner" to owner, "skill_id" to entry.id,
            "hash" to entry.hash), SQLiteDatabase.CONFLICT_IGNORE) }; Unit
    }
    suspend fun enabled(id: String, enabled: Boolean) = conversations.write { db ->
        require(db.update("skills", values("enabled" to if (enabled) 1 else 0), "id=?", arrayOf(id)) == 1)
    }
    suspend fun select(id: String, hash: String, source: SkillSource) = conversations.write { db ->
        require(db.rawQuery("SELECT 1 FROM skill_releases WHERE skill_id=? AND hash=?", arrayOf(id, hash)).use { it.moveToFirst() })
        require(db.update("skills", values("current_hash" to hash, "source" to WireJson.encodeToString(source)), "id=?", arrayOf(id)) == 1)
    }
    suspend fun remove(id: String): List<String> = conversations.write { db ->
        val owners = db.rawQuery("SELECT DISTINCT owner FROM skill_pins WHERE skill_id=?", arrayOf(id))
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        db.update("skills", values("enabled" to 0), "id=?", arrayOf(id))
        if (owners.isEmpty()) db.delete("skills", "id=?", arrayOf(id))
        owners
    }
    suspend fun garbage(): List<Pair<String, Long>> = conversations.read { db ->
        db.rawQuery("SELECT hash,bytes FROM skill_versions WHERE hash NOT IN (SELECT current_hash FROM skills) AND hash NOT IN (SELECT hash FROM skill_pins)", null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getLong(1)) } }
    }
    suspend fun reserveRemoval(id: String, hash: String) = conversations.write { db ->
        require(!db.rawQuery("SELECT 1 FROM skills WHERE current_hash=? UNION SELECT 1 FROM skill_pins WHERE hash=?", arrayOf(hash, hash)).use { it.moveToFirst() })
        db.delete("skill_releases", "hash=?", arrayOf(hash))
        db.delete("skill_versions", "hash=?", arrayOf(hash))
        db.insertOrThrow("skill_operations", null, values("id" to id, "hashes" to JsonArray(listOf(string(hash))).toString())); Unit
    }

    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS skill_versions(hash TEXT PRIMARY KEY,metadata TEXT NOT NULL,bytes INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS skills(id TEXT PRIMARY KEY,current_hash TEXT NOT NULL REFERENCES skill_versions(hash),enabled INTEGER NOT NULL DEFAULT 1,source TEXT NOT NULL,updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS skill_releases(skill_id TEXT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,hash TEXT NOT NULL REFERENCES skill_versions(hash),source TEXT NOT NULL,installed_at INTEGER NOT NULL,PRIMARY KEY(skill_id,hash))")
            db.execSQL("CREATE TABLE IF NOT EXISTS skill_pins(owner TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,skill_id TEXT NOT NULL,hash TEXT NOT NULL,PRIMARY KEY(owner,skill_id,hash),FOREIGN KEY(skill_id,hash) REFERENCES skill_releases(skill_id,hash))")
            db.execSQL("CREATE INDEX IF NOT EXISTS skill_pin_hash ON skill_pins(hash)")
            db.execSQL("CREATE TABLE IF NOT EXISTS skill_operations(id TEXT PRIMARY KEY,hashes TEXT NOT NULL)")
        }
    }
}
