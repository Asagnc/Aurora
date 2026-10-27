#!/usr/bin/env python3
"""Check kernel child-exit proofs when proc children enumeration is unavailable."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc"), "Linux C compiler required")
class ReapTests(unittest.TestCase):
    def test_missing_proc_children_does_not_hide_a_live_child(self):
        self.run_fixture("managed_reap_fixture.c", "managed_reap_no_proc_children_passed")

    def test_replaced_worker_pid_can_identify_a_live_thread(self):
        self.run_fixture("managed_replaced_worker_fixture.c", "managed_replaced_worker_passed")

    def run_fixture(self, source, expected):
        with tempfile.TemporaryDirectory(prefix="aurora-managed-reap-") as directory:
            executable = Path(directory) / "fixture"
            subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-Os", "-ffunction-sections",
                            "-fdata-sections", "-Wl,--gc-sections", "-pthread",
                            str(ROOT / "scripts" / source), "-o", str(executable)], check=True)
            result = subprocess.run([str(executable)], capture_output=True, text=True, timeout=10, check=True)
            self.assertEqual(expected, result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
