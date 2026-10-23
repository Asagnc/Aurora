#!/usr/bin/env python3
"""Root-owned durable intent/release/retirement; no target injection or cgroups."""
import errno
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from managed_journal_fixture import HARNESS

ROOT = Path(__file__).resolve().parents[1]
RUN = "00000000-0000-0000-0000-000000000002"
TICKET = "11" * 32
KEY_BYTES = b"ARI1\n" + RUN.encode() + b"\n" + TICKET.encode() + b"\n"


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc") and
    getattr(os, "geteuid", lambda: -1)() == 0, "Explicit Linux Root compiler fixture required")
class JournalTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-managed-journal-")
        cls.root = Path(cls.temporary.name)
        source = cls.root / "harness.c"; source.write_text(HARNESS, encoding="utf-8")
        cls.binary = cls.root / "harness"
        native = ROOT / "app/src/main/cpp/managed"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-I" + str(native), str(source),
            *[str(native / name) for name in ["journal.c", "journal_identity.c", "journal_io.c"]],
            "-Wl,--wrap=fsync,--wrap=write", "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def setUp(self):
        self.space = tempfile.TemporaryDirectory(dir=self.root)
        self.addCleanup(self.space.cleanup)
        self.state = Path(self.space.name)

    def command(self, operation, run=RUN, ticket=TICKET):
        return [str(self.binary), str(self.state), operation, run, ticket]

    def call(self, operation, run=RUN, ticket=TICKET, fault=None):
        environment = {key: value for key, value in os.environ.items() if key != "AURORA_JOURNAL_FAIL"}
        if fault:
            environment["AURORA_JOURNAL_FAIL"] = fault
        result = subprocess.run(self.command(operation, run, ticket), env=environment,
            capture_output=True, text=True, timeout=3, check=True)
        return json.loads(result.stdout)

    def phase(self, expected):
        result = self.call("status")
        self.assertEqual(expected, result["result"], result)
        return result

    def expect_failure(self, operation, error, **options):
        result = self.call(operation, **options)
        self.assertEqual(-1, result["result"], result)
        self.assertEqual(error, result["error"], result)

    def put(self, name, contents):
        path = self.state / name
        path.write_bytes(contents); path.chmod(0o600)
        return path

    def test_intent_is_durable_one_use_and_cannot_be_replayed_after_reopen(self):
        self.phase(0)
        self.assertEqual(0, self.call("begin")["result"])
        self.assertEqual(KEY_BYTES, (self.state / "managed.intent").read_bytes())
        self.phase(1); self.expect_failure("begin", errno.EEXIST)

    def test_retire_before_intent_blocks_late_start_and_is_idempotent(self):
        self.assertEqual(0, self.call("retire")["result"])
        self.phase(8)
        self.assertEqual(0, self.call("retire")["result"])
        self.expect_failure("begin", errno.ECANCELED)
        self.assertFalse((self.state / "managed.intent").exists())

    def test_worker_identity_persists_before_release_and_release_never_replays(self):
        self.assertEqual(0, self.call("begin")["result"])
        self.assertEqual(0, self.call("ready")["result"])
        value = self.phase(3)
        self.assertEqual((123, 789, 999), (value["guard"], value["worker"], value["ticks"]))
        self.assertEqual(164, (self.state / "managed.ready").stat().st_size)
        self.expect_failure("ready", errno.EEXIST)
        self.assertEqual(0, self.call("release")["result"])
        self.phase(7); self.expect_failure("release", errno.EEXIST)
        self.assertEqual(0, self.call("retire")["result"]); self.phase(15)

    def test_release_requires_intent_and_ready_and_retirement_wins(self):
        self.expect_failure("ready", errno.EPROTO); self.expect_failure("release", errno.EPROTO)
        self.assertEqual(0, self.call("begin")["result"])
        self.expect_failure("release", errno.EPROTO)
        self.assertEqual(0, self.call("ready")["result"])
        self.assertEqual(0, self.call("retire")["result"])
        self.expect_failure("release", errno.ECANCELED); self.expect_failure("ready", errno.ECANCELED)
        self.phase(11)

    def test_wrong_ticket_or_run_cannot_retire_or_overwrite_existing_intent(self):
        self.assertEqual(0, self.call("begin")["result"])
        for options in [{"ticket": "22" * 32}, {"run": RUN[:-1] + "3"}]:
            for operation in ["status", "begin", "ready", "release", "retire"]:
                with self.subTest(operation=operation, options=options):
                    self.expect_failure(operation, errno.EPROTO, **options)
        self.assertEqual(KEY_BYTES, (self.state / "managed.intent").read_bytes())
        self.assertFalse((self.state / "managed.retired").exists())

    def test_corrupt_or_truncated_phase_records_are_retained_and_refused(self):
        for name in ["managed.intent", "managed.retired", "managed.released", "managed.ready"]:
            for contents in [b"", b"unknown", KEY_BYTES + b"tail"]:
                with self.subTest(name=name, contents=contents):
                    path = self.put(name, contents)
                    self.expect_failure("status", errno.EPROTO); self.expect_failure("retire", errno.EPROTO)
                    self.assertEqual(contents, path.read_bytes()); path.unlink()

    def test_out_of_order_release_or_ready_does_not_imply_dispatched(self):
        path = self.put("managed.released", KEY_BYTES)
        self.expect_failure("status", errno.EPROTO); self.expect_failure("begin", errno.EPROTO)
        path.unlink()
        self.assertEqual(0, self.call("begin")["result"])
        self.assertEqual(0, self.call("ready")["result"])
        (self.state / "managed.intent").unlink()
        self.expect_failure("status", errno.EPROTO); self.expect_failure("retire", errno.EPROTO)

    def test_symlink_hardlink_fifo_and_insecure_record_modes_are_never_followed(self):
        external = self.root / (self.state.name + "-external")
        external.write_bytes(KEY_BYTES); external.chmod(0o600)
        self.addCleanup(external.unlink)
        path = self.state / "managed.intent"
        path.symlink_to(external); self.expect_failure("status", errno.ELOOP); path.unlink()
        os.link(external, path); self.expect_failure("status", errno.EPERM); path.unlink()
        os.mkfifo(path, 0o600); self.expect_failure("status", errno.EPERM); path.unlink()
        self.put(path.name, KEY_BYTES).chmod(0o644); self.expect_failure("status", errno.EPERM)
        self.assertEqual(KEY_BYTES, external.read_bytes())

    def test_nonroot_owned_record_or_nonprivate_directory_is_rejected(self):
        path = self.put("managed.intent", KEY_BYTES)
        os.chown(path, 65534, 65534); self.expect_failure("status", errno.EPERM)
        path.unlink(); self.state.chmod(0o755)
        self.expect_failure("begin", errno.EPERM)

    def test_task_lock_excludes_other_processes_until_owner_closes(self):
        for mode in ["hold", "fork-close"]:
            with self.subTest(mode=mode):
                process = subprocess.Popen(self.command(mode), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE, text=True)
                try:
                    self.assertEqual("locked", process.stdout.readline().strip())
                    result = self.call("retire")
                    self.assertEqual(-1, result["opened"]); self.assertIn(result["error"], [errno.EAGAIN, errno.EWOULDBLOCK])
                finally:
                    process.communicate(input="x", timeout=3)
                self.assertEqual(0, process.returncode)
        self.assertEqual(0, self.call("retire")["result"])
        self.expect_failure("begin", errno.ECANCELED)

    def test_process_death_releases_lock_without_deleting_its_inode(self):
        process = subprocess.Popen(self.command("hold"), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True)
        try:
            self.assertEqual("locked", process.stdout.readline().strip())
            inode = (self.state / ".run.lock").stat().st_ino
        finally:
            process.kill(); process.communicate(timeout=3)
        self.assertEqual(0, self.call("retire")["result"])
        self.assertEqual(inode, (self.state / ".run.lock").stat().st_ino)
        self.expect_failure("begin", errno.ECANCELED)

    def test_ready_identity_corruption_cannot_be_used_to_release_or_retire(self):
        self.assertEqual(0, self.call("begin")["result"])
        self.assertEqual(0, self.call("ready")["result"])
        path = self.state / "managed.ready"; original = path.read_bytes()
        corruptions = [(0, b"x"), (4, b"x"), (40, b"x"), (104, bytes(4)),
            (108, bytes(8)), (116, bytes(4)), (116, (123).to_bytes(4, "little")), (120, bytes(8)), (128, b"Z")]
        for offset, value in corruptions:
            with self.subTest(offset=offset, value=value):
                changed = bytearray(original); changed[offset:offset + len(value)] = value; path.write_bytes(changed)
                self.expect_failure("status", errno.EPROTO); self.expect_failure("release", errno.EPROTO)
                self.expect_failure("retire", errno.EPROTO)
                self.assertEqual(changed, path.read_bytes())
        path.write_bytes(original); self.phase(3)

    def test_unsafe_lock_entries_cannot_split_or_redirect_lock_ownership(self):
        external = self.root / (self.state.name + "-lock-external")
        external.write_bytes(b"preserve"); external.chmod(0o600); self.addCleanup(external.unlink)
        lock = self.state / ".run.lock"
        lock.symlink_to(external); self.expect_failure("begin", errno.ELOOP); lock.unlink()
        os.link(external, lock); self.expect_failure("begin", errno.EPERM); lock.unlink()
        os.mkfifo(lock, 0o600); self.expect_failure("begin", errno.EPERM); lock.unlink()
        lock.write_bytes(b""); lock.chmod(0o644); self.expect_failure("begin", errno.EPERM)
        self.assertEqual(b"preserve", external.read_bytes())

    def test_partial_write_remains_unknown_and_never_restarts(self):
        self.expect_failure("begin", errno.EIO, fault="partial")
        path = self.state / "managed.intent"
        self.assertEqual(KEY_BYTES[:5], path.read_bytes())
        self.expect_failure("status", errno.EPROTO); self.expect_failure("begin", errno.EPROTO)
        self.assertEqual(KEY_BYTES[:5], path.read_bytes())

    def test_record_or_directory_fsync_failure_cannot_be_replayed_as_new_start(self):
        for fault in ["record", "publish"]:
            with self.subTest(fault=fault):
                self.expect_failure("begin", errno.EIO, fault=fault)
                self.phase(1); self.expect_failure("begin", errno.EEXIST)
                (self.state / "managed.intent").unlink()

    def test_existing_retirement_retry_must_fsync_again_before_success(self):
        self.assertEqual(0, self.call("retire")["result"])
        self.expect_failure("retire", errno.EIO, fault="record")
        self.phase(8)
        self.assertEqual(0, self.call("retire")["result"])
        self.expect_failure("begin", errno.ECANCELED)

    def test_malformed_identifiers_cannot_create_intents(self):
        for run, ticket in [(RUN.upper().replace("0", "A", 1), TICKET), ("../bad", TICKET),
            (RUN, "11" * 31), (RUN, "AA" * 32)]:
            with self.subTest(run=run, ticket=ticket):
                self.expect_failure("begin", errno.EINVAL, run=run, ticket=ticket)
        self.assertFalse((self.state / "managed.intent").exists())


if __name__ == "__main__":
    unittest.main()
