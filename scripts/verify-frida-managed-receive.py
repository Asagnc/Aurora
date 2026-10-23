#!/usr/bin/env python3
"""Exact final-fact stream framing and descriptor ownership, without injection."""
import array
from contextlib import contextmanager
import errno
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
FRAME = b"AEX1" + bytes([1, 127, 0, 0]) + bytes([17]) * 32 + struct.pack("<IQ", 123, 456)
HARNESS = r'''
#define _GNU_SOURCE
#include "managed_receive.h"
#include <dirent.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static int descriptors(void) {
    DIR *directory = opendir("/proc/self/fd");
    if (!directory) exit(80);
    int count = 0;
    while (readdir(directory)) count++;
    closedir(directory); return count;
}
int main(int argc, char **argv) {
    if (argc != 2) return 64;
    setvbuf(stdout, NULL, _IONBF, 0);
    struct aurora_managed_receiver receiver = {0};
    struct aurora_managed_worker worker = {.pid = 123, .ticks = 456};
    unsigned char ticket[32]; memset(ticket, 17, sizeof(ticket));
    int before = descriptors();
    while (getchar() != EOF) {
        errno = 0;
        int result = aurora_managed_receive(&receiver, atoi(argv[1]), ticket, &worker), error = errno;
        printf("{\"result\":%d,\"error\":%d,\"flags\":%u,\"status\":%u,\"fdDelta\":%d}\n",
            result, error, receiver.receipt.flags, receiver.receipt.status, descriptors() - before);
    }
    return 0;
}
'''


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc"), "Linux C compiler required")
class ReceiveTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-managed-receive-")
        cls.root = Path(cls.temporary.name)
        source = cls.root / "harness.c"; source.write_text(HARNESS, encoding="utf-8")
        cls.binary = cls.root / "harness"
        native = ROOT / "app/src/main/cpp/frida"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-I" + str(native), str(source),
            *[str(native / name) for name in ["managed_protocol.c", "managed_receive.c"]], "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    @contextmanager
    def receiver(self):
        writer, reader = socket.socketpair()
        process = subprocess.Popen([str(self.binary), str(reader.fileno())], pass_fds=[reader.fileno()],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        reader.close(); writer.settimeout(2)
        try:
            yield writer, process
        finally:
            writer.close()
            process.communicate(timeout=3)
            self.assertEqual(0, process.returncode)

    def step(self, process):
        process.stdin.write("s"); process.stdin.flush()
        import select
        self.assertTrue(select.select([process.stdout], [], [], 2)[0], "Receiver blocked instead of returning pending")
        result = json.loads(process.stdout.readline())
        self.assertEqual(0, result["fdDelta"], "Unexpected received descriptors leaked")
        return result

    def assert_rejected(self, result):
        self.assertEqual(-1, result["result"])
        self.assertEqual(errno.EPROTO, result["error"])
        self.assertEqual(0, result["flags"])

    def test_complete_frame_without_eof_is_pending_and_never_publishes_facts(self):
        with self.receiver() as (writer, process):
            self.assertEqual(0, self.step(process)["result"])
            writer.sendall(FRAME)
            pending = self.step(process)
            self.assertEqual(0, pending["result"]); self.assertEqual(0, pending["flags"])
            writer.shutdown(socket.SHUT_WR)
            result = self.step(process)
            self.assertEqual(1, result["result"]); self.assertEqual(127, result["flags"])
            self.assertEqual(result, self.step(process), "Completed receiver must not poll EOF again")

    def test_all_frame_split_points_preserve_pending_until_eof(self):
        for split in range(1, len(FRAME)):
            with self.subTest(split=split), self.receiver() as (writer, process):
                writer.sendall(FRAME[:split])
                self.assertEqual(0, self.step(process)["result"])
                writer.sendall(FRAME[split:]); writer.shutdown(socket.SHUT_WR)
                self.assertEqual(1, self.step(process)["result"])

    def test_empty_and_every_truncated_frame_are_rejected(self):
        for size in range(len(FRAME)):
            with self.subTest(size=size), self.receiver() as (writer, process):
                if size:
                    writer.sendall(FRAME[:size])
                writer.shutdown(socket.SHUT_WR)
                self.assert_rejected(self.step(process))

    def test_extra_byte_duplicate_and_large_tail_are_rejected_before_eof(self):
        for tail in [b"x", FRAME, b"x" * 4096]:
            with self.subTest(size=len(tail)), self.receiver() as (writer, process):
                writer.sendall(FRAME + tail)
                self.assert_rejected(self.step(process))

    def test_delayed_tail_poisons_receiver_even_if_valid_frame_arrived_first(self):
        with self.receiver() as (writer, process):
            writer.sendall(FRAME)
            self.assertEqual(0, self.step(process)["result"])
            writer.sendall(b"x")
            self.assert_rejected(self.step(process))
            writer.shutdown(socket.SHUT_WR)
            self.assert_rejected(self.step(process))

    def test_rights_on_first_or_final_fragment_are_closed_and_rejected(self):
        with tempfile.TemporaryFile() as file:
            for prefix in [b"", FRAME[:-1]]:
                with self.subTest(prefix=len(prefix)), self.receiver() as (writer, process):
                    if prefix:
                        writer.sendall(prefix)
                        self.assertEqual(0, self.step(process)["result"])
                    writer.sendmsg([FRAME[len(prefix):]], [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array("i", [file.fileno()]))])
                    self.assert_rejected(self.step(process))

    def test_truncated_ancillary_array_does_not_leak_descriptors(self):
        with tempfile.TemporaryFile() as file, self.receiver() as (writer, process):
            writer.sendmsg([FRAME], [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array("i", [file.fileno()] * 64))])
            self.assert_rejected(self.step(process))

    def test_wrong_ticket_process_or_protocol_stays_unconfirmed(self):
        for offset in [0, 4, 7, 8, 40, 44]:
            with self.subTest(offset=offset), self.receiver() as (writer, process):
                changed = bytearray(FRAME); changed[offset] ^= 1
                writer.sendall(changed); writer.shutdown(socket.SHUT_WR)
                self.assert_rejected(self.step(process))

    def test_valid_partial_cleanup_is_recorded_without_becoming_success(self):
        with self.receiver() as (writer, process):
            partial = bytearray(FRAME); partial[5] = 43; partial[6] = 1
            writer.sendall(partial); writer.shutdown(socket.SHUT_WR)
            result = self.step(process)
            self.assertEqual(1, result["result"]); self.assertEqual(43, result["flags"])
            self.assertEqual(1, result["status"])


if __name__ == "__main__":
    unittest.main()
