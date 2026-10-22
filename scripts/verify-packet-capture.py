#!/usr/bin/env python3
"""Controlled loopback only, inside a new private Linux network namespace."""
import json
import os
from pathlib import Path
import selectors
import resource
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import time
import unittest

WORKER = Path(sys.argv.pop()).resolve()


class LiveCaptureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='aurora-capture-netns-')
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def start(self, seconds=2, budget=1048576, count=1000, expression='udp dst port 43210', device='lo'):
        process = subprocess.Popen([str(WORKER), device, str(seconds), str(budget), str(count), expression],
                                   cwd=self.directory, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(self.stop, process)
        with selectors.DefaultSelector() as selector:
            selector.register(process.stderr, selectors.EVENT_READ)
            self.assertTrue(selector.select(5), 'Worker did not become ready')
            line = process.stderr.readline()
            self.assertEqual(line, b'aurora_capture_ready\n')
        return process

    @staticmethod
    def stop(process):
        if process.poll() is None:
            process.kill()
        process.communicate(timeout=5)

    def finish(self, process, expected=0):
        output, error = process.communicate(timeout=5)
        self.assertEqual(process.returncode, expected, error)
        record = json.loads(output)
        if record['fileComplete']:
            file = self.directory / 'capture.pcap'
            self.assertEqual(file.stat().st_size, record['bytes'])
            self.assertEqual(record['snapshotTruncated'], 0)
            self.assertEqual(file.stat().st_ino, record['inode'])
            self.assertEqual(file.stat().st_dev, record['device'])
            data = file.read_bytes()
            offset = 24
            packets = 0
            while offset < len(data):
                self.assertGreaterEqual(len(data) - offset, 16)
                size = struct.unpack_from('<I', data, offset + 8)[0]
                offset += 16 + size
                packets += 1
            self.assertEqual(offset, len(data))
            self.assertEqual(packets, record['packets'])
        return record

    def send(self):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sender:
            for _ in range(20):
                sender.sendto(b'aurora-test', ('127.0.0.1', 43210))

    def test_idle_deadline_has_no_packet_dependency(self):
        started = time.monotonic()
        before = resource.getrusage(resource.RUSAGE_CHILDREN)
        record = self.finish(self.start(seconds=1))
        self.assertEqual(record['reason'], 'duration')
        self.assertEqual(record['packets'], 0)
        self.assertEqual(record['bytes'], 24)
        self.assertGreaterEqual(time.monotonic() - started, 0.8)
        self.assertLess(time.monotonic() - started, 4)
        after = resource.getrusage(resource.RUSAGE_CHILDREN)
        cpu = after.ru_utime + after.ru_stime - before.ru_utime - before.ru_stime
        self.assertLess(cpu, 0.25, 'Idle capture consumed CPU instead of waiting for events')

    def test_exact_header_budget_stops_without_traffic(self):
        record = self.finish(self.start(seconds=10, budget=24))
        self.assertEqual(record['reason'], 'byte_budget')
        self.assertEqual(record['bytes'], 24)

    def test_packet_that_does_not_fit_is_never_truncated(self):
        process = self.start(seconds=10, budget=25)
        self.send()
        record = self.finish(process)
        self.assertEqual(record['reason'], 'byte_budget')
        self.assertEqual(record['packets'], 0)
        self.assertEqual(record['bytes'], 24)

    def test_packet_count_budget_preserves_complete_packets(self):
        process = self.start(seconds=10, count=3)
        self.send()
        record = self.finish(process)
        self.assertEqual(record['reason'], 'packet_budget')
        self.assertEqual(record['packets'], 3)
        self.assertGreaterEqual(record['received'], 3)

    def test_any_interface_uses_standard_cooked_link_type(self):
        process = self.start(seconds=10, count=1, device='any')
        self.send()
        record = self.finish(process)
        self.assertEqual(record['reason'], 'packet_budget')
        data = (self.directory / 'capture.pcap').read_bytes()
        self.assertIn(struct.unpack_from('<I', data, 20)[0], (113, 276))
        self.assertTrue(data.endswith(b'aurora-test'))

    def test_cancel_during_idle_flushes_but_is_not_success(self):
        process = self.start(seconds=10)
        process.send_signal(signal.SIGTERM)
        record = self.finish(process, expected=2)
        self.assertEqual(record['reason'], 'cancelled')
        self.assertTrue(record['fileComplete'])

    def test_hard_kill_cannot_emit_completion(self):
        process = self.start(seconds=10)
        process.kill()
        output, _ = process.communicate(timeout=5)
        self.assertEqual(process.returncode, -signal.SIGKILL)
        self.assertEqual(output, b'')

    def test_invalid_filter_is_explicit_setup_error(self):
        result = subprocess.run([str(WORKER), 'lo', '1', '4096', '3', 'udp and ('],
                                cwd=self.directory, capture_output=True, timeout=5)
        self.assertEqual(result.returncode, 1)
        record = json.loads(result.stdout)
        self.assertEqual(record['reason'], 'setup_error')
        self.assertFalse(record['fileComplete'])
        self.assertFalse((self.directory / 'capture.pcap').exists())

    def test_interface_down_is_failure_instead_of_idle_spin(self):
        process = self.start(seconds=10)
        subprocess.run(['ip', 'link', 'set', 'lo', 'down'], check=True)
        try:
            record = self.finish(process, expected=1)
            self.assertEqual(record['reason'], 'capture_error')
        finally:
            subprocess.run(['ip', 'link', 'set', 'lo', 'up'], check=True)

    def test_output_symlink_is_not_followed(self):
        target = self.directory / 'outside'
        target.write_bytes(b'preserve')
        (self.directory / 'capture.pcap').symlink_to(target)
        result = subprocess.run([str(WORKER), 'lo', '1', '4096', '3', ''],
                                cwd=self.directory, capture_output=True, timeout=5)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(json.loads(result.stdout)['fileComplete'])
        self.assertEqual(target.read_bytes(), b'preserve')


if __name__ == '__main__':
    if os.getuid() != 0:
        raise RuntimeError('Use sudo unshare --net; not the host network namespace')
    if os.readlink('/proc/self/ns/net') == os.readlink('/proc/1/ns/net'):
        raise RuntimeError('Refusing tests in the host network namespace')
    subprocess.run(['ip', 'link', 'set', 'lo', 'up'], check=True)
    unittest.main(verbosity=2)
