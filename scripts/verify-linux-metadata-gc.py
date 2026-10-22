#!/usr/bin/env python3
"""Root host GC tests; synthetic metadata is not an Android epoch/SQLite proof."""
import fcntl
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import unittest
import uuid

spec = importlib.util.spec_from_file_location('scopes', Path(__file__).with_name('verify-linux-scopes.py'))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)


class MetadataGcTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base.ScopeTest.setUpClass.__func__(cls)

    @classmethod
    def tearDownClass(cls):
        base.ScopeTest.tearDownClass.__func__(cls)

    def fixture(self):
        root = Path(self.directory.name) / str(uuid.uuid4())
        root.mkdir(mode=0o700)
        runtime, trash = root / 'runtime', root / 'trash'
        runtime.mkdir(mode=0o700); trash.mkdir(mode=0o700)
        state = runtime / str(uuid.uuid4())
        state.mkdir(mode=0o700)
        for name, contents in [('ticket.json', b'{}'), ('stopped', b''), ('run.json', b'{}'), ('.run.lock', b''), ('.recovery.lock', b'')]:
            (state / name).write_bytes(contents)
            (state / name).chmod(0o600)
        return state, trash

    def collect(self, state, trash=None):
        args = ['--gc-trash' if trash is None else '--gc', '--state-dir', str(state)]
        if trash is not None:
            args += ['--trash-dir', str(trash)]
        return subprocess.run([self.binary] + args, capture_output=True, timeout=8)

    def test_completed_flat_metadata_is_removed_but_parent_lock_is_retained(self):
        state, trash = self.fixture()
        result = self.collect(state, trash)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(json.loads(result.stdout)['collected'])
        self.assertFalse(state.exists())
        self.assertEqual(['.run.lock'], [p.name for p in trash.iterdir()])

    def test_current_native_run_lock_prevents_any_unlink(self):
        state, trash = self.fixture()
        with (state / '.run.lock').open('r+') as held:
            fcntl.flock(held, fcntl.LOCK_EX)
            self.assertNotEqual(0, self.collect(state, trash).returncode)
        self.assertTrue((state / 'ticket.json').exists())
        self.assertTrue((state / 'stopped').exists())

    def test_parent_lock_prevents_collector_split_during_final_unlink(self):
        state, trash = self.fixture()
        lock = trash / '.run.lock'
        lock.touch(mode=0o600)
        with lock.open('r+') as held:
            fcntl.flock(held, fcntl.LOCK_EX)
            self.assertNotEqual(0, self.collect(state, trash).returncode)
        self.assertTrue(state.exists())

    def test_unknown_entries_links_hardlinks_and_subdirectories_are_retained(self):
        for kind in ['unknown', 'symlink', 'hardlink', 'directory']:
            state, trash = self.fixture()
            outside = state.parent / f'outside-{uuid.uuid4()}'
            outside.write_bytes(b'keep')
            if kind == 'unknown': (state / 'unknown').touch(mode=0o600)
            elif kind == 'symlink': (state / 'completed.json').symlink_to(outside)
            elif kind == 'hardlink': os.link(outside, state / 'completed.json')
            else: (state / 'nested').mkdir()
            self.assertNotEqual(0, self.collect(state, trash).returncode, kind)
            self.assertEqual(b'keep', outside.read_bytes())
            self.assertTrue((state / 'ticket.json').exists())

    def test_no_stopped_marker_means_no_collection(self):
        state, trash = self.fixture()
        (state / 'stopped').unlink()
        self.assertNotEqual(0, self.collect(state, trash).returncode)
        self.assertTrue(state.exists())

    def test_legacy_read_only_world_bits_inside_private_root_directory_are_supported(self):
        state, trash = self.fixture()
        (state / 'ticket.json').chmod(0o644)
        self.assertEqual(0, self.collect(state, trash).returncode)

    def test_quarantined_partial_deletion_resumes_without_requiring_deleted_ticket(self):
        state, trash = self.fixture()
        moved = trash / state.name
        state.rename(moved)
        (moved / 'ticket.json').unlink(); (moved / 'stopped').unlink()
        self.assertEqual(0, self.collect(moved).returncode)
        self.assertFalse(moved.exists())

    def test_missing_or_non_uuid_directory_is_not_created(self):
        state, trash = self.fixture()
        missing = state.parent / str(uuid.uuid4())
        self.assertNotEqual(0, self.collect(missing, trash).returncode)
        self.assertFalse(missing.exists())
        self.assertNotEqual(0, self.collect(state.parent, trash).returncode)

    def test_empty_quarantine_and_writable_metadata(self):
        state, trash = self.fixture()
        moved = trash / state.name
        state.rename(moved)
        for path in moved.iterdir(): path.unlink()
        self.assertEqual(0, self.collect(moved).returncode)
        state, trash = self.fixture()
        (state / 'ticket.json').chmod(0o666)
        self.assertNotEqual(0, self.collect(state, trash).returncode)
        self.assertTrue((state / 'stopped').exists())

    def test_regular_file_bind_mount_is_retained_before_any_unlink(self):
        state, trash = self.fixture()
        outside = state.parent / 'mounted-source'
        outside.write_bytes(b'keep'); outside.chmod(0o600)
        program = '''import pathlib,subprocess,sys
binary,state,trash,source=sys.argv[1:]
subprocess.run(['mount','--bind',source,str(pathlib.Path(state)/'run.json')],check=True)
result=subprocess.run([binary,'--gc','--state-dir',state,'--trash-dir',trash],capture_output=True)
assert result.returncode != 0
assert (pathlib.Path(state)/'ticket.json').exists()
assert pathlib.Path(source).read_bytes() == b'keep'
'''
        result = subprocess.run(['unshare', '-m', '--propagation', 'private', 'python3', '-c', program,
                                 self.binary, str(state), str(trash), str(outside)], capture_output=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_mounted_quarantine_directory_is_not_traversed(self):
        state, trash = self.fixture()
        moved = trash / state.name
        state.rename(moved)
        program = '''import pathlib,subprocess,sys
binary,state=sys.argv[1:]
subprocess.run(['mount','--bind',state,state],check=True)
result=subprocess.run([binary,'--gc-trash','--state-dir',state],capture_output=True)
assert result.returncode != 0
assert (pathlib.Path(state)/'ticket.json').exists()
'''
        result = subprocess.run(['unshare', '-m', '--propagation', 'private', 'python3', '-c', program,
                                 self.binary, str(moved)], capture_output=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
