#!/usr/bin/env python3
"""Embedded archive corruption must fail without importing frozen Python modules."""
import importlib.util
import marshal
from pathlib import Path
import struct
import sys
import unittest
import zlib

spec = importlib.util.spec_from_file_location('bundle', Path(__file__).with_name('audit-python-bundle.py'))
bundle = importlib.util.module_from_spec(spec); spec.loader.exec_module(bundle)


def fixture(name='owned', raw=b'payload', declared=None, position=0):
    packed = zlib.compress(raw); text = name.encode() + b'\0'
    table = struct.pack('!IIIIBc', 18 + len(text), position, len(packed), len(raw) if declared is None else declared, 1, b'x') + text
    cookie = struct.pack('!8sIIII64s', bundle.MAGIC, len(packed) + len(table) + 88,
                         len(packed), len(table), 314, b'libpython3.14.so.1.0')
    return b'owned-prefix' + packed + table + cookie


class BundleTest(unittest.TestCase):
    @unittest.skipIf(sys.version_info < (3, 13), 'code-disabled marshal requires Python 3.13+')
    def test_python_inventory_disallows_code_and_invalid_locations(self):
        def pyz(table):
            return b'PYZ\0MAGC' + struct.pack('!I', 16) + b'data' + marshal.dumps(table)
        self.assertEqual(['owned.module'], bundle.python_modules(pyz([('owned.module', (0, 12, 4))])))
        for table in [[('owned', (0, 12, 5))], [('owned', (0, 12, 4))] * 2,
                      [('owned', compile('raise RuntimeError()', '<owned>', 'exec'))], [('../escape', (0, 12, 4))]]:
            with self.assertRaises(ValueError):
                bundle.python_modules(pyz(table))

    def test_checked_table_and_exact_decompression(self):
        data = fixture()
        metadata, entries = bundle.archive(data)
        self.assertEqual(314, metadata['pythonVersion'])
        self.assertEqual(b'payload', bundle.content(data, entries[0]))

    def test_unsafe_offsets_paths_and_expansion_are_rejected(self):
        for data in [fixture(name='../outside'), fixture(position=1_000_000), fixture()[:-20]]:
            with self.assertRaises((ValueError, struct.error)):
                bundle.archive(data)
        data = fixture(raw=b'x' * 100000, declared=1)
        _, entries = bundle.archive(data)
        with self.assertRaises(ValueError):
            bundle.content(data, entries[0])

    def test_elf_architecture_and_program_header_bounds_are_checked(self):
        data = bytearray(120); data[:7] = b'\x7fELF\x02\x01\x01'
        struct.pack_into('<HH', data, 16, 2, 183); struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, 0, 0, 0, 120, 120, 65536)
        self.assertTrue(bundle.elf(data)['page16kCompatible'])
        struct.pack_into('<H', data, 18, 62)
        with self.assertRaises(ValueError):
            bundle.elf(data)


if __name__ == '__main__':
    unittest.main()
