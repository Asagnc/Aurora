#!/usr/bin/env python3
"""Reject truncated/ambiguous Debian data and linked or duplicate source notices."""
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('gcc_materials', Path(__file__).with_name('prepare-proxy-gcc-materials.py'))
source = importlib.util.module_from_spec(spec); spec.loader.exec_module(source)


def ar_member(name, data):
    header = f'{name + "/":<16}{0:<12}{0:<6}{0:<6}{"100644":<8}{len(data):<10}`\n'.encode()
    return header + data + (b'\n' if len(data) % 2 else b'')


class GccMaterialTest(unittest.TestCase):
    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(); self.addCleanup(self.stage.cleanup)
        self.path = Path(self.stage.name) / 'owned.deb'

    def test_valid_deb_and_truncated_or_duplicate_payload(self):
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode='w:xz') as archive:
            item = tarfile.TarInfo('owned'); item.size = 4; archive.addfile(item, io.BytesIO(b'test'))
        payload = ar_member('data.tar.xz', buffer.getvalue()); valid = b'!<arch>\n' + payload
        self.path.write_bytes(valid)
        with source.deb_data(self.path) as archive:
            self.assertEqual(source.member(archive, 'owned', 4), b'test')
        for invalid in [valid[:-1], valid + payload, valid[:20], b'!<arch>\n' + ar_member('control.tar.xz', b'test')]:
            self.path.write_bytes(invalid)
            with self.assertRaises(ValueError):
                source.deb_data(self.path)

    def test_source_member_rejects_links_duplicates_and_budget(self):
        for mode in ['link', 'duplicate', 'budget']:
            buffer = io.BytesIO()
            with tarfile.open(fileobj=buffer, mode='w') as archive:
                item = tarfile.TarInfo('COPYING'); item.size = 4
                if mode == 'link':
                    item.type = tarfile.SYMTYPE; item.linkname = 'elsewhere'; archive.addfile(item)
                else:
                    archive.addfile(item, io.BytesIO(b'test'))
                    if mode == 'duplicate':
                        archive.addfile(item, io.BytesIO(b'test'))
            buffer.seek(0)
            with tarfile.open(fileobj=buffer) as archive, self.assertRaises(ValueError):
                source.member(archive, 'COPYING', 3 if mode == 'budget' else 4)

    def test_notice_content_address_never_overwrites_another_payload(self):
        root = Path(self.stage.name)
        record = source.store(root, 'COPYING', b'test')
        (root / record['stored']).write_bytes(b'changed')
        with self.assertRaises(ValueError):
            source.store(root, 'COPYING', b'test')


if __name__ == '__main__':
    unittest.main()
