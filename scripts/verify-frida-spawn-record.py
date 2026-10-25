#!/usr/bin/env python3
"""Real process and filesystem checks for the spawn journal; no Frida download or device."""
import ctypes
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest


class Record(ctypes.Structure):
    _fields_ = [('ticket', ctypes.c_ubyte * 32), ('pid', ctypes.c_uint32), ('ticks', ctypes.c_uint64)]


class Journal(ctypes.Structure):
    _fields_ = [('directory', ctypes.c_int), ('lock', ctypes.c_int)]


class Key(ctypes.Structure):
    _fields_ = [('run', ctypes.c_char * 37), ('ticket', ctypes.c_char * 65)]


class Admission(ctypes.Structure):
    _fields_ = [('guard', ctypes.c_uint32), ('worker', ctypes.c_uint32), ('guardTicks', ctypes.c_uint64),
                ('workerTicks', ctypes.c_uint64), ('boot', ctypes.c_char * 37)]


@unittest.skipUnless(sys.platform.startswith('linux'), 'Linux pidfd host fixture')
class SpawnRecordTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='aurora-spawn-record-')
        cls.root = Path(cls.temporary.name)
        native = Path(__file__).resolve().parents[1] / 'app/src/main/cpp/frida'
        library = cls.root / 'record.so'
        subprocess.run(['cc', '-Wall', '-Wextra', '-Werror', '-fPIC', '-shared',
            str(native / 'spawn_record.c'), str(native / 'input.c'),
            str(native.parent / 'managed/spawn_recover.c'), str(native.parent / 'linux/scope_process.c'),
            '-o', str(library)], check=True)
        cls.lib = ctypes.CDLL(str(library), use_errno=True)
        cls.lib.aurora_spawn_store.argtypes = [ctypes.c_int, ctypes.c_uint, ctypes.POINTER(Record)]
        cls.lib.aurora_spawn_read.argtypes = [ctypes.c_int, ctypes.c_uint, ctypes.POINTER(Record)]
        cls.lib.aurora_spawn_terminate.argtypes = [ctypes.POINTER(Record), ctypes.c_int]
        cls.lib.managed_spawn_recover.argtypes = [ctypes.POINTER(Journal), ctypes.POINTER(Key), ctypes.POINTER(Admission), ctypes.c_uint]

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def setUp(self):
        self.work = tempfile.TemporaryDirectory(dir=self.root)
        self.addCleanup(self.work.cleanup)
        self.path = Path(self.work.name)
        self.fd = os.open(self.path, os.O_DIRECTORY | os.O_RDONLY)
        self.addCleanup(os.close, self.fd)

    def test_exclusive_durable_record_has_exact_canonical_bytes(self):
        value = Record(); value.ticket[:] = bytes(range(32))
        self.assertEqual(0, self.lib.aurora_spawn_store(self.fd, 1, ctypes.byref(value)))
        self.assertEqual(b'ASP2\x01\0\0\0' + bytes(range(32)) + bytes(24), (self.path / 'spawn.1').read_bytes())
        self.assertEqual(-1, self.lib.aurora_spawn_store(self.fd, 1, ctypes.byref(value)))
        captured = Record(); self.assertEqual(1, self.lib.aurora_spawn_read(self.fd, 1, ctypes.byref(captured)))
        self.assertEqual(bytes(value.ticket), bytes(captured.ticket))

    def test_partial_symlink_and_reserved_byte_changes_are_rejected(self):
        target = self.path / 'spawn.2'
        target.write_bytes(b'partial')
        value = Record()
        self.assertEqual(-1, self.lib.aurora_spawn_read(self.fd, 2, ctypes.byref(value)))
        target.unlink(); target.symlink_to(self.path / 'outside')
        self.assertEqual(-1, self.lib.aurora_spawn_read(self.fd, 2, ctypes.byref(value)))
        target.unlink()
        value.pid = 123; value.ticks = 456
        self.assertEqual(0, self.lib.aurora_spawn_store(self.fd, 2, ctypes.byref(value)))
        data = bytearray(target.read_bytes()); data[63] = 1; target.write_bytes(data)
        self.assertEqual(-1, self.lib.aurora_spawn_read(self.fd, 2, ctypes.byref(value)))

    def test_exact_pidfd_termination_and_replacement_identity_guard(self):
        child = subprocess.Popen(['sleep', '60'])
        self.addCleanup(lambda: child.kill() if child.poll() is None else None)
        try:
            text = Path(f'/proc/{child.pid}/stat').read_text()
            ticks = int(text[text.rfind(')') + 1:].split()[19])
            value = Record(); value.pid = child.pid; value.ticks = ticks + 1
            self.assertEqual(0, self.lib.aurora_spawn_terminate(ctypes.byref(value), 1000))
            self.assertIsNone(child.poll())
            value.ticks = ticks
            self.assertEqual(0, self.lib.aurora_spawn_terminate(ctypes.byref(value), 1000))
            self.assertEqual(-9, child.wait(timeout=3))
        finally:
            if child.poll() is None:
                child.kill(); child.wait(timeout=3)

    def recovery_fixture(self):
        child = subprocess.Popen(['sleep', '60'])
        def cleanup():
            if child.poll() is None:
                child.kill()
            child.wait(timeout=3)
        self.addCleanup(cleanup)
        text = Path(f'/proc/{child.pid}/stat').read_text()
        value = Record(); value.ticket[:] = bytes(range(32))
        self.assertEqual(0, self.lib.aurora_spawn_store(self.fd, 1, ctypes.byref(value)))
        value.pid = child.pid; value.ticks = int(text[text.rfind(')') + 1:].split()[19])
        self.assertEqual(0, self.lib.aurora_spawn_store(self.fd, 2, ctypes.byref(value)))
        return child, value

    def recover(self, boot=None):
        journal = Journal(self.fd, -1); key = Key(); key.ticket = bytes(range(32)).hex().encode()
        admission = Admission(); admission.boot = (boot or Path('/proc/sys/kernel/random/boot_id').read_text().strip()).encode()
        return self.lib.managed_spawn_recover(ctypes.byref(journal), ctypes.byref(key), ctypes.byref(admission), 15)

    def test_recovery_terminates_only_captured_target_before_resume_intent(self):
        child, _ = self.recovery_fixture()
        self.assertEqual(0, self.recover())
        self.assertEqual(-9, child.wait(timeout=3))
        self.assertTrue((self.path / 'spawn.4').is_file())
        self.assertEqual(0, self.recover())

    def test_resume_intent_and_old_boot_never_authorize_target_termination(self):
        child, value = self.recovery_fixture()
        self.assertEqual(0, self.recover('00000000-0000-0000-0000-000000000000'))
        self.assertIsNone(child.poll())
        self.assertEqual(0, self.lib.aurora_spawn_store(self.fd, 3, ctypes.byref(value)))
        self.assertEqual(0, self.recover())
        self.assertIsNone(child.poll())
        self.assertFalse((self.path / 'spawn.4').exists())


if __name__ == '__main__':
    unittest.main()
