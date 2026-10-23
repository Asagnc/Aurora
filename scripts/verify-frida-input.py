#!/usr/bin/env python3
"""Actual POSIX input/identity checks; no Frida engine, injection or phone operations."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HARNESS = r'''
#include "input.h"
#include <stdlib.h>
#include <string.h>
int main(int argc, char **argv) {
    struct frida_attach_input input;
    if (argc == 4 && !strcmp(argv[1], "matches")) {
        input.pid = (pid_t) strtol(argv[2], NULL, 10);
        input.start_ticks = strtoull(argv[3], NULL, 10);
        return frida_target_matches(&input) ? 0 : 1;
    }
    if (frida_input_read(argc, argv, &input)) return 64;
    free(input.script); free(input.payload);
    return 0;
}
'''


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc"), "POSIX C compiler required")
class InputTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-frida-input-")
        cls.root = Path(cls.temporary.name)
        source = cls.root / "harness.c"
        source.write_text(HARNESS, encoding="utf-8")
        cls.binary = cls.root / "harness"
        native = ROOT / "app/src/main/cpp/frida"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-I" + str(native), str(source),
                        str(native / "input.c"), "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def invoke(self, arguments):
        return subprocess.run([str(self.binary), *map(str, arguments)], timeout=3).returncode

    def test_numeric_and_argument_contract_is_strict(self):
        source = self.root / "valid.js"
        source.write_text("send('owned');", encoding="utf-8")
        valid = ["attach", "123", "234", source, "1000"]
        self.assertEqual(0, self.invoke(valid))
        self.assertEqual(0, self.invoke(valid + [source]))
        self.assertEqual(64, self.invoke(valid + [source, source]))
        for index, values in [(1, ["0", "-1", "+1", "2147483648", "1x"]),
                              (2, ["0", "18446744073709551616", "1.5"]),
                              (4, ["0", "270001", "1.5", "1 "])]:
            for value in values:
                with self.subTest(index=index, value=value):
                    changed = valid.copy()
                    changed[index] = value
                    self.assertEqual(64, self.invoke(changed))
        self.assertEqual(64, self.invoke(valid + ["ignored"]))

    def test_scripts_are_bounded_regular_files_without_final_links_or_nul(self):
        source = self.root / "source.js"
        source.write_text("send(1);", encoding="utf-8")
        link = self.root / "link.js"
        link.symlink_to(source)
        fifo = self.root / "fifo"
        os.mkfifo(fifo)
        empty = self.root / "empty.js"
        empty.touch()
        nul = self.root / "nul.js"
        nul.write_bytes(b"send(1);\0send(2)")
        large = self.root / "large.js"
        with large.open("wb") as stream:
            stream.truncate(1048577)
        for path in [link, fifo, empty, nul, large, self.root, Path("relative.js")]:
            with self.subTest(path=path):
                self.assertEqual(64, self.invoke(["attach", "123", "234", path, "1000"]))

    def test_live_process_start_ticks_must_match(self):
        stat = Path("/proc/self/stat").read_text()
        ticks = int(stat[stat.rfind(")") + 1:].split()[19])
        self.assertEqual(0, self.invoke(["matches", os.getpid(), ticks]))
        self.assertEqual(1, self.invoke(["matches", os.getpid(), ticks + 1]))
        self.assertEqual(1, self.invoke(["matches", 2147483647, ticks]))


if __name__ == "__main__":
    unittest.main()
