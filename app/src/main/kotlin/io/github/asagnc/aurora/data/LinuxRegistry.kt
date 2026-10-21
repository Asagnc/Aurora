package io.github.asagnc.aurora.data

import android.database.sqlite.SQLiteDatabase
import io.github.asagnc.aurora.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

@Serializable internal data class LinuxVersion(val id: String, val manifest: LinuxRootfsManifest, val installedAt: Long)

internal class LinuxRegistry(private val conversations: ConversationStore) {
    suspend fun versions(): List<LinuxVersion> = conversations.read { db ->
        db.rawQuery("SELECT id,manifest,installed_at FROM linux_versions ORDER BY installed_at DESC,id", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(LinuxVersion(cursor.getString(0), WireJson.decodeFromString(cursor.getString(1)), cursor.getLong(2))) }
        }
    }
    suspend fun current(): String? = conversations.read { db ->
        db.rawQuery("SELECT current_id FROM linux_settings WHERE singleton=1", null).use { if (it.moveToFirst()) it.getString(0) else null }
    }
    suspend fun pending(): List<Pair<String, String>> = conversations.read { db ->
        db.rawQuery("SELECT id,version_id FROM linux_operations", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
        }
    }
    suspend fun begin(operation: String, version: String) = conversations.write { db ->
        db.insertOrThrow("linux_operations", null, values("id" to operation, "version_id" to version)); Unit
    }
    suspend fun clear(operation: String) = conversations.write { it.delete("linux_operations", "id=?", arrayOf(operation)); Unit }
    suspend fun select(version: String) = conversations.write { db ->
        require(db.rawQuery("SELECT 1 FROM linux_versions WHERE id=?", arrayOf(version)).use { it.moveToFirst() })
        db.execSQL("INSERT OR REPLACE INTO linux_settings(singleton,current_id) VALUES(1,?)", arrayOf(version)); Unit
    }
    suspend fun publish(operation: String, version: LinuxVersion) = conversations.write { db ->
        db.insertWithOnConflict("linux_versions", null, values("id" to version.id, "manifest" to WireJson.encodeToString(version.manifest),
            "installed_at" to version.installedAt), SQLiteDatabase.CONFLICT_IGNORE)
        db.execSQL("INSERT OR REPLACE INTO linux_settings(singleton,current_id) VALUES(1,?)", arrayOf(version.id))
        db.delete("linux_operations", "id=?", arrayOf(operation)); Unit
    }
    suspend fun lease(owner: String, version: String): String = conversations.write { db ->
        conversations.requireActive(db, owner)
        val token = UUID.randomUUID().toString()
        db.insertOrThrow("linux_leases", null, values("token" to token, "owner" to owner, "version_id" to version)); token
    }
    suspend fun release(token: String) = conversations.write { it.delete("linux_leases", "token=?", arrayOf(token)); Unit }
    suspend fun unused(): List<String> = conversations.read { db ->
        db.rawQuery("SELECT id FROM linux_versions WHERE id NOT IN (SELECT current_id FROM linux_settings) AND id NOT IN (SELECT version_id FROM linux_leases)", null)
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    }
    suspend fun reserveRemoval(operation: String, version: String) = conversations.write { db ->
        require(!db.rawQuery("SELECT 1 FROM linux_settings WHERE current_id=? UNION SELECT 1 FROM linux_leases WHERE version_id=?", arrayOf(version, version)).use { it.moveToFirst() })
        db.delete("linux_versions", "id=?", arrayOf(version))
        db.insertOrThrow("linux_operations", null, values("id" to operation, "version_id" to version)); Unit
    }
    companion object {
        fun create(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS linux_versions(id TEXT PRIMARY KEY,manifest TEXT NOT NULL,installed_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS linux_settings(singleton INTEGER PRIMARY KEY CHECK(singleton=1),current_id TEXT NOT NULL REFERENCES linux_versions(id))")
            db.execSQL("CREATE TABLE IF NOT EXISTS linux_leases(token TEXT PRIMARY KEY,owner TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,version_id TEXT NOT NULL REFERENCES linux_versions(id))")
            db.execSQL("CREATE INDEX IF NOT EXISTS linux_lease_version ON linux_leases(version_id)")
            db.execSQL("CREATE TABLE IF NOT EXISTS linux_operations(id TEXT PRIMARY KEY,version_id TEXT NOT NULL)")
        }
    }
}
