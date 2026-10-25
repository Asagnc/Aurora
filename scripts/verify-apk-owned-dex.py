#!/usr/bin/env python3
"""Regression cases distinguish defined fixture classes from references and cover multidex."""
import hashlib
import io
import struct
import unittest
import zipfile
import zlib
from apk_owned_dex import defined_classes, verify

OWNED = b'Lio/github/asagnc/aurora/buildfixture/Owned;'
SCREEN = b'Lio/github/asagnc/aurora/buildfixture/OwnedScreenKt;'


def seal(data):
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into('<I', data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return bytes(data)


def fixture(strings, definitions):
    # Metadata-only format fixture for the bounded verifier; not an executable Android program.
    data = bytearray(112 + len(strings) * 8 + len(definitions) * 32)
    data[:8] = b'dex\n035\0'; string_offset = 112; type_offset = 112 + len(strings) * 4; classes = 112 + len(strings) * 8
    for index, value in enumerate(strings):
        struct.pack_into('<I', data, string_offset + index * 4, len(data))
        struct.pack_into('<I', data, type_offset + index * 4, index)
        assert len(value) < 128
        data.extend(bytes([len(value)]) + value + b'\0')
    for index, value in enumerate(definitions):
        struct.pack_into('<I', data, classes + index * 32, value)
    for offset, value in [(32, len(data)), (36, 112), (40, 0x12345678), (56, len(strings)), (60, string_offset),
                          (64, len(strings)), (68, type_offset), (96, len(definitions)), (100, classes)]:
        struct.pack_into('<I', data, offset, value)
    return seal(data)


class OwnedDexTest(unittest.TestCase):
    def archive(self, files):
        output = io.BytesIO()
        with zipfile.ZipFile(output, 'w') as archive:
            for name, data in files:
                archive.writestr(name, data)
        return zipfile.ZipFile(io.BytesIO(output.getvalue()))

    def test_string_reference_cannot_substitute_for_class_definition(self):
        data = fixture([OWNED, b'LOther;'], [1])
        self.assertIn(OWNED, data); self.assertNotIn(OWNED, defined_classes(data))
        with self.archive([('classes.dex', data)]) as archive, self.assertRaisesRegex(ValueError, 'defined owned'):
            verify(archive, False)

    def test_actual_definition_in_secondary_dex_is_found(self):
        with self.archive([('classes.dex', fixture([SCREEN], [0])), ('classes2.dex', fixture([OWNED], [0]))]) as archive:
            locations = verify(archive, True)
        self.assertEqual(locations[OWNED.decode()], 'classes2.dex')
        self.assertEqual(locations[SCREEN.decode()], 'classes.dex')

    def test_corrupt_offsets_fail_even_with_recomputed_checksum(self):
        data = bytearray(fixture([OWNED], [0])); offset = struct.unpack_from('<I', data, 100)[0]
        struct.pack_into('<I', data, offset, 0xfffffffe)
        with self.assertRaisesRegex(ValueError, 'type index'):
            defined_classes(seal(data))

    def test_duplicate_definitions_across_dex_are_rejected(self):
        data = fixture([OWNED], [0])
        with self.archive([('classes.dex', data), ('classes2.dex', data)]) as archive, self.assertRaisesRegex(ValueError, 'Duplicate'):
            verify(archive, False)


if __name__ == '__main__':
    unittest.main()
