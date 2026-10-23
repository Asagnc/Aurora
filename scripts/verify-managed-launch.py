#!/usr/bin/env python3
"""Actual Root cgroup admission/GO/bootstrap/cleanup with owned fixture workers.
No Frida injection; final loaded/unloaded bits are simulated by the fixture.
"""
import errno
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc") and
    getattr(os, "geteuid", lambda: -1)() == 0, "Explicit Linux Root/cgroup fixture required")
class LaunchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-managed-launch-")
        cls.root = Path(cls.temporary.name)
        cls.binary = cls.root / "fixture"
        native = ROOT / "app/src/main/cpp"
        sources = [ROOT / "scripts/managed_launch_fixture.c"]
        sources += [native / "managed" / name for name in ["journal.c", "journal_io.c", "journal_identity.c", "launch.c", "launch_child.c"]]
        sources += [native / "linux" / name for name in ["task_scope.c", "scope_identity.c", "scope_state.c", "scope_process.c"]]
        sources += [native / "frida" / name for name in ["input.c", "managed_control.c", "managed_protocol.c", "managed_receive.c", "managed_observe.c"]]
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-Os", "-I" + str(native / "managed"),
            *map(str, sources), "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def run_mode(self, mode):
        with tempfile.TemporaryDirectory(dir=self.root) as temporary:
            state = Path(temporary)
            process = subprocess.run([str(self.binary), mode, str(state), str(state / "executed")],
                capture_output=True, text=True, timeout=12, check=True)
            value = json.loads(process.stdout)
            self.assertEqual(0, value["cleaned"], value)
            self.assertEqual(0, value["retired"], value)
            self.assertEqual(0, value["before"], "Worker executed before durable release")
            self.assertFalse(list(state.glob("*.state")), "Recorded task domain not retired after actual removal")
            self.assertTrue((state / "managed.retired").is_file())
            return value

    def test_admitted_child_executes_only_after_durable_identity_and_go(self):
        result = self.run_mode("normal")
        self.assertEqual((0, 3, 0), (result["prepared"], result["phase"], result["released"]))
        self.assertEqual((1, 127, 0, 1), (result["frame"], result["flags"], result["workerStatus"], result["after"]))
        self.assertEqual(0, result["observed"])

    def test_retirement_before_launch_prevents_fork_and_exec(self):
        result = self.run_mode("retired-before")
        self.assertEqual(-1, result["prepared"]); self.assertEqual(errno.ECANCELED, result["prepareError"])
        self.assertEqual(0, result["after"]); self.assertEqual(-1, result["workerStatus"])

    def test_pending_owner_cancellation_prevents_fork_and_exec(self):
        result = self.run_mode("cancel-before")
        self.assertEqual(-1, result["prepared"]); self.assertEqual(errno.ECANCELED, result["prepareError"])
        self.assertEqual((0, -1), (result["after"], result["workerStatus"]))

    def test_cancellation_after_admission_prevents_go_and_script_execution(self):
        result = self.run_mode("cancel-ready")
        self.assertEqual(0, result["prepared"]); self.assertEqual(-1, result["released"])
        self.assertEqual(errno.ECANCELED, result["releaseError"])
        self.assertEqual((0, 57, 125), (result["after"], result["flags"], result["workerStatus"]))

    def test_retirement_after_admission_cannot_release_child(self):
        result = self.run_mode("retired-ready")
        self.assertEqual(0, result["prepared"]); self.assertEqual(-1, result["released"])
        self.assertEqual(errno.ECANCELED, result["releaseError"]); self.assertEqual(0, result["after"])
        self.assertEqual(57, result["flags"]); self.assertEqual(125, result["workerStatus"])

    def test_owner_loss_before_or_after_admission_cannot_execute_worker(self):
        for mode in ["owner-before", "owner-after"]:
            with self.subTest(mode=mode):
                result = self.run_mode(mode)
                self.assertEqual(0, result["after"])
                key = "prepareError" if mode == "owner-before" else "releaseError"
                self.assertEqual(errno.ECANCELED, result[key])

    def test_failed_cgroup_admission_never_publishes_ready_or_executes(self):
        result = self.run_mode("admission-fail")
        self.assertEqual(-1, result["prepared"]); self.assertEqual(1, result["phase"])
        self.assertEqual(0, result["after"]); self.assertEqual(125, result["workerStatus"])

    def test_worker_death_before_go_cannot_be_mistaken_for_execution(self):
        result = self.run_mode("worker-before-go")
        self.assertEqual(0, result["prepared"]); self.assertEqual(-1, result["released"])
        self.assertEqual(errno.ECANCELED, result["releaseError"]); self.assertEqual(0, result["after"])
        self.assertEqual(137, result["workerStatus"]); self.assertEqual(0, result["flags"])

    def test_exec_failure_reports_no_session_from_native_launcher(self):
        result = self.run_mode("exec-fail")
        self.assertEqual((0, 0), (result["prepared"], result["released"]))
        self.assertEqual((1, 57, 125, 0), (result["frame"], result["flags"], result["workerStatus"], result["after"]))

    def test_go_cannot_be_replayed_and_inherited_fds_do_not_reach_helpers(self):
        result = self.run_mode("duplicate-go")
        self.assertEqual((0, -1), (result["released"], result["second"]))
        self.assertEqual(0, result["workerStatus"], "Private FD leaked across worker or helper exec")
        self.assertEqual(1, result["after"])

    def test_malformed_worker_arguments_cannot_fork_or_execute(self):
        result = self.run_mode("bad-argv")
        self.assertEqual(-1, result["prepared"]); self.assertEqual(errno.EINVAL, result["prepareError"])
        self.assertEqual(0, result["after"]); self.assertEqual(-1, result["workerStatus"])

    def test_auto_reaping_parent_is_rejected_before_pid_ownership_can_be_lost(self):
        result = self.run_mode("auto-reap")
        self.assertEqual(-1, result["prepared"]); self.assertEqual(errno.EINVAL, result["prepareError"])
        self.assertEqual(0, result["after"]); self.assertEqual(-1, result["workerStatus"])


if __name__ == "__main__":
    unittest.main()
