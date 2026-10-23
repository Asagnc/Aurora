"""Real Frida injection through the standalone guard, sharing the owned C target."""
import json
import os
from pathlib import Path
import signal
import struct
import subprocess
import unittest
import uuid


class GuardCases:
    def guarded(self, selected=b"send('guard-ready');", bridge=None, corrupt=None, duration=8000):
        from frida_channel_fixture import LaunchOptions
        from frida_guard_fixture import launch_guard
        options = LaunchOptions(bridge=bridge, corrupt=corrupt, duration=duration)
        state = self.root / ("guard-" + uuid.uuid4().hex)
        launched = launch_guard(self.worker, self.guard, (self.process.pid, self.ticks), selected,
            (state, options, self.worker_sha))
        self.addCleanup(self.stop_guarded, launched)
        return launched

    @staticmethod
    def stop_guarded(launched):
        if launched.child.poll() is None:
            launched.child.terminate()
        try:
            launched.child.communicate(timeout=25)
        except subprocess.TimeoutExpired:
            launched.child.kill(); launched.child.communicate(timeout=5)
        subprocess.run(launched.recovery(), capture_output=True, timeout=25)

    def guarded_finish(self, launched, prefix=()):
        output, error = launched.child.communicate(timeout=30)
        records = [json.loads(line) for line in [*prefix, *output.decode().splitlines()] if line]
        recovery = subprocess.run(launched.recovery(), capture_output=True, timeout=25, check=True)
        summary = json.loads(recovery.stdout)
        self.assertTrue(summary["resourcesClear"], error.decode(errors="replace"))
        self.assertFalse(list(launched.state.glob("*.state")))
        self.assertFalse((launched.state / "managed.scope-plan").exists())
        self.assertIsNone(self.process.poll(), "Guard cleanup killed the pre-existing target")
        frame = (launched.state / "managed.worker").read_bytes()
        self.assertEqual(52, len(frame)); self.assertEqual(launched.digest, frame[8:40])
        self.assertEqual(launched.worker, struct.unpack_from("<IQ", frame, 40))
        return records, summary

    def test_guarded_real_hook_has_durable_report_and_leaves_target_alive(self):
        launched = self.guarded(b"Interceptor.attach(Module.getGlobalExportByName('aurora_marker'), { onEnter() { send('guard-hook'); } });")
        records, summary = self.guarded_finish(launched)
        self.assertEqual(0, launched.child.returncode); self.assertTrue(summary["success"])
        self.assertEqual((127, 0), (summary["workerFlags"], summary["exitCode"]))
        self.assertTrue(any(record.get("kind") == "message" and "guard-hook" in record.get("message", "") for record in records))
        completion = next(record for record in records if record.get("kind") == "completion")
        self.assertTrue(completion["cleanupConfirmed"])

    def test_guarded_owner_death_preserves_real_unload_and_detach_report(self):
        launched = self.guarded(duration=30000)
        prefix = self.loaded(launched.child)
        launched.child.terminate()
        records, summary = self.guarded_finish(launched, prefix)
        self.assertEqual(-signal.SIGTERM, launched.child.returncode)
        self.assertFalse(summary["success"]); self.assertEqual((63, 1, 2), (summary["workerFlags"], summary["exitCode"], summary["reason"]))
        completion = next(record for record in records if record.get("kind") == "completion")
        self.assertTrue(completion["cleanupConfirmed"])

    def test_guarded_snapshot_preflight_never_reports_attach_or_success(self):
        launched = self.guarded(corrupt="sha")
        records, summary = self.guarded_finish(launched)
        self.assertEqual(1, launched.child.returncode); self.assertFalse(summary["success"])
        self.assertEqual((57, 64), (summary["workerFlags"], summary["exitCode"]))
        self.assertFalse(any(record.get("kind") == "loaded" for record in records))

    @unittest.skipUnless(os.environ.get("AURORA_FRIDA_JAVA_BUNDLE"), "Explicit development bridge required")
    def test_guarded_sealed_bridge_runs_selected_script_once(self):
        bridge = Path(os.environ["AURORA_FRIDA_JAVA_BUNDLE"]).read_bytes()
        launched = self.guarded(b"send('guard-bridge-script');", bridge=bridge)
        records, summary = self.guarded_finish(launched)
        self.assertTrue(summary["success"]); self.assertEqual(0, launched.child.returncode)
        messages = [json.loads(record["message"]) for record in records if record.get("kind") == "message"]
        self.assertEqual(1, sum(message.get("payload") == "guard-bridge-script" for message in messages))
