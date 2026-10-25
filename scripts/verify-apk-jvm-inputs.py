#!/usr/bin/env python3
"""Bounded qualification archive rejection paths; no downloads or external JVM execution."""
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('qualification', Path(__file__).with_name('qualify-apk-jvm.py'))
qualification = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualification)


class JvmInputsTest(unittest.TestCase):
    def test_noncanonical_cross_root_and_windows_paths_are_rejected(self):
        for name in ['../jdk/bin/java', '/jdk/bin/java', 'jdk/../other', 'jdk/./bin/java', 'jdk//bin/java', 'jdk\\bin\\java', 'other/java']:
            with self.assertRaises(ValueError):
                qualification.member_name(name, 'jdk')

    def test_jdk_link_cannot_escape_temporary_qualification_directory(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); archive = root / 'jdk.tar.gz'; work = root / 'work'; work.mkdir()
            with tarfile.open(archive, 'w:gz') as output:
                link = tarfile.TarInfo('jdk/link'); link.type = tarfile.SYMTYPE; link.linkname = '../../outside'
                output.addfile(link)
                content = tarfile.TarInfo('jdk/link/file'); content.size = 1
                output.addfile(content, io.BytesIO(b'x'))
            with self.assertRaises(tarfile.FilterError):
                qualification.unpack({'id': 'jdk', 'prefix': 'jdk'}, archive, work)
            self.assertFalse((root / 'outside').exists())

    def test_gradle_symlink_archive_is_rejected_without_following_it(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); archive = root / 'gradle.zip'; work = root / 'work'; work.mkdir()
            with zipfile.ZipFile(archive, 'w') as output:
                link = zipfile.ZipInfo('gradle/bin'); link.external_attr = 0o120777 << 16
                output.writestr(link, '../../outside')
            with self.assertRaises(ValueError):
                qualification.unpack({'id': 'gradle', 'prefix': 'gradle'}, archive, work)
            self.assertFalse((root / 'outside').exists())


if __name__ == '__main__':
    unittest.main()
