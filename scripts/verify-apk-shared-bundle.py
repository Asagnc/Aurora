#!/usr/bin/env python3
"""Portable native bundle boundaries: exclude SDK distributions, retain notices, reject foreign ELF and escaping links."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('apk_bundle', Path(__file__).with_name('apk_shared_bundle.py'))
bundle = importlib.util.module_from_spec(spec); spec.loader.exec_module(bundle)


class SharedBundleTest(unittest.TestCase):
    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name); self.sdk = self.root / 'sdk'
        native = self.sdk / 'build-tools/36.0.0'; native.mkdir(parents=True)
        for name in ['aapt2', 'aidl', 'zipalign']:
            (native / name).write_bytes(b'owned native fixture')
        for name in ['ndk/30.0.16248370', 'cmake/4.1.2', 'platforms/android-37.0']:
            (self.sdk / name).mkdir(parents=True)
        (self.sdk / 'platforms/android-37.0/android.jar').write_bytes(b'official SDK distribution must stay upstream')
        self.runtime = self.root / 'runtime'; self.runtime.mkdir()

    def export(self, name):
        return bundle.export_native(self.sdk, self.runtime, self.root / name, {'fixture': 'owned'}, [])

    def test_fixed_archive_excludes_official_sdk_and_preserves_existing_output(self):
        first = self.export('first.tar.gz'); second = self.export('second.tar.gz')
        self.assertEqual(first['sha256'], second['sha256'])
        with tarfile.open(self.root / first['archive']) as archive:
            names = archive.getnames()
        self.assertIn('android-host/resources/aapt2', names)
        self.assertNotIn('android-host/platforms/android-37.0/android.jar', names)
        with self.assertRaises(FileExistsError):
            self.export('first.tar.gz')
        self.assertEqual(bundle.digest(self.root / first['archive']), first['sha256'])

    @unittest.skipIf(os.name == 'nt', 'Actual Unix symlink fixture executes in ARM64 qualification CI')
    def test_escape_rejection_cleans_only_its_stage_and_preserves_source_target(self):
        outside = self.root / 'outside'; outside.write_bytes(b'preserve')
        (self.sdk / 'ndk/30.0.16248370/escape').symlink_to(outside)
        with self.assertRaisesRegex(ValueError, 'escapes'):
            self.export('bad.tar.gz')
        self.assertEqual(outside.read_bytes(), b'preserve')
        self.assertFalse((self.root / '.android-host-stage').exists()); self.assertFalse((self.root / 'bad.tar.gz').exists())

    def test_foreign_host_elf_is_rejected_before_dependency_inspection(self):
        data = bytearray(20); data[:6] = b'\x7fELF\x02\x01'; data[18:20] = (62).to_bytes(2, 'little')
        (self.sdk / 'build-tools/36.0.0/aapt2').write_bytes(data)
        with self.assertRaisesRegex(ValueError, 'Foreign host ELF'):
            list(bundle.host_executables(self.sdk))

    def test_source_notice_requires_its_actual_fixed_archive_bytes(self):
        source = self.root / 'sources'; source.mkdir(); (source / 'sources').mkdir()
        data = b'owned source copyright fixture'; archive = source / 'sources/owned.tar.gz'
        with tarfile.open(archive, 'w:gz') as output:
            item = tarfile.TarInfo('LICENSE'); item.size = len(data); output.addfile(item, io.BytesIO(data))
        entry = {'id': 'owned', 'repository': 'https://android.googlesource.com/owned', 'commit': 'a' * 40,
            'archive': archive.name, 'archiveBytes': archive.stat().st_size, 'archiveSha256': bundle.digest(archive),
            'notices': [{'path': 'LICENSE', 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}]}
        (source / 'materials.json').write_text(json.dumps([entry]))
        first = self.root / 'first'; first.mkdir(); bundle.source_notices([source], first)
        self.assertEqual(len(list((first / 'source-notices').iterdir())), 1)
        archive.write_bytes(b'changed source archive')
        second = self.root / 'second'; second.mkdir()
        with self.assertRaisesRegex(ValueError, 'Source material changed'):
            bundle.source_notices([source], second)


if __name__ == '__main__':
    unittest.main()
