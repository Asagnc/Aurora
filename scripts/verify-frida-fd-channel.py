#!/usr/bin/env python3
"""Real Unix/SCM_RIGHTS failure and FD ownership tests; no engine or target injection."""
import array
from contextlib import contextmanager
import errno
import json
import os
import select
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import unittest
import uuid

if sys.platform.startswith("linux"):
    import fcntl

ROOT = Path(__file__).resolve().parents[1]
FRAME = b"AFD1" + bytes([2, 1, 0, 0]) + bytes([17]) * 32 + bytes([34]) * 32
HARNESS = r'''
#define _GNU_SOURCE
#include "fd_channel.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
static int descriptors(void) {
    DIR *directory = opendir("/proc/self/fd");
    if (!directory) exit(80);
    int count = 0;
    while (readdir(directory)) count++;
    closedir(directory); return count;
}
int main(int argc, char **argv) {
    if (argc != 8) return 64;
    struct aurora_fd_channel channel = {.socket = atoi(argv[1]), .cancel = atoi(argv[2]), .peer = -1,
        .deadline_ms = aurora_fd_now() + atoi(argv[7])};
    struct aurora_fd_offer offer = {.peer_pid = atoi(argv[3]), .peer_uid = (uid_t) atoi(argv[4]),
        .peer_ticks = strtoull(argv[5], NULL, 10), .count = (unsigned) atoi(argv[6])};
    memset(offer.nonce, 17, 32); memset(offer.ticket, 34, 32);
    int before = descriptors(), output[2], first = -1;
    int result = aurora_fd_receive(&channel, &offer, output), error = errno;
    if (!result) { unsigned char byte; if (pread(output[0], &byte, 1, 0) == 1) first = byte; }
    for (int index = 0; index < 2; index++) if (output[index] >= 0) close(output[index]);
    printf("{\"result\":%d,\"error\":%d,\"fdDelta\":%d,\"first\":%d}\n", result, error, descriptors() - before, first);
    return 0;
}
'''


def ticks(pid):
    value = Path(f"/proc/{pid}/stat").read_text()
    return int(value[value.rfind(")") + 1:].split()[19])


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc"), "Linux C compiler required")
class ChannelTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-fd-channel-")
        cls.root = Path(cls.temporary.name)
        source = cls.root / "harness.c"
        source.write_text(HARNESS, encoding="utf-8")
        cls.binary = cls.root / "harness"
        native = ROOT / "app/src/main/cpp/frida"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-Os", "-I" + str(native), str(source),
                        *[str(native / name) for name in ["input.c", "fd_channel.c", "fd_io.c"]],
                        "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    @contextmanager
    def channel(self, count=1, timeout=1000, peer=None, uid=None, start=None):
        server, client = socket.socketpair()
        cancel_read, cancel_write = os.pipe2(os.O_CLOEXEC)
        parent = os.getpid()
        process = subprocess.Popen([str(self.binary), str(client.fileno()), str(cancel_read),
            str(parent if peer is None else peer), str(os.getuid() if uid is None else uid),
            str(ticks(parent) if start is None else start), str(count), str(timeout)],
            pass_fds=[client.fileno(), cancel_read], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        client.close(); os.close(cancel_read); server.settimeout(3)
        try:
            yield server, process, cancel_write
        finally:
            server.close(); os.close(cancel_write)
            if process.poll() is None:
                process.kill()
            process.communicate(timeout=5)

    def result(self, process):
        output, error = process.communicate(timeout=5)
        self.assertEqual(0, process.returncode, error)
        result = json.loads(output)
        self.assertEqual(0, result["fdDelta"], result)
        return result

    def read_frame(self, server):
        data = bytearray()
        while len(data) < 72:
            part = server.recv(72 - len(data))
            if not part:
                break
            data.extend(part)
        return bytes(data)

    @contextmanager
    def snapshot(self, data=b"send('owned')", sealed=True):
        fd = os.memfd_create("aurora-channel-test", os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING)
        try:
            os.write(fd, data)
            if sealed:
                fcntl.fcntl(fd, fcntl.F_ADD_SEALS, fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL)
            yield fd
        finally:
            os.close(fd)

    def offer(self, server, frame, descriptors):
        if descriptors:
            return server.sendmsg([frame], [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array("i", descriptors))])
        return server.send(frame)

    def hello(self, server, count=1):
        self.assertEqual(FRAME[:4] + bytes([1, count, 0, 0]) + FRAME[8:], self.read_frame(server))

    def test_one_and_two_sealed_snapshots_accept_fragmented_frames(self):
        for count in [1, 2]:
            with self.subTest(count=count), self.snapshot() as first, self.snapshot(b"bridge") as second, self.channel(count=count) as (server, process, _):
                self.hello(server, count)
                frame = FRAME[:5] + bytes([count]) + FRAME[6:]
                self.offer(server, frame[:1], [first, second][:count])
                for offset in range(1, 72, 7):
                    server.sendall(frame[offset:offset + 7])
                server.shutdown(socket.SHUT_WR)
                self.assertEqual(frame[:4] + bytes([3, count, 0, 0]) + frame[8:], self.read_frame(server))
                self.assertEqual(115, self.result(process)["first"])

    def test_wrong_nonce_digest_role_or_reserved_fields_close_received_fds(self):
        for index in [0, 4, 5, 6, 8, 40]:
            with self.subTest(index=index), self.snapshot() as fd, self.channel() as (server, process, _):
                self.hello(server)
                frame = bytearray(FRAME); frame[index] ^= 1
                self.offer(server, frame, [fd]); server.shutdown(socket.SHUT_WR)
                self.assertEqual(-1, self.result(process)["result"])

    def test_missing_extra_duplicate_and_truncated_control_rights_are_closed(self):
        for count in [0, 2, 40]:
            with self.subTest(count=count), self.snapshot() as fd, self.channel() as (server, process, _):
                self.hello(server); self.offer(server, FRAME, [fd] * count); server.shutdown(socket.SHUT_WR)
                self.assertEqual(-1, self.result(process)["result"])
        with self.snapshot() as fd, self.channel(count=2) as (server, process, _):
            self.hello(server, 2); self.offer(server, FRAME[:5] + bytes([2]) + FRAME[6:], [fd, fd])
            server.shutdown(socket.SHUT_WR)
            self.assertEqual(-1, self.result(process)["result"])

    def test_unsealed_empty_oversized_and_plain_disk_files_are_rejected(self):
        cases = [(b"source", False), (b"", True), (b"x" * 1048577, True)]
        for data, sealed in cases:
            with self.subTest(size=len(data), sealed=sealed), self.snapshot(data, sealed) as fd, self.channel() as (server, process, _):
                self.hello(server); self.offer(server, FRAME, [fd]); server.shutdown(socket.SHUT_WR)
                self.assertEqual(-1, self.result(process)["result"])
        with tempfile.TemporaryFile() as file, self.channel() as (server, process, _):
            file.write(b"regular"); file.flush()
            self.hello(server); self.offer(server, FRAME, [file.fileno()]); server.shutdown(socket.SHUT_WR)
            self.assertEqual(-1, self.result(process)["result"])

    def test_additional_tail_bytes_or_rights_are_rejected_without_leaks(self):
        for extra_rights in [False, True]:
            with self.subTest(extra_rights=extra_rights), self.snapshot() as fd, self.channel() as (server, process, _):
                self.hello(server); self.offer(server, FRAME, [fd])
                self.offer(server, b"X", [fd] if extra_rights else []); server.shutdown(socket.SHUT_WR)
                self.assertEqual(-1, self.result(process)["result"])

    def test_second_fragment_rights_and_early_disconnect_are_rejected(self):
        for early in [False, True]:
            with self.subTest(early=early), self.snapshot() as fd, self.channel() as (server, process, _):
                self.hello(server); self.offer(server, FRAME[:1], [fd])
                if not early:
                    self.offer(server, FRAME[1:], [fd])
                server.shutdown(socket.SHUT_WR)
                self.assertEqual(-1, self.result(process)["result"])

    def test_peer_pid_uid_and_start_ticks_must_match(self):
        for options in [{"peer": os.getpid() + 100000}, {"uid": os.getuid() + 1}, {"start": ticks(os.getpid()) + 1}]:
            with self.subTest(options=options), self.channel(**options) as (_, process, __):
                self.assertEqual(-1, self.result(process)["result"])

    def test_peer_death_wakes_wait_without_waiting_for_deadline(self):
        program = self.root / "owned-peer.py"
        program.write_text("import socket, sys\n"
            "listener=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)\n"
            "listener.bind('\\0'+sys.argv[1]);listener.listen(1)\n"
            "print('ready',flush=True)\n"
            "peer,_=listener.accept()\n"
            "data=b''\n"
            "while len(data)<72: data+=peer.recv(72-len(data))\n"
            "print('hello',flush=True)\n"
            "sys.stdin.read(1)\n", encoding="utf-8")
        name = "aurora-fd-" + uuid.uuid4().hex
        peer = subprocess.Popen([sys.executable, str(program), name], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        child = None
        try:
            self.assertTrue(select.select([peer.stdout], [], [], 3)[0]); self.assertEqual(b"ready\n", peer.stdout.readline())
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
                client.connect("\0" + name)
                child = subprocess.Popen([str(self.binary), str(client.fileno()), "-1", str(peer.pid),
                    str(os.getuid()), str(ticks(peer.pid)), "1", "3000"],
                    pass_fds=[client.fileno()], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                self.assertTrue(select.select([peer.stdout], [], [], 3)[0]); self.assertEqual(b"hello\n", peer.stdout.readline())
                peer.kill(); peer.communicate(timeout=3)
                result = self.result(child)
                self.assertEqual(-1, result["result"])
                self.assertIn(result["error"], [errno.ESRCH, errno.EPROTO])
        finally:
            if peer.poll() is None:
                peer.kill()
            peer.communicate(timeout=3)
            if child is not None:
                if child.poll() is None:
                    child.kill()
                child.communicate(timeout=3)

    def test_event_driven_deadline_and_cancellation_close_partial_fds(self):
        for cancel in [False, True]:
            with self.subTest(cancel=cancel), self.snapshot() as fd, self.channel(timeout=150 if not cancel else 1000) as (server, process, cancel_write):
                self.hello(server); self.offer(server, FRAME[:1], [fd])
                if cancel:
                    os.write(cancel_write, b"X")
                result = self.result(process)
                self.assertEqual(-1, result["result"])
                self.assertEqual(errno.ECANCELED if cancel else errno.ETIMEDOUT, result["error"])


if __name__ == "__main__":
    unittest.main()
