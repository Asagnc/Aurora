#!/usr/bin/env python3
"""Root host fault injection; does not qualify Android or business chroot leases."""
import importlib.util
import json
import os
from pathlib import Path
import signal
import struct
import subprocess
import unittest

spec = importlib.util.spec_from_file_location('scope_tests', Path(__file__).with_name('verify-linux-scopes.py'))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)


def sign_record(data):
    value = 14695981039346656037
    for byte in data[:-8]:
        value = ((value ^ byte) * 1099511628211) & ((1 << 64) - 1)
    struct.pack_into('<Q', data, len(data) - 8, value)
    return data


class RecoveryTest(base.ScopeTest):
    def recover(self, state=None):
        return subprocess.run([self.binary, '--recover', '--state-dir', str(state or self.state)],
                              capture_output=True, timeout=10)

    def restore_and_recover(self, path, contents):
        path.write_bytes(contents)
        path.chmod(0o600)
        result = self.recover(path.parent)
        self.assertEqual(0, result.returncode, result.stderr.decode())

    def orphan(self):
        process, detached, scope = self.start()
        guards = base.children(process.pid)
        self.assertEqual(1, len(guards))
        os.kill(guards[0], signal.SIGSTOP)
        os.kill(process.pid, signal.SIGKILL)
        os.kill(guards[0], signal.SIGKILL)
        process.communicate(timeout=8)
        record, = self.state.glob('*.state')
        contents = record.read_bytes()
        self.addCleanup(self.restore_and_recover, record, contents)
        return detached, scope, record, contents

    def test_guard_sigkill_is_recovered_by_coordinator_without_success_reply(self):
        process, detached, scope = self.start()
        os.kill(base.children(process.pid)[0], signal.SIGKILL)
        out, err = process.communicate(timeout=8)
        self.assertNotEqual(0, process.returncode, err.decode())
        self.assertEqual(b'', out)
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assertEqual([], list(self.state.glob('*.state')))

    def test_both_supervisors_killed_are_recovered_by_a_fresh_process(self):
        detached, scope, _, _ = self.orphan()
        self.assertTrue(Path(f'/proc/{detached}').exists())
        result = self.recover()
        self.assertEqual(0, result.returncode, result.stderr.decode())
        self.assertTrue(json.loads(result.stdout)['recovered'])
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assertEqual([], list(self.state.glob('*.state')))

    def test_live_guard_is_not_replayed_or_killed(self):
        process, detached, scope = self.start()
        result = self.recover()
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(Path(f'/proc/{detached}').exists())
        self.assertEqual(1, len(list(self.state.glob('*.state'))))
        process.kill()
        process.communicate(timeout=8)
        self.assert_gone(detached)
        self.assert_scope_removed(scope)

    def test_corrupt_record_is_retained_without_touching_scope(self):
        detached, _, record, original = self.orphan()
        data = bytearray(original)
        data[48] ^= 1
        record.write_bytes(data)
        result = self.recover()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(bytes(data), record.read_bytes())
        self.assertTrue(Path(f'/proc/{detached}').exists())

    def test_valid_checksum_with_different_scope_inode_still_refuses_kill(self):
        detached, _, record, original = self.orphan()
        data = bytearray(original)
        inode, = struct.unpack_from('<Q', data, 48)
        struct.pack_into('<Q', data, 48, inode + 1)
        record.write_bytes(sign_record(data))
        result = self.recover()
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(record.exists())
        self.assertTrue(Path(f'/proc/{detached}').exists())

    def test_previous_boot_record_is_retired_without_killing_present_scope(self):
        detached, _, record, original = self.orphan()
        data = bytearray(original)
        data[56:92] = b'00000000-0000-0000-0000-000000000000'
        record.write_bytes(sign_record(data))
        result = self.recover()
        self.assertEqual(0, result.returncode, result.stderr.decode())
        self.assertFalse(record.exists())
        self.assertTrue(Path(f'/proc/{detached}').exists())

    def test_untrusted_state_directory_and_symlink_are_refused(self):
        directory = Path(self.directory.name) / 'untrusted-state'
        directory.mkdir(mode=0o755)
        self.assertNotEqual(0, self.recover(directory).returncode)
        directory.chmod(0o700)
        os.chown(directory, 65534, 65534)
        self.assertNotEqual(0, self.recover(directory).returncode)
        link = Path(self.directory.name) / 'state-link'
        link.symlink_to(directory)
        self.assertNotEqual(0, self.recover(link).returncode)


if __name__ == '__main__':
    # Base lifecycle tests run separately; run only this class's new faults.
    suite = unittest.TestSuite(RecoveryTest(name) for name in RecoveryTest.__dict__ if name.startswith('test_'))
    outcome = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(not outcome.wasSuccessful())
