#!/usr/bin/env python3
"""NDK CMake data extraction must never admit foreign executable or unsafe archive paths."""
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile
from apk_native_host import prepare_ndk


class NativeLayoutTest(unittest.TestCase):
    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name)
        self.candidate = self.root / 'candidate'; (self.candidate / 'bin').mkdir(parents=True)
        (self.candidate / 'bin/clang').write_bytes(b'OWNED VERIFIED COMPILER FIXTURE')
        self.pin = {'targetTriples': ['aarch64-linux-android', 'x86_64-linux-android'], 'androidApis': [36, 37], 'resourceVersion': '21'}

    def archive(self, entries):
        target = self.root / 'ndk.zip'
        with zipfile.ZipFile(target, 'w') as archive:
            archive.writestr('android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/jni.h', '// owned')
            for name, data in entries:
                archive.writestr(name, data)
        return target

    def test_cmake_data_is_preserved_and_foreign_host_bin_not_extracted(self):
        archive = self.archive([('android-ndk-r30/build/cmake/android.toolchain.cmake', 'set(OWNED 1)'),
            ('android-ndk-r30/meta/platforms.json', '{}'), ('android-ndk-r30/source.properties', 'Pkg.Revision=30'),
            ('android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/bin/foreign', b'\x7fELFFOREIGN')])
        ndk, toolchain, _ = prepare_ndk(archive, self.pin, self.candidate, self.root / 'work')
        self.assertEqual((ndk / 'build/cmake/android.toolchain.cmake').read_text(), 'set(OWNED 1)')
        self.assertEqual((toolchain / 'bin/clang').read_bytes(), b'OWNED VERIFIED COMPILER FIXTURE')
        self.assertFalse((toolchain / 'bin/foreign').exists())
        self.assertIn('llvm-ar', (toolchain / 'bin/llvm-ranlib').read_text())

    def test_metadata_traversal_is_rejected(self):
        archive = self.archive([('android-ndk-r30/build/cmake/../../../escape', 'bad')])
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            prepare_ndk(archive, self.pin, self.candidate, self.root / 'work')
        self.assertFalse((self.root / 'escape').exists())

    def test_elf_in_metadata_is_rejected(self):
        archive = self.archive([('android-ndk-r30/build/cmake/foreign.cmake', b'\x7fELFbad')])
        with self.assertRaisesRegex(ValueError, 'Foreign executable'):
            prepare_ndk(archive, self.pin, self.candidate, self.root / 'work')

    def test_metadata_symlink_is_rejected(self):
        entry = zipfile.ZipInfo('android-ndk-r30/build/cmake/link'); entry.create_system = 3
        entry.external_attr = 0o120777 << 16
        archive = self.archive([(entry, '../../outside')])
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            prepare_ndk(archive, self.pin, self.candidate, self.root / 'work')

    def test_duplicate_metadata_is_rejected(self):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            archive = self.archive([('android-ndk-r30/build/cmake/data', 'one'), ('android-ndk-r30/build/cmake/data', 'two')])
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            prepare_ndk(archive, self.pin, self.candidate, self.root / 'work')


if __name__ == '__main__':
    unittest.main()
