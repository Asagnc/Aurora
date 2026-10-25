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


@unittest.skipUnless(sys.platform.startswith('linux'), 'Linux pidfd host fixture')
class SpawnRecordTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix='aurora-spawn-record-')
        cls.root = Path(cls.temporary.name)
        native = Path(__file__).resolve().parents[1] / 'app/src/main/cpp/frida'
        library = cls.root / 'record.so'
        subprocess.run(['cc', '-Wall', '-Wextra', '-Werror', '-fPIC', '-shared',
            str(native / 'spawn_record.c'), str(native / 'input.c'), '-o', str(library)], check=True)
        cls.lib = ctypes.CDLL(str(library), use_errno=True)
        cls.lib.aurora_spawn_store.argtypes = [ctypes.c_int, ctypes.c_uint, ctypes.POINTER(Record)]
        cls.lib.aurora_spawn_read.argtypes = [ctypes.c_int, ctypes.c_uint, ctypes.POINTER(Record)]
        cls.lib.aurora_spawn_terminate.argtypes = [ctypes.POINTER(Record), ctypes.c_int]

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


if __name__ == '__main__':
    unittest.main()
