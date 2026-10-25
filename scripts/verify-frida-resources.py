#!/usr/bin/env python3
"""Offline failure cases for ELF resource bounds; no Frida or device execution."""
import importlib.util
from pathlib import Path
import struct
import unittest

SPEC = importlib.util.spec_from_file_location('resources', Path(__file__).with_name('audit-frida-resources.py'))
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


def elf():
    data = bytearray(256)
    data[:7] = b'\x7fELF\x02\x01\x01'
    struct.pack_into('<HH', data, 16, 3, 183)
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 1)
    struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, 0, 0, 0, len(data), len(data), 16384)
    return data


class ResourcesTest(unittest.TestCase):
    def test_android_toolchain_note_does_not_inherit_outer_compiler_version(self):
        parsed = AUDIT.Elf(elf())
        note = struct.pack('<III', 8, 132, 1) + b'Android\0' + struct.pack('<I', 21) + b'r29'.ljust(64, b'\0') + b'14206865'.ljust(64, b'\0')
        parsed.data = bytes(256) + note
        parsed.headers = [(4, 256, 0, len(note), len(note), 0, 4)]
        self.assertEqual([{'api': 21, 'ndk': 'r29', 'build': '14206865'}], parsed.android_notes())
        parsed.data = parsed.data[:-1]
        with self.assertRaises(ValueError):
            parsed.android_notes()

    def test_file_bounds_and_ambiguous_or_non_file_backed_addresses(self):
        parsed = AUDIT.Elf(elf())
        self.assertTrue(parsed.describe()['load16KiB'])
        for address, size in [(-1, 1), (256, 1), (250, 7)]:
            with self.assertRaises(ValueError):
                parsed.offset(address, size)
        parsed.headers.append(parsed.headers[0])
        with self.assertRaises(ValueError):
            parsed.offset(1, 1)

    def test_truncated_or_invalid_program_headers_are_rejected(self):
        for offset, value in [(32, 4096), (64 + 32, 4096), (64 + 48, 3)]:
            data = elf()
            struct.pack_into('<Q', data, offset, value)
            with self.assertRaises(ValueError):
                AUDIT.Elf(data)
        with self.assertRaises(ValueError):
            AUDIT.Elf(elf()[:80])

    def test_relocatable_bpf_is_distinct_from_loadable_native_elf(self):
        data = elf()
        struct.pack_into('<HH', data, 16, 1, 247)
        struct.pack_into('<Q', data, 32, 0)
        struct.pack_into('<HH', data, 54, 0, 0)
        result = AUDIT.Elf(data).describe()
        self.assertEqual(247, result['machine'])
        self.assertFalse(result['load16KiB'])
        self.assertEqual([], result['loadAlignments'])


if __name__ == '__main__':
    unittest.main()
