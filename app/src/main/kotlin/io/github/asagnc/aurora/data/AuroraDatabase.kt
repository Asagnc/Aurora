package io.github.asagnc.aurora.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AuroraDatabase(context: Context, name: String = "aurora.db") : SQLiteOpenHelper(context, name, null, 2) {
    init { setWriteAheadLoggingEnabled(true) }

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE conversations (
            id TEXT PRIMARY KEY, title TEXT NOT NULL, pinned INTEGER NOT NULL DEFAULT 0,
            updated_at INTEGER NOT NULL, run_state TEXT NOT NULL DEFAULT 'idle',
            active_scope TEXT, deleting INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("CREATE INDEX conversation_order ON conversations(deleting, pinned DESC, updated_at DESC, id)")
        db.execSQL("""CREATE TABLE messages (
            owner TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
            seq INTEGER NOT NULL, id TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL,
            complete INTEGER NOT NULL, PRIMARY KEY(owner, seq), UNIQUE(owner, id))""")
        db.execSQL("""CREATE TABLE wire_scopes (
            owner TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
            id TEXT NOT NULL, metadata TEXT NOT NULL, PRIMARY KEY(owner, id))""")
        db.execSQL("""CREATE TABLE wire_entries (
            owner TEXT NOT NULL, scope TEXT NOT NULL, seq INTEGER NOT NULL, content TEXT NOT NULL,
            PRIMARY KEY(owner, scope, seq), FOREIGN KEY(owner, scope) REFERENCES wire_scopes(owner, id) ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE tool_runs (
            owner TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
            id TEXT PRIMARY KEY, call_id TEXT NOT NULL, tool TEXT NOT NULL,
            state TEXT NOT NULL, result TEXT, started_at INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX tool_run_owner ON tool_runs(owner, started_at)")
        db.execSQL("CREATE TABLE connections (id TEXT PRIMARY KEY, configuration TEXT NOT NULL)")
        createTaskRuns(db)
    }

    private fun createTaskRuns(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE agent_runs (
            id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
            state TEXT NOT NULL, created_at INTEGER NOT NULL, finished_at INTEGER)""")
        db.execSQL("CREATE INDEX agent_run_owner ON agent_runs(owner, created_at)")
        db.execSQL("CREATE UNIQUE INDEX agent_run_active ON agent_runs(owner) WHERE state IN ('queued','running')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion == 1 && newVersion == 2)
        createTaskRuns(db)
    }
}
