#!/usr/bin/env python3
"""Filesystem/provenance boundaries of the new public-cache qualification format."""
import hashlib
import os
from pathlib import Path
import tempfile
import unittest
import sys
import time
import apk_dependency_cache as cache


class CacheBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / 'modules-2'; self.source.mkdir()
        data = b'owned inert public artifact fixture'
        sha1 = hashlib.sha1(data).hexdigest()
        self.file = self.source / 'files-2.1/example.public/owned/1.0' / sha1 / 'owned-1.0.jar'
        self.file.parent.mkdir(parents=True); self.file.write_bytes(data)
        self.log = 'Downloading https://repo.maven.apache.org/maven2/example/public/owned/1.0/owned-1.0.jar to /owned/tmp\n'

    def snapshot(self, name='snapshot'):
        target = self.root / name
        return target, cache.snapshot(self.source, target, {'fixture': True}, self.log)

    def test_snapshot_excludes_locks_and_detects_later_tampering(self):
        (self.source / 'modules-2.lock').write_bytes(b'private lock')
        (self.source / 'gc.properties').write_bytes(b'access state')
        target, manifest = self.snapshot()
        self.assertEqual(1, manifest['artifactFiles'])
        self.assertFalse((target / 'modules-2/modules-2.lock').exists())
        self.assertFalse((target / 'modules-2/gc.properties').exists())
        file = next((target / 'modules-2').rglob('*.jar')); file.write_bytes(b'tampered')
        with self.assertRaisesRegex(ValueError, 'mismatch'):
            cache.verify(target)

    def test_wrong_content_address_or_missing_public_origin_is_rejected(self):
        original = self.file.parent
        renamed = original.with_name('0' * 40)
        original.rename(renamed)
        with self.assertRaisesRegex(ValueError, 'content address'):
            self.snapshot()
        renamed.rename(original)
        self.log = 'Downloading https://private.invalid/example/public/owned/1.0/owned-1.0.jar'
        with self.assertRaisesRegex(ValueError, 'public seed'):
            self.snapshot('other')

    def test_hardlink_source_is_not_adopted(self):
        os.link(self.file, self.root / 'outside')
        with self.assertRaisesRegex(ValueError, 'single-link'):
            self.snapshot()
        self.assertEqual(b'owned inert public artifact fixture', (self.root / 'outside').read_bytes())

    def test_gradle_non_padded_sha1_address_is_verified_against_complete_content(self):
        data = next(b'owned leading-zero fixture ' + str(index).encode() for index in range(10000)
                    if hashlib.sha1(b'owned leading-zero fixture ' + str(index).encode()).hexdigest().startswith('0'))
        self.file.unlink(); self.file.parent.rmdir()
        address = hashlib.sha1(data).hexdigest().lstrip('0')
        self.file = self.file.parent.with_name(address) / self.file.name
        self.file.parent.mkdir(); self.file.write_bytes(data)
        _, manifest = self.snapshot()
        self.assertEqual(hashlib.sha256(data).hexdigest(), next(iter(manifest['files'].values()))['sha256'])

    @unittest.skipIf(os.name == 'nt', 'Actual symlink qualification runs on Unix CI')
    def test_source_and_snapshot_links_are_rejected_without_touching_targets(self):
        (self.source / 'link').symlink_to(self.root / 'outside')
        with self.assertRaisesRegex(ValueError, 'links'):
            self.snapshot()
        (self.source / 'link').unlink()
        target, _ = self.snapshot('valid')
        outside = self.root / 'outside'; outside.write_bytes(b'preserve')
        (target / 'modules-2/link').symlink_to(outside)
        with self.assertRaisesRegex(ValueError, 'inode'):
            cache.verify(target)
        self.assertEqual(b'preserve', outside.read_bytes())

    def test_archive_publication_never_overwrites_existing_bytes(self):
        target, _ = self.snapshot()
        archive = self.root / 'cache.tar.gz'
        first = cache.export(target, archive)
        self.assertEqual(cache.digest(archive), first['sha256'])
        before = archive.read_bytes()
        with self.assertRaises(FileExistsError):
            cache.export(target, archive)
        self.assertEqual(before, archive.read_bytes())

    @unittest.skipIf(os.name == 'nt', 'Actual pidfd/subreaper boundary executes on Linux CI')
    def test_seed_exit_stops_adopted_writer_before_cache_snapshot(self):
        import apk_dependency_process as owned
        owned.subreaper()
        marker = self.root / 'writer'
        writer = f"import time\nfrom pathlib import Path\np=Path({str(marker)!r})\nwhile True:\n p.write_text(str(time.monotonic()))\n time.sleep(.01)\n"
        launcher = (f"import subprocess,sys,time\nfrom pathlib import Path\n"
                    f"subprocess.Popen([sys.executable,'-c',{writer!r}])\n"
                    f"p=Path({str(marker)!r})\n"
                    "deadline=time.monotonic()+2\nwhile not p.exists() and time.monotonic()<deadline:time.sleep(.01)\n"
                    "assert p.exists()\n")
        owned.execute([sys.executable, '-c', launcher], self.root / 'process.log', 5)
        before = marker.read_bytes(); time.sleep(.1)
        self.assertEqual(before, marker.read_bytes())
        self.assertEqual([], owned.children())


if __name__ == '__main__':
    unittest.main()
