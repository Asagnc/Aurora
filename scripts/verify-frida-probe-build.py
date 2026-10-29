#!/usr/bin/env python3
"""Offline archive rejection checks for the Frida build adapter."""
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("frida_build", Path(__file__).with_name("build-frida-probe.py"))
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class ArchiveRejection(unittest.TestCase):
    def test_policy_headers_match_the_already_pinned_sdk_source(self):
        policy = BUILD.policy_inputs()
        catalog = json.loads((BUILD.ROOT / 'tools/frida-sources/catalog.json').read_text(encoding='utf-8'))
        pin = next(entry for entry in catalog['repositories'] if entry['id'] == 'selinux')
        self.assertEqual(policy.DIGEST, pin['archiveSha256'])
        self.assertEqual(policy.SIZE, pin['archiveBytes'])
        self.assertEqual(policy.NAME, 'selinux-' + pin['commit'] + '.tar.gz')

    def test_policy_header_links_and_windows_drive_paths_are_rejected(self):
        policy = BUILD.policy_inputs()
        for path, link in [('repo/libsepol/include/sepol/link.h', '../escape'),
                           ('repo/libsepol/include/C:escape.h', None),
                           ('repo/libsepol/include/../../escape', None)]:
            with tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive = root / 'input.tar.gz'
                with tarfile.open(archive, 'w:gz') as target:
                    entry = tarfile.TarInfo(path)
                    if link is None:
                        entry.size = 1; target.addfile(entry, io.BytesIO(b'x'))
                    else:
                        entry.type = tarfile.SYMTYPE; entry.linkname = link; target.addfile(entry)
                with self.assertRaises(ValueError):
                    policy.headers(archive, root / 'output')
                self.assertFalse((root / 'escape').exists())

    def test_fixed_header_inventory_omits_the_cil_directory_link(self):
        policy = BUILD.policy_inputs()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); archive = root / 'input.tar.gz'
            with tarfile.open(archive, 'w:gz') as target:
                link = tarfile.TarInfo('repo/libsepol/include/sepol/cil')
                link.type = tarfile.SYMTYPE; link.linkname = '../../cil/include/cil'; target.addfile(link)
                for index in range(49):
                    entry = tarfile.TarInfo('repo/libsepol/include/sepol/header%d.h' % index)
                    entry.size = 1; target.addfile(entry, io.BytesIO(b'x'))
            self.assertEqual(49, policy.headers(archive, root / 'output'))
            self.assertFalse((root / 'output/policy-include/sepol/cil').exists())

    def reject(self, entries):
        with tempfile.TemporaryDirectory(prefix="aurora-frida-archive-test-") as temporary:
            root = Path(temporary)
            archive = root / "input.tar.xz"
            with tarfile.open(archive, "w:xz") as target:
                for name, link in entries:
                    entry = tarfile.TarInfo(name)
                    if name == ".":
                        entry.type = tarfile.DIRTYPE
                        target.addfile(entry)
                    elif link is not None:
                        entry.type = tarfile.SYMTYPE
                        entry.linkname = link
                        target.addfile(entry)
                    else:
                        entry.size = 1
                        target.addfile(entry, io.BytesIO(b"x"))
            output = root / "output"
            output.mkdir()
            with self.assertRaises(ValueError):
                BUILD.extract(archive, output)
            self.assertFalse((root / "escape").exists())

    def test_unknown_paths_and_duplicate_inventory_are_rejected(self):
        self.reject([("../escape", None)])
        names = [".", "./libfrida-core.a", "./frida-core.h", "./frida-core.gir", "./frida-core-example.c"]
        self.reject([(name, None) for name in names + ["./frida-core.h"]])

    def test_expected_name_cannot_be_a_link(self):
        self.reject([(".", None), ("./libfrida-core.a", "../escape"), ("./frida-core.h", None),
                     ("./frida-core.gir", None), ("./frida-core-example.c", None)])


if __name__ == "__main__":
    unittest.main()
