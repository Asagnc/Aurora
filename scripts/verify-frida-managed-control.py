#!/usr/bin/env python3
"""Linux control descriptor ownership and exact report parsing, without Frida injection."""
import json
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HARNESS = r'''
#define _GNU_SOURCE
#include "managed_control.h"
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
int main(int argc, char **argv) {
    if (argc != 2) return 64;
    if (!strcmp(argv[1], "decode")) {
        unsigned char frame[AURORA_MANAGED_FACT_BYTES], ticket[32]; memset(ticket, 17, 32);
        struct aurora_managed_worker worker = {.pid = 123, .ticks = 456};
        struct aurora_managed_receipt receipt = {0};
        int valid = fread(frame, 1, sizeof(frame), stdin) == sizeof(frame) &&
            !aurora_managed_decode(frame, ticket, &worker, &receipt);
        printf("%d\n", valid); return 0;
    }
    struct aurora_managed_control control;
    int opened = aurora_managed_open(&control), cloexec = control.fd >= 0 && (fcntl(control.fd, F_GETFD) & FD_CLOEXEC);
    int cleared = !getenv("AURORA_MANAGED_FD") && !getenv("AURORA_MANAGED_TICKET");
    struct aurora_managed_facts facts = {.flags = AURORA_FACT_NO_SESSION};
    int result = opened ? -1 : aurora_managed_report(&control, &facts, 64);
    aurora_managed_close(&control);
    printf("{\"opened\":%d,\"cloexec\":%d,\"cleared\":%d,\"reported\":%d}\n", opened, !!cloexec, cleared, result);
    return 0;
}
'''


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc"), "Linux C compiler required")
class ControlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-managed-control-")
        cls.root = Path(cls.temporary.name)
        source = cls.root / "harness.c"; source.write_text(HARNESS, encoding="utf-8")
        cls.binary = cls.root / "harness"
        native = ROOT / "app/src/main/cpp/frida"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-I" + str(native), str(source),
            *[str(native / name) for name in ["input.c", "managed_control.c", "managed_protocol.c"]], "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def decode(self, frame):
        result = subprocess.run([str(self.binary), "decode"], input=frame, capture_output=True, timeout=3, check=True)
        return result.stdout.strip() == b"1"

    def frame(self, flags=57, status=64):
        return b"AEX1" + bytes([1, flags, status, 0]) + bytes([17]) * 32 + struct.pack("<IQ", 123, 456)

    def test_exact_clean_and_preflight_facts_are_distinct(self):
        self.assertTrue(self.decode(self.frame()))
        self.assertTrue(self.decode(self.frame(127, 0)))
        self.assertTrue(self.decode(self.frame(63, 1)))
        self.assertTrue(self.decode(self.frame(7, 1)), "Partial cleanup may be recorded but must not be upgraded to success")

    def test_wrong_ticket_process_start_protocol_or_reserved_bits_cannot_pass(self):
        frame = self.frame()
        for index in [0, 4, 7, 8, 39, 40, 44, 51]:
            with self.subTest(index=index):
                changed = bytearray(frame); changed[index] ^= 1
                self.assertFalse(self.decode(changed))
        self.assertFalse(self.decode(frame[:-1]))

    def test_impossible_success_or_injection_flags_are_rejected(self):
        for flags, status in [(0, 64), (128, 64), (1 | 4, 1), (127, 1), (63, 0), (1 | 2 | 4 | 64, 0)]:
            with self.subTest(flags=flags, status=status):
                self.assertFalse(self.decode(self.frame(flags, status)))

    def invoke(self, environment, descriptors=()):
        result = subprocess.run([str(self.binary), "report"], env={**os.environ, **environment},
            pass_fds=descriptors, capture_output=True, text=True, timeout=3, check=True)
        return json.loads(result.stdout)

    @unittest.skipUnless(getattr(os, "geteuid", lambda: -1)() == 0, "Explicit Root parent required")
    def test_inherited_stream_restores_cloexec_clears_environment_and_reports_identity(self):
        parent, worker = socket.socketpair()
        try:
            result = self.invoke({"AURORA_MANAGED_FD": str(worker.fileno()), "AURORA_MANAGED_TICKET": "11" * 32}, [worker.fileno()])
            self.assertEqual({"opened": 0, "cloexec": 1, "cleared": 1, "reported": 0}, result)
            parent.settimeout(1)
            frame = parent.recv(52)
            self.assertEqual(52, len(frame)); self.assertEqual(b"AEX1", frame[:4])
            self.assertEqual(bytes([17]) * 32, frame[8:40]); self.assertGreater(struct.unpack_from("<IQ", frame, 40)[0], 0)
        finally:
            parent.close(); worker.close()

    def test_absent_supervisor_remains_unmanaged_and_partial_environment_is_rejected(self):
        self.assertEqual({"opened": 0, "cloexec": 0, "cleared": 1, "reported": 0}, self.invoke({}))
        for environment in [{"AURORA_MANAGED_FD": "3"}, {"AURORA_MANAGED_TICKET": "11" * 32}]:
            self.assertEqual(-1, self.invoke(environment)["opened"])

    def test_malformed_or_missing_control_environment_cannot_enable_channel(self):
        for descriptor, ticket in [("0", "11" * 32), ("-1", "11" * 32), ("3x", "11" * 32), ("99999999999999999999", "11" * 32), ("3", "bad")]:
            with self.subTest(descriptor=descriptor):
                self.assertEqual(-1, self.invoke({"AURORA_MANAGED_FD": descriptor, "AURORA_MANAGED_TICKET": ticket})["opened"])

    def test_regular_files_cannot_be_used_as_supervisor_streams(self):
        with tempfile.TemporaryFile() as file:
            self.assertEqual(-1, self.invoke({"AURORA_MANAGED_FD": str(file.fileno()), "AURORA_MANAGED_TICKET": "11" * 32}, [file.fileno()])["opened"])


if __name__ == "__main__":
    unittest.main()
