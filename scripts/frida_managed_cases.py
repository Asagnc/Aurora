"""Inherited-control lifecycle cases share the actual Frida/owned-target fixture."""
import json
import ctypes
import os
from pathlib import Path
import socket
import struct


class ManagedCases:
    def managed(self, corrupt=None, duration=30000):
        from frida_channel_fixture import launch, LaunchOptions
        parent, worker = socket.socketpair()
        parent.settimeout(5)
        try:
            launched = launch(self.worker, (self.process.pid, self.ticks), b"send('managed-ready');",
                LaunchOptions(corrupt=corrupt, supervision=worker, duration=duration))
            self.addCleanup(parent.close)
            self.addCleanup(self.stop, launched.child)
            return launched, parent
        except BaseException:
            parent.close()
            raise
        finally:
            worker.close()

    def facts(self, launched, parent):
        data = bytearray()
        while len(data) < 52:
            chunk = parent.recv(52 - len(data))
            if not chunk:
                break
            data.extend(chunk)
        self.assertEqual(52, len(data), data)
        self.assertEqual(b"AEX1", data[:4]); self.assertEqual(1, data[4]); self.assertEqual(0, data[7])
        self.assertEqual(launched.digest, data[8:40])
        self.assertEqual((launched.child.pid, launched.ticks), struct.unpack_from("<IQ", data, 40))
        self.assertEqual(b"", parent.recv(1))
        return data[5], data[6]

    def test_managed_completion_reports_exact_cleanup_outside_stdout(self):
        launched, parent = self.managed(duration=2000)
        self.finish(launched.child)
        self.assertEqual((127, 0), self.facts(launched, parent))

    def test_managed_cooperative_cancel_cleans_existing_target(self):
        launched, parent = self.managed()
        prefix = self.loaded(launched.child)
        parent.sendall(b"\x02")
        records = self.finish(launched.child, prefix)
        self.assertEqual("cancelled", records[-1]["reason"])
        self.assertEqual((63, 1), self.facts(launched, parent))

    def test_managed_cooperative_deadline_is_distinct_from_cancel(self):
        launched, parent = self.managed()
        prefix = self.loaded(launched.child)
        parent.sendall(b"\x01")
        records = self.finish(launched.child, prefix)
        self.assertEqual("duration_elapsed", records[-1]["reason"])
        self.assertEqual((127, 0), self.facts(launched, parent))

    def test_managed_parent_channel_loss_cleans_but_cannot_publish_proof(self):
        launched, parent = self.managed()
        prefix = self.loaded(launched.child)
        parent.close()
        records = self.finish(launched.child, prefix)
        self.assertEqual("supervisor_gone", records[-1]["reason"])
        self.assertEqual(74, launched.child.returncode)
        self.assertFalse(records[-1]["success"])

    def test_managed_invalid_control_cleans_without_success(self):
        launched, parent = self.managed()
        prefix = self.loaded(launched.child)
        parent.sendall(b"\x7f")
        records = self.finish(launched.child, prefix)
        self.assertEqual("supervisor_protocol", records[-1]["reason"])
        self.assertEqual((63, 1), self.facts(launched, parent))

    def test_managed_preflight_rejection_reports_never_attempted_attach(self):
        launched, parent = self.managed(corrupt="sha")
        output, _ = launched.child.communicate(timeout=10)
        self.assertEqual(b"", output)
        self.assertEqual(64, launched.child.returncode)
        self.assertEqual((57, 64), self.facts(launched, parent))
        self.assertIsNone(self.process.poll())

    def test_managed_abrupt_worker_death_has_no_cleanup_proof(self):
        launched, parent = self.managed()
        self.loaded(launched.child)
        launched.child.kill(); launched.child.communicate(timeout=10)
        self.assertEqual(b"", parent.recv(52))
        self.assertIsNone(self.process.poll())

    def test_managed_attach_failure_without_session_keeps_detach_unconfirmed(self):
        libc = ctypes.CDLL(None, use_errno=True)
        libc.ptrace.argtypes = [ctypes.c_uint, ctypes.c_uint, ctypes.c_void_p, ctypes.c_void_p]
        libc.ptrace.restype = ctypes.c_long
        self.assertEqual(0, libc.ptrace(0x4206, self.process.pid, None, None), ctypes.get_errno())
        try:
            launched, parent = self.managed()
            output, error = launched.child.communicate(timeout=20)
            records = [json.loads(line) for line in output.decode().splitlines() if line]
            self.assertTrue(records, error.decode(errors="replace"))
            self.assertEqual("completion", records[-1]["kind"])
            self.assertTrue(records[-1]["attachAttempted"])
            self.assertFalse(records[-1]["detached"])
            self.assertFalse(records[-1]["cleanupConfirmed"])
            self.assertEqual((43, 1), self.facts(launched, parent))
            state = Path(f"/proc/{self.process.pid}/stat").read_text().rsplit(")", 1)[1].split()[0]
            self.assertNotEqual("Z", state, "Failed attach killed the existing owned target")
        finally:
            # Only this test's newly launched target is traced; never touch another process.
            self.process.kill(); self.process.communicate(timeout=5)
