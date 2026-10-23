#!/usr/bin/env python3
"""Actual cgroup mkdir/full-record crash windows and empty-only plan recovery."""
import ctypes
import errno
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc") and
    getattr(os, "geteuid", lambda: -1)() == 0, "Explicit Linux Root/cgroup fixture required")
class ScopePlanTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Own/reap only our deliberately orphaned fixture helper.
        if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0):
            raise OSError(ctypes.get_errno(), "subreaper")
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-managed-plan-")
        cls.root = Path(cls.temporary.name); cls.binary = cls.root / "fixture"
        native = ROOT / "app/src/main/cpp"
        sources = [ROOT / "scripts/managed_scope_plan_fixture.c"]
        sources += [native / "managed" / name for name in ["journal.c", "journal_io.c", "journal_identity.c", "scope_plan.c", "scope_plan_codec.c"]]
        sources += [native / "linux" / name for name in ["task_scope.c", "scope_identity.c", "scope_state.c", "scope_process.c"]]
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-Os", "-I" + str(native / "managed"),
            *map(str, sources), "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def setUp(self):
        self.temporary_state = tempfile.TemporaryDirectory(dir=self.root)
        self.state = Path(self.temporary_state.name)
        self.addCleanup(self.temporary_state.cleanup)

    def call(self, mode, ticket=None):
        args = [str(self.binary), mode, str(self.state)] + ([ticket] if ticket else [])
        return subprocess.run(args, capture_output=True, text=True, timeout=8, check=True).stdout.strip()

    def prepare(self, mode):
        lines = self.call(mode).splitlines()
        self.assertEqual(3, len(lines), lines)
        return Path(lines[0]) / lines[1], int(lines[2])

    def recovered(self, group):
        self.assertEqual("recovered 0", self.call("recover"))
        self.assertFalse(group.exists())
        self.assertFalse((self.state / "managed.scope-plan").exists())
        self.assertFalse(list(self.state.glob("*.state")))

    def test_crash_before_mkdir_forgets_only_plan_without_creating_group(self):
        group, _ = self.prepare("before-mkdir")
        self.assertFalse(group.exists()); self.recovered(group)

    def test_crash_after_mkdir_without_full_record_removes_owned_empty_group(self):
        group, _ = self.prepare("after-mkdir")
        self.assertTrue(group.exists()); self.assertFalse(list(self.state.glob("*.state")))
        self.recovered(group)

    def test_complete_record_is_recovered_before_plan_is_forgotten(self):
        group, _ = self.prepare("full")
        self.assertTrue(list(self.state.glob("*.state")))
        self.recovered(group)

    def test_failed_full_record_partial_is_removed_only_after_empty_group_is_removed(self):
        group, _ = self.prepare("partial")
        unrelated = self.state / "unrelated.tmp"; unrelated.write_bytes(b"keep")
        self.recovered(group)
        self.assertEqual([unrelated], list(self.state.glob("*.tmp")))
        self.assertEqual(b"keep", unrelated.read_bytes())

    def test_live_plan_guard_blocks_recovery_even_without_its_lock_reference(self):
        process = subprocess.Popen([str(self.binary), "live", str(self.state)], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            parent, name = process.stdout.readline().strip(), process.stdout.readline().strip()
            process.stdout.readline()
            group = Path(parent) / name
            self.assertTrue(group.exists())
            self.assertEqual(f"error {errno.EBUSY}", self.call("recover"))
            self.assertTrue(group.exists())
        finally:
            process.communicate(input="x", timeout=3)
        self.assertEqual(0, process.returncode); self.recovered(group)

    def test_unrecorded_populated_plan_is_preserved_and_never_killed(self):
        group, child = self.prepare("populated")
        try:
            self.assertGreater(child, 0)
            self.assertEqual(f"error {errno.EBUSY}", self.call("recover"))
            self.assertTrue(Path(f"/proc/{child}").exists())
            self.assertTrue(group.exists())
        finally:
            os.kill(child, signal.SIGKILL); os.waitpid(child, 0)
        self.recovered(group)

    def test_corrupt_plan_is_retained_without_touching_group_until_restored(self):
        group, _ = self.prepare("after-mkdir")
        path = self.state / "managed.scope-plan"; original = path.read_bytes()
        try:
            for offset in [0, 107, 111, 123, 159, 175, len(original) - 1]:
                with self.subTest(offset=offset):
                    changed = bytearray(original); changed[offset] ^= 1; path.write_bytes(changed)
                    self.assertEqual(f"error {errno.EPROTO}", self.call("recover"))
                    self.assertEqual(changed, path.read_bytes()); self.assertTrue(group.exists())
        finally:
            path.write_bytes(original)
        self.recovered(group)

    def test_wrong_ticket_cannot_remove_plan_or_its_group(self):
        group, _ = self.prepare("after-mkdir")
        self.assertEqual(f"error {errno.EPROTO}", self.call("recover", ticket="22" * 32))
        self.assertTrue(group.exists()); self.recovered(group)

    def test_existing_plan_refuses_another_group_creation(self):
        group, _ = self.prepare("after-mkdir")
        original = (self.state / "managed.scope-plan").read_bytes()
        self.assertEqual(f"error {errno.EEXIST}", self.call("replay"))
        self.assertEqual(original, (self.state / "managed.scope-plan").read_bytes())
        self.recovered(group)


if __name__ == "__main__":
    unittest.main()
