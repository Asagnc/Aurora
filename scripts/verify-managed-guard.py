#!/usr/bin/env python3
"""Standalone coordinator/guard recovery on real Root cgroups; simulated worker facts."""
import ctypes
import json
import os
import re
from pathlib import Path
import shutil
import signal
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from managed_native_sources import guard_sources

ROOT = Path(__file__).resolve().parents[1]
RUN = "00000000-0000-0000-0000-000000000002"
TICKET = "11" * 32


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc") and
    getattr(os, "geteuid", lambda: -1)() == 0, "Explicit Linux Root/cgroup fixture required")
class GuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0):
            raise OSError(ctypes.get_errno(), "subreaper")
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-managed-guard-")
        cls.root = Path(cls.temporary.name); cls.guard = cls.root / "guard"; cls.worker = cls.root / "worker"
        native = ROOT / "app/src/main/cpp"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-Os", *map(str, guard_sources(ROOT)), "-o", str(cls.guard)], check=True)
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-Os", "-I" + str(native / "frida"),
            str(ROOT / "scripts/managed_guard_worker_fixture.c"), str(native / "frida/input.c"),
            str(native / "frida/managed_control.c"), "-o", str(cls.worker)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def setUp(self):
        self.space = tempfile.TemporaryDirectory(dir=self.root)
        self.addCleanup(self.space.cleanup)
        self.state = Path(self.space.name) / "state"; self.marker = Path(self.space.name) / "executed"

    def command(self, action="run", mode="normal", observe=1000, worker=None, cooperative=False, capture=False):
        result = [str(self.guard), action, "--state-dir", str(self.state), "--run-id", RUN, "--ticket-sha", TICKET]
        if action == "run":
            result += ["--observe-ms", str(observe)]
            if capture:
                result += ["--kind", "capture"]
            if cooperative or capture:
                result.append("--cooperative-cancel")
            result += ["--", str(worker or self.worker), "capture-channel" if capture else "attach-channel", mode, "22" * 32, "{}"]
            if capture:
                result.append("fixture-root-ticks")
        return result

    def start(self, mode="normal", **options):
        environment = {**os.environ, "AURORA_FIXTURE_MARKER": str(self.marker)}
        if mode == "escaped":
            environment["AURORA_FIXTURE_ESCAPE"] = str(self.parent_cgroup() / "cgroup.procs")
        process = subprocess.Popen(self.command(mode=mode, **options), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, env=environment)
        self.addCleanup(self.stop, process)
        return process

    @staticmethod
    def parent_cgroup():
        own = next(line[3:] for line in Path("/proc/self/cgroup").read_text().splitlines() if line.startswith("0::"))
        for line in Path("/proc/self/mountinfo").read_text().splitlines():
            if " - cgroup2 " not in line:
                continue
            fields = line.split()
            root, mount = [re.sub(r"\\([0-7]{3})", lambda value: chr(int(value[1], 8)), item) for item in fields[3:5]]
            if root == "/" or own == root or own.startswith(root + "/"):
                return Path(mount) / (own if root == "/" else own[len(root):]).lstrip("/")
        raise AssertionError("No matching current cgroup mount")

    def stop(self, process):
        if process.poll() is None:
            process.terminate()
        try:
            process.communicate(timeout=25)
        except subprocess.TimeoutExpired:
            process.kill(); process.communicate(timeout=5)
        if self.state.exists():
            subprocess.run(self.command("recover"), capture_output=True, text=True, timeout=25)
        while True:
            try:
                if os.waitpid(-1, os.WNOHANG)[0] == 0:
                    break
            except ChildProcessError:
                break

    def finish(self, process, timeout=25):
        output, error = process.communicate(timeout=timeout)
        if not output.strip() and process.returncode == -signal.SIGTERM:
            recovered = subprocess.run(self.command("recover"), capture_output=True, text=True, timeout=5, check=True)
            output = recovered.stdout
        self.assertTrue(output.strip(), error)
        result = json.loads(output.splitlines()[-1])
        self.assertTrue(result["resourcesClear"], (result, error))
        self.assertFalse(list(self.state.glob("*.state")))
        self.assertFalse((self.state / "managed.scope-plan").exists())
        self.assertEqual(123, (self.state / "managed.stopped").stat().st_size)
        return result

    def wait_started(self, process):
        deadline = time.monotonic() + 5
        while not self.marker.exists() and time.monotonic() < deadline:
            if process.poll() is not None:
                self.fail(process.communicate())
            time.sleep(0.01) # Host fixture readiness only; native supervision is event-driven.
        self.assertTrue(self.marker.exists())

    def test_normal_task_has_durable_worker_and_resource_proofs(self):
        process = self.start(); result = self.finish(process)
        self.assertEqual(0, process.returncode); self.assertTrue(result["success"])
        self.assertEqual((127, 0), (result["workerFlags"], result["exitCode"]))
        self.assertEqual(52, (self.state / "managed.worker").stat().st_size)

    def test_version_probe_does_not_create_task_state_or_claim_frida_ready(self):
        result = subprocess.run([str(self.guard), "--version"], capture_output=True, text=True, timeout=5, check=True)
        self.assertEqual({"protocol": 1, "kind": "aurora-managed-guard", "rootRequired": True, "cooperativeCancel": True}, json.loads(result.stdout))
        self.assertFalse(self.state.exists())

    def test_duration_uses_cooperative_finish_before_forced_cleanup(self):
        process = self.start("wait", observe=500); result = self.finish(process)
        self.assertEqual(0, process.returncode); self.assertTrue(result["success"])
        self.assertEqual((1, 0), (result["reason"], result["exitCode"]))

    def test_coordinator_death_allows_guard_to_cancel_and_finish(self):
        process = self.start("wait", observe=10000); self.wait_started(process)
        process.terminate(); result = self.finish(process)
        self.assertEqual(-signal.SIGTERM, process.returncode)
        self.assertFalse(result["success"]); self.assertEqual((63, 1, 2), (result["workerFlags"], result["exitCode"], result["reason"]))

    def test_cooperative_term_waits_for_guard_report_and_resource_cleanup(self):
        process = self.start("wait", observe=60000, cooperative=True); self.wait_started(process)
        process.terminate(); result = self.finish(process)
        self.assertEqual(1, process.returncode)
        self.assertFalse(result["success"]); self.assertEqual((63, 1, 2), (result["workerFlags"], result["exitCode"], result["reason"]))

    def test_cooperative_mode_does_not_cancel_normal_completion(self):
        process = self.start(cooperative=True); result = self.finish(process)
        self.assertEqual(0, process.returncode); self.assertTrue(result["success"])

    def test_cooperative_parent_death_still_finishes_without_a_live_java_owner(self):
        wrapper = "import subprocess,sys,time; p=subprocess.Popen(sys.argv[1:]); print(p.pid,flush=True); time.sleep(60)"
        process = subprocess.Popen([sys.executable, "-c", wrapper, *self.command(mode="wait", observe=60000, cooperative=True)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
            env={**os.environ, "AURORA_FIXTURE_MARKER": str(self.marker)})
        self.addCleanup(self.stop, process)
        self.wait_started(process); process.kill(); result = self.finish(process)
        self.assertEqual(-signal.SIGKILL, process.returncode)
        self.assertFalse(result["success"]); self.assertEqual((63, 1, 2), (result["workerFlags"], result["exitCode"], result["reason"]))

    def test_cooperative_cancel_forces_frozen_guard_before_long_observation_deadline(self):
        process = self.start("wait", observe=60000, cooperative=True); self.wait_started(process)
        ready = (self.state / "managed.ready").read_bytes(); guard = struct.unpack_from("<I", ready, 104)[0]
        os.kill(guard, signal.SIGSTOP); process.terminate()
        result = self.finish(process, timeout=28)
        self.assertEqual(137, process.returncode)
        self.assertFalse(result["success"]); self.assertFalse(result["workerReport"])

    def test_guard_death_recovers_domain_without_inventing_unload_report(self):
        process = self.start("wait", observe=10000); self.wait_started(process)
        ready = (self.state / "managed.ready").read_bytes(); guard = struct.unpack_from("<I", ready, 104)[0]
        os.kill(guard, signal.SIGKILL); result = self.finish(process)
        self.assertEqual(137, process.returncode); self.assertFalse(result["success"])
        self.assertFalse(result["workerReport"]); self.assertEqual((-1, 4), (result["exitCode"], result["reason"]))

    def test_both_ancestors_dead_recover_resources_without_replaying_worker(self):
        process = self.start("wait", observe=10000); self.wait_started(process)
        ready = (self.state / "managed.ready").read_bytes(); guard = struct.unpack_from("<I", ready, 104)[0]
        original = self.marker.read_bytes()
        os.kill(guard, signal.SIGKILL); process.kill(); process.communicate(timeout=5)
        recovered = subprocess.run(self.command("recover"), capture_output=True, text=True, timeout=10, check=True)
        value = json.loads(recovered.stdout)
        self.assertTrue(value["resourcesClear"]); self.assertFalse(value["workerReport"])
        self.assertFalse(value["success"]); self.assertEqual(original, self.marker.read_bytes())
        self.assertFalse(list(self.state.glob("*.state")))

    def test_frozen_guard_cannot_keep_coordinator_waiting_indefinitely(self):
        process = self.start("wait", observe=500); self.wait_started(process)
        ready = (self.state / "managed.ready").read_bytes(); guard = struct.unpack_from("<I", ready, 104)[0]
        os.kill(guard, signal.SIGSTOP)
        process.communicate(timeout=25)
        self.assertNotEqual(0, process.returncode)
        recovered = subprocess.run(self.command("recover"), capture_output=True, text=True, timeout=10, check=True)
        value = json.loads(recovered.stdout)
        self.assertTrue(value["resourcesClear"]); self.assertFalse(value["workerReport"])
        self.assertFalse(value["success"])

    def test_detached_orphan_helper_is_reaped_before_completion(self):
        process = self.start("orphan"); result = self.finish(process)
        self.assertTrue(result["success"])
        child = int(Path(str(self.marker) + ".helper").read_text())
        self.assertFalse(Path(f"/proc/{child}").exists())

    def test_own_escaped_helper_is_reaped_without_touching_unrelated_process(self):
        external = subprocess.Popen(["sleep", "30"])
        try:
            process = self.start("escaped"); result = self.finish(process)
            self.assertTrue(result["success"])
            child = int(Path(str(self.marker) + ".helper").read_text())
            self.assertFalse(Path(f"/proc/{child}").exists())
            self.assertIsNone(external.poll())
        finally:
            external.terminate(); external.wait(timeout=3)

    def test_exec_failure_is_distinct_from_loaded_success(self):
        process = self.start(worker=Path("/aurora-fixture-not-installed/worker")); result = self.finish(process)
        self.assertEqual(1, process.returncode); self.assertFalse(result["success"])
        self.assertEqual((57, 125), (result["workerFlags"], result["exitCode"]))

    def test_invalid_report_is_not_replaced_with_stdout_or_cleanup_success(self):
        process = self.start("partial"); result = self.finish(process)
        self.assertEqual(1, process.returncode); self.assertFalse(result["workerReport"])
        self.assertFalse(result["success"])

    def test_recovery_is_idempotent_and_run_never_replays(self):
        process = self.start(); original = self.finish(process)
        stopped = (self.state / "managed.stopped").read_bytes(); marker = self.marker.read_bytes()
        for _ in range(2):
            result = subprocess.run(self.command("recover"), capture_output=True, text=True, timeout=5, check=True)
            self.assertEqual(original, json.loads(result.stdout))
        result = subprocess.run(self.command(), capture_output=True, text=True, timeout=5)
        self.assertEqual(126, result.returncode)
        self.assertEqual(marker, self.marker.read_bytes()); self.assertEqual(stopped, (self.state / "managed.stopped").read_bytes())

    def test_retire_before_start_blocks_delayed_dispatch(self):
        result = subprocess.run(self.command("recover"), capture_output=True, text=True, timeout=5, check=True)
        value = json.loads(result.stdout)
        self.assertEqual((2, 2, False), (value["workerState"], value["domainState"], value["workerReport"]))
        result = subprocess.run(self.command(), capture_output=True, text=True, timeout=5)
        self.assertEqual(126, result.returncode); self.assertFalse(self.marker.exists())

    def test_capture_kind_uses_distinct_report_and_recovers_exact_record(self):
        process = self.start(capture=True); result = self.finish(process)
        self.assertTrue(result["success"]); self.assertEqual(result["workerFlags"], 193)
        self.assertEqual((self.state / "managed.capture").stat().st_size, 108)
        recovered = subprocess.run(self.command("recover"), capture_output=True, text=True, timeout=25)
        self.assertEqual(recovered.returncode, 0, recovered.stderr)
        self.assertTrue(json.loads(recovered.stdout)["success"])

    def test_capture_rejects_frida_report(self):
        process = self.start(capture=True, mode="wrong-frida"); result = self.finish(process)
        self.assertFalse(result["success"]); self.assertFalse(result["workerReport"])
        self.assertTrue(result["resourcesClear"])

    def test_frida_rejects_capture_report(self):
        process = self.start(mode="wrong-capture"); result = self.finish(process)
        self.assertFalse(result["success"]); self.assertFalse(result["workerReport"])
        self.assertTrue(result["resourcesClear"])

    def test_frida_cannot_gain_long_capture_budget(self):
        result = subprocess.run(self.command(observe=250001), capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 64); self.assertFalse(self.marker.exists())

    def test_long_capture_budget_does_not_change_frida_limits(self):
        process = self.start(capture=True, observe=1800000); result = self.finish(process)
        self.assertTrue(result["success"])

    def test_valid_report_while_worker_alive_cannot_claim_success(self):
        process = self.start("frame-alive", observe=500); result = self.finish(process)
        self.assertEqual(1, process.returncode); self.assertFalse(result["success"])
        self.assertTrue(result["workerReport"]); self.assertEqual((127, 137), (result["workerFlags"], result["exitCode"]))

    def test_unresponsive_worker_forces_cleanup_but_never_invents_report(self):
        process = self.start("ignore", observe=500); result = self.finish(process)
        self.assertEqual(1, process.returncode); self.assertFalse(result["success"])
        self.assertFalse(result["workerReport"]); self.assertEqual(137, result["exitCode"])


if __name__ == "__main__":
    unittest.main()
