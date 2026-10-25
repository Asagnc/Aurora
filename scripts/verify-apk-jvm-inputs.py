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
resource_spec = importlib.util.spec_from_file_location('resources', Path(__file__).with_name('qualify-apk-resources.py'))
resources = importlib.util.module_from_spec(resource_spec)
resource_spec.loader.exec_module(resources)


class JvmInputsTest(unittest.TestCase):
    def test_resource_header_links_materialize_only_within_the_pinned_archive(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); archive = root / 'source.tar.gz'; work = root / 'work'; work.mkdir()
            with tarfile.open(archive, 'w:gz') as output:
                data = tarfile.TarInfo('binder/include/utils/TypeHelpers.h'); data.size = 5
                output.addfile(data, io.BytesIO(b'owned'))
                link = tarfile.TarInfo('include/utils/TypeHelpers.h'); link.type = tarfile.SYMTYPE
                link.linkname = '../../binder/include/utils/TypeHelpers.h'; output.addfile(link)
                external = tarfile.TarInfo('.clang-format'); external.type = tarfile.SYMTYPE
                external.linkname = '../../outside'; output.addfile(external)
            report = resources.unpack({'destination': 'vendor/core', 'repository': 'https://android.googlesource.com/platform/system/core',
                                       'id': 'fixture'}, archive, work)
            header = work / 'vendor/core/include/utils/TypeHelpers.h'
            self.assertEqual(b'owned', header.read_bytes())
            self.assertFalse(header.is_symlink())
            self.assertEqual(['.clang-format'], report['linksNotMaterialized'])
            self.assertFalse((root / 'outside').exists())
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
