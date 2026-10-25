#!/usr/bin/env python3
"""Actual isolated kernel socket observations and malformed netlink parser qualification."""
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/cpp/packet/SocketDiag.c'

HARNESS = r'''
#define AURORA_SOCKET_DIAG_TEST
#include "SocketDiag.c"
#include <assert.h>
int main(void) {
    char *args[] = {"diag", "ipv4", "tcp", "127.0.0.1", "1234", "127.0.0.1", "5678"};
    struct query q = {0}; assert(!arguments(7, args, &q));
    struct {struct nlmsghdr h; struct inet_diag_msg m;} record = {
        .h = {.nlmsg_len = sizeof(record), .nlmsg_type = SOCK_DIAG_BY_FAMILY, .nlmsg_seq = 1},
        .m = {.idiag_family = AF_INET, .idiag_uid = 10001, .idiag_inode = 10}};
    record.m.id = q.id;
    struct snapshot out = {0}; assert(!consume(&record, sizeof(record), &q, &out)); assert(out.count == 1);
    record.h.nlmsg_flags = NLM_F_DUMP_INTR;
    assert(consume(&record, sizeof(record), &q, &out) == -1); record.h.nlmsg_flags = 0;
    record.h.nlmsg_seq = 2; assert(consume(&record, sizeof(record), &q, &out) == -1); record.h.nlmsg_seq = 1;
    assert(consume(&record, sizeof(record) - 1, &q, &out) == -1);
    record.h.nlmsg_len = 15; assert(consume(&record, sizeof(record), &q, &out) == -1); record.h.nlmsg_len = sizeof(record);
    out.count = 32; assert(consume(&record, sizeof(record), &q, &out) == -1);
    out = (struct snapshot){0}; record.m.id.idiag_dport = htons(99);
    assert(!consume(&record, sizeof(record), &q, &out)); assert(out.count == 0);
    struct {struct nlmsghdr h; int status;} done = {.h = {.nlmsg_len = sizeof(done), .nlmsg_type = NLMSG_DONE, .nlmsg_seq = 1}};
    done.status = -EINTR; assert(consume(&done, sizeof(done), &q, &out) == -1);
    done.status = 0; assert(!consume(&done, sizeof(done), &q, &out)); assert(out.done);
    assert(consume(&record, sizeof(record), &q, &out) == -1);
    puts("Malformed/interrupted/oversized/sequence/mismatch/completion parser checks passed");
}
'''

CHILD = r'''
import json, os, socket, sys
uid, family, protocol, port, shared = map(int, sys.argv[1:])
os.setgroups([]); os.setgid(uid); os.setuid(uid)
s = socket.socket(family, protocol)
if shared: s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
address = '127.0.0.1' if family == socket.AF_INET else '::1'
s.bind((address, port))
if protocol == socket.SOCK_DGRAM: s.connect((address, 54321))
print(json.dumps({'local':s.getsockname()[:2], 'uid':os.getuid(), 'inode':os.fstat(s.fileno()).st_ino}), flush=True)
sys.stdin.buffer.read(1)
'''


class SocketDiagTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stage = tempfile.TemporaryDirectory(prefix='aurora-socket-diag-')
        cls.directory = Path(cls.stage.name); cls.directory.chmod(0o755); cls.worker = cls.directory / 'diag'
        subprocess.run(['cc', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror', str(SOURCE), '-o', str(cls.worker)], check=True)
        harness = cls.directory / 'parser.c'; harness.write_text(HARNESS)
        subprocess.run(['cc', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror', '-Wno-unused-function',
            '-I', str(SOURCE.parent), str(harness), '-o', str(cls.directory / 'parser')], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.stage.cleanup()

    def query(self, family, protocol, local, remote):
        result = subprocess.run([str(self.worker), family, protocol, local[0], str(local[1]), remote[0], str(remote[1])],
            capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        value = json.loads(result.stdout)
        self.assertEqual(value['scope'], 'current_socket_snapshot')
        self.assertLessEqual(value['finishedBootMs'] - value['startedBootMs'], 3000)
        self.assertEqual(value['netNamespace'], f'{os.stat("/proc/self/ns/net").st_dev}:{os.stat("/proc/self/ns/net").st_ino}')
        return value

    def child(self, uid, family=socket.AF_INET, protocol=socket.SOCK_DGRAM, port=0, shared=False):
        process = subprocess.Popen([sys.executable, '-c', CHILD, str(uid), str(family), str(protocol), str(port), str(int(shared))],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.addCleanup(self.stop, process)
        line = process.stdout.readline()
        self.assertTrue(line, process.stderr.read() if process.poll() is not None else 'Missing child socket')
        return process, json.loads(line)

    @staticmethod
    def stop(process):
        if process.poll() is None:
            try:
                process.communicate('x', timeout=3)
            except subprocess.TimeoutExpired:
                process.kill(); process.communicate(timeout=3)

    def test_malformed_netlink_and_partial_scan_are_rejected(self):
        subprocess.run([str(self.directory / 'parser')], check=True, timeout=5)

    def test_connected_udp_two_real_uids_and_closed_unknown(self):
        for uid in [10001, 10002]:
            process, owned = self.child(uid)
            result = self.query('ipv4', 'udp', owned['local'], ('127.0.0.1', 54321))
            self.assertEqual(result['status'], 'exact_socket_uid')
            self.assertEqual([(m['uid'], m['inode']) for m in result['matches']], [(uid, owned['inode'])])
            self.assertNotEqual(result['matches'][0]['cookie'], 'ffffffffffffffff')
            self.stop(process)
            self.assertEqual(self.query('ipv4', 'udp', owned['local'], ('127.0.0.1', 54321))['status'], 'unknown')

    def test_same_tuple_all_uids_remain_multiple(self):
        _, first = self.child(10001, shared=True)
        _, second = self.child(10002, port=first['local'][1], shared=True)
        result = self.query('ipv4', 'udp', first['local'], ('127.0.0.1', 54321))
        self.assertEqual(result['status'], 'multiple')
        self.assertEqual({(m['uid'], m['inode']) for m in result['matches']}, {(10001, first['inode']), (10002, second['inode'])})

    def test_unconnected_udp_retains_raw_match_but_unknown(self):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as owned:
            owned.bind(('127.0.0.1', 0))
            result = self.query('ipv4', 'udp', owned.getsockname(), ('0.0.0.0', 0))
            self.assertEqual(result['status'], 'unknown'); self.assertEqual(len(result['matches']), 1)

    def test_ipv6_connected_uid(self):
        _, owned = self.child(10001, socket.AF_INET6)
        result = self.query('ipv6', 'udp', owned['local'], ('::1', 54321))
        self.assertEqual(result['status'], 'exact_socket_uid'); self.assertEqual(result['matches'][0]['uid'], 10001)

    def test_tcp_live_tuple_and_inverse_are_different_sockets(self):
        with socket.socket() as server, socket.socket() as client:
            server.bind(('127.0.0.1', 0)); server.listen(); client.connect(server.getsockname())
            connection, _ = server.accept()
            with connection:
                result = self.query('ipv4', 'tcp', client.getsockname(), client.getpeername())
                reverse = self.query('ipv4', 'tcp', connection.getsockname(), connection.getpeername())
                self.assertEqual(result['status'], 'exact_socket_uid')
                self.assertEqual(result['matches'][0]['inode'], os.fstat(client.fileno()).st_ino)
                self.assertEqual(reverse['matches'][0]['inode'], os.fstat(connection.fileno()).st_ino)
                self.assertNotEqual(result['matches'][0]['cookie'], reverse['matches'][0]['cookie'])

    def test_ordinary_uid_fails_closed_and_bad_address_not_resolved(self):
        result = subprocess.run([str(self.worker), 'ipv4', 'tcp', '127.0.0.1', '1', '127.0.0.1', '2'],
            user=10001, group=10001, extra_groups=[], capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 1); self.assertEqual(json.loads(result.stdout), {'error': 'root_required'})
        result = subprocess.run([str(self.worker), 'ipv4', 'tcp', 'localhost', '1', '127.0.0.1', '2'], capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 64)


if __name__ == '__main__':
    if os.geteuid() != 0:
        raise SystemExit('Run only as Root in a fresh private network namespace')
    subprocess.run(['ip', 'link', 'set', 'lo', 'up'], check=True)
    unittest.main(verbosity=2)
