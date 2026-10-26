#!/usr/bin/env python3
"""Exercise exact new source DDL against retained prior SQLite rows; Android upgrade is separately tested on device."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'app/src/main/kotlin/io/github/asagnc/aurora/data/SkillSourceRegistry.kt').read_text(encoding='utf-8')
DDL = re.findall(r'db\.execSQL\("(CREATE TABLE [^"\n]+)"\)', SOURCE)


class SourceMigration(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:'); self.addCleanup(self.db.close)
        self.db.execute('CREATE TABLE prior_rows(kind TEXT PRIMARY KEY,payload BLOB NOT NULL)')
        self.retained = [('skill', b'old source and pinned version\x00'), ('capture', b'opaque pending Root ticket'), ('message', '保留消息'.encode())]
        self.db.executemany('INSERT INTO prior_rows VALUES (?,?)', self.retained); self.db.commit()

    def migrate(self):
        self.assertEqual(2, len(DDL))
        for statement in DDL:
            self.db.execute(statement)

    def test_ddl_preserves_existing_bytes_and_can_run_with_existing_tables(self):
        with self.db:
            self.migrate(); self.migrate()
        self.assertEqual(self.retained, self.db.execute('SELECT * FROM prior_rows').fetchall())
        self.assertEqual([], self.db.execute('SELECT * FROM skill_sources').fetchall())

    def test_duplicate_repository_does_not_replace_prior_policy(self):
        self.migrate()
        self.db.execute('INSERT INTO skill_sources VALUES (?,?,?)', ('first', 'example/skills', 'original policy'))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute('INSERT INTO skill_sources VALUES (?,?,?)', ('second', 'example/skills', 'replacement'))
        self.assertEqual([('first', 'example/skills', 'original policy')], self.db.execute('SELECT * FROM skill_sources').fetchall())

    def test_failed_upgrade_rolls_back_new_ddl_and_keeps_prior_rows(self):
        with self.assertRaises(sqlite3.OperationalError):
            with self.db:
                self.db.execute('BEGIN'); self.migrate(); self.db.execute('INVALID FIXTURE SQL')
        self.assertEqual(self.retained, self.db.execute('SELECT * FROM prior_rows').fetchall())
        self.assertIsNone(self.db.execute("SELECT name FROM sqlite_master WHERE name='skill_sources'").fetchone())


if __name__ == '__main__':
    unittest.main()
