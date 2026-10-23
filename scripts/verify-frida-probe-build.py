#!/usr/bin/env python3
"""Offline archive rejection checks for the Frida build adapter."""
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("frida_build", Path(__file__).with_name("build-frida-probe.py"))
BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILD)


class ArchiveRejection(unittest.TestCase):
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
