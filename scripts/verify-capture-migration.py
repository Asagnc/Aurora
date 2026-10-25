#!/usr/bin/env python3
"""Execute the actual Kotlin registry DDL on SQLite, preserving pending v8 tickets and rollback barriers."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'app/src/main/kotlin/io/github/asagnc/aurora/data/RootManagedRunRegistry.kt').read_text(encoding='utf-8')


def statements(name):
    body = re.search(r'fun ' + name + r'\(db: SQLiteDatabase\) \{(.*?)\n        \}', SOURCE, re.S).group(1)
    pattern = r'db\.execSQL\(\s*("""[\s\S]*?"""|"[^"\n]*")\s*\)|\bcreate\(db\)'
    result = []
    for match in re.finditer(pattern, body):
        raw = match.group(1)
        if raw is None:
            result.extend(statements('create'))
        else:
            result.append(raw[3:-3] if raw.startswith('"""') else raw[1:-1])
    return result


class CaptureMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:'); self.addCleanup(self.db.close)
        self.db.execute('PRAGMA foreign_keys=ON')
        self.db.execute('CREATE TABLE conversations(id TEXT PRIMARY KEY)')
        self.db.execute('INSERT INTO conversations VALUES (?)', ('owner',))
        self.db.execute("""CREATE TABLE root_managed_runs(id TEXT PRIMARY KEY, owner TEXT NOT NULL REFERENCES conversations(id),
            kind TEXT NOT NULL CHECK(kind='frida'), ticket TEXT NOT NULL, tool_run_id TEXT)""")
        self.db.execute('CREATE INDEX root_managed_owner ON root_managed_runs(owner,id)')
        self.rows = [('a', 'owner', 'frida', '{"opaque":"未知旧意图\\n保留","protocol":1}', 'tool-1'),
            ('b', 'owner', 'frida', '{"spawnPackage":"owned.fixture","protocol":2}', None)]
        self.db.executemany('INSERT INTO root_managed_runs VALUES (?,?,?,?,?)', self.rows); self.db.commit()

    def migrate(self):
        sql = statements('migrateCapture'); self.assertEqual(len(sql), 6)
        with self.db:
            for command in sql:
                self.db.execute(command)

    def test_pending_tickets_and_tool_ownership_are_byte_preserved(self):
        self.migrate()
        self.assertEqual(self.db.execute('SELECT * FROM root_managed_runs ORDER BY id').fetchall(), self.rows)
        self.assertEqual(self.db.execute('PRAGMA foreign_key_check').fetchall(), [])
        self.assertEqual(self.db.execute("SELECT name FROM sqlite_master WHERE type='index' AND name='root_managed_owner'").fetchall(), [('root_managed_owner',)])

    def test_capture_allowed_unknown_kind_and_foreign_owner_rejected(self):
        self.migrate()
        self.db.execute('INSERT INTO root_managed_runs VALUES (?,?,?,?,?)', ('c', 'owner', 'capture', 'opaque capture intent', 'tool-2'))
        for row in [('d', 'owner', 'daemon', '{}', None), ('d', 'missing-owner', 'capture', '{}', None)]:
            with self.assertRaises(sqlite3.IntegrityError):
                self.db.execute('INSERT INTO root_managed_runs VALUES (?,?,?,?,?)', row)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute('DELETE FROM conversations WHERE id=?', ('owner',))

    def test_upgrade_failure_rolls_back_all_prior_ddl_and_keeps_old_constraint(self):
        sql = statements('migrateCapture')
        with self.assertRaises(sqlite3.OperationalError):
            with self.db:
                self.db.execute('BEGIN')
                for command in sql[:5]:
                    self.db.execute(command)
                self.db.execute('INVALID FIXTURE SQL')
        self.assertEqual(self.db.execute('SELECT * FROM root_managed_runs ORDER BY id').fetchall(), self.rows)
        self.assertIsNone(self.db.execute("SELECT name FROM sqlite_master WHERE name='root_managed_runs_v8'").fetchone())
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute('INSERT INTO root_managed_runs VALUES (?,?,?,?,?)', ('c', 'owner', 'capture', '{}', None))


if __name__ == '__main__':
    unittest.main()
