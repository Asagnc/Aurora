package io.github.asagnc.aurora.data

import android.database.sqlite.SQLiteDatabase
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.encodeToString

/** Secret ownership intents are recorded before touching the shared vault. Model credentials are never enumerated. */
internal class SkillSourceRegistry(private val conversations: ConversationStore) {
    suspend fun profiles(): List<SkillSourceProfile> = conversations.read { db ->
        db.rawQuery("SELECT configuration FROM skill_sources ORDER BY repository", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(WireJson.decodeFromString<SkillSourceProfile>(cursor.getString(0)).also { it.validate() }) }
        }
    }
    suspend fun put(profile: SkillSourceProfile) = conversations.write { db ->
        profile.validate()
        require(profile.credentialId == null || db.rawQuery("SELECT 1 FROM skill_source_secrets WHERE id=?", arrayOf(profile.credentialId)).use { it.moveToFirst() })
        val fields = values("repository" to profile.repository, "configuration" to WireJson.encodeToString(profile))
        if (db.update("skill_sources", fields, "id=?", arrayOf(profile.id)) == 0) {
            fields.put("id", profile.id); db.insertOrThrow("skill_sources", null, fields)
        }
        Unit
    }
    suspend fun ownCredential(id: String) = conversations.write { db ->
        require(java.util.UUID.fromString(id).toString() == id)
        db.insertOrThrow("skill_source_secrets", null, values("id" to id)); Unit
    }
    suspend fun orphanCredentials(): List<String> {
        val referenced = profiles().mapNotNull { it.credentialId }.toSet()
        return conversations.read { db -> db.rawQuery("SELECT id FROM skill_source_secrets", null).use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.getString(0).takeUnless { it in referenced }?.let(::add) }
        } }
    }
    suspend fun forgetCredential(id: String) = conversations.write { db -> db.delete("skill_source_secrets", "id=?", arrayOf(id)); Unit }

    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS skill_sources(id TEXT PRIMARY KEY,repository TEXT NOT NULL UNIQUE,configuration TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS skill_source_secrets(id TEXT PRIMARY KEY)")
        }
    }
}
