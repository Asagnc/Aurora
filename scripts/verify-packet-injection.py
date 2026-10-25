#!/usr/bin/env python3
"""Actual libpcap sends only on an owned veth pair inside a fresh private network namespace."""
import json
import os
from pathlib import Path
import selectors
import signal
import socket
import struct
import subprocess
import sys
import time
import unittest

if len(sys.argv) != 3:
    raise ValueError('Expected real worker and explicit host-only short-write adapter')
FAULT_WORKER = Path(sys.argv.pop()).resolve()
WORKER = Path(sys.argv.pop()).resolve()


class InjectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.getuid() != 0:
            raise RuntimeError('Explicit private Root netns host suite only')
        subprocess.run(['ip', 'link', 'add', 'aurora_tx', 'type', 'veth', 'peer', 'name', 'aurora_rx'], check=True)
        for device, mac in [('aurora_tx', '02:00:00:00:00:01'), ('aurora_rx', '02:00:00:00:00:02')]:
            subprocess.run(['ip', 'link', 'set', device, 'address', mac, 'mtu', '1500', 'up'], check=True)

    def setUp(self):
        payload = b'aurora-owned-injection-' + os.urandom(12)
        self.frame = bytes.fromhex('02000000000202000000000188b5') + payload.ljust(46, b'\0')
        self.receiver = socket.socket(socket.AF_PACKET, socket.SOCK_RAW, socket.htons(3))
        self.receiver.bind(('aurora_rx', 0)); self.receiver.settimeout(0.1)
        # Linux 64-bit SO_TIMESTAMPNS_OLD: kernel receive timestamps, not Python scheduling time.
        self.receiver.setsockopt(socket.SOL_SOCKET, 35, 1)
        self.addCleanup(self.receiver.close)

    def start(self, packets=3, rate=20, seconds=3, budget=16777216, frame=None, device='aurora_tx', worker=WORKER):
        process = subprocess.Popen([str(worker), device, str(seconds), str(rate), str(packets), str(budget),
            (frame or self.frame).hex()], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(self.stop, process)
        with selectors.DefaultSelector() as selector:
            selector.register(process.stderr, selectors.EVENT_READ)
            self.assertTrue(selector.select(5), 'Missing bounded startup')
            self.assertEqual(process.stderr.readline(), b'aurora_inject_ready\n')
        return process

    @staticmethod
    def stop(process):
        if process.poll() is None:
            process.kill()
        process.communicate(timeout=5)

    def receive(self, count, timeout=3):
        received = []; deadline = time.monotonic() + timeout
        while len(received) < count and time.monotonic() < deadline:
            try:
                data, ancillary, _, _ = self.receiver.recvmsg(65536, 128)
            except socket.timeout:
                continue
            if data != self.frame:
                continue
            timestamps = [struct.unpack('=qq', body[:16]) for level, kind, body in ancillary
                          if level == socket.SOL_SOCKET and kind == 35 and len(body) >= 16]
            self.assertEqual(len(timestamps), 1)
            received.append(timestamps[0][0] * 1_000_000_000 + timestamps[0][1])
        self.assertEqual(len(received), count)
        return received

    def finish(self, process, code):
        output, error = process.communicate(timeout=5)
        self.assertEqual(process.returncode, code, error)
        return json.loads(output)

    def test_exact_owned_frames_and_minimum_spacing(self):
        process = self.start()
        timestamps = self.receive(3)
        result = self.finish(process, 0)
        self.assertTrue(result['complete']); self.assertEqual(result['acceptedPackets'], 3)
        self.assertEqual(result['acceptedBytes'], 180)
        self.assertTrue(all(right - left >= 40_000_000 for left, right in zip(timestamps, timestamps[1:])))

    def test_whole_frame_byte_budget_stops_without_extra_send(self):
        process = self.start(packets=4, budget=179)
        self.receive(2); result = self.finish(process, 3)
        self.assertEqual(result['reason'], 'byte_budget'); self.assertFalse(result['complete'])
        self.assertEqual((result['acceptedPackets'], result['acceptedBytes']), (2, 120))

    def test_deadline_is_not_a_successful_full_replay(self):
        process = self.start(packets=4, rate=1, seconds=1)
        result = self.finish(process, 3)
        self.assertEqual(result['reason'], 'duration'); self.assertFalse(result['complete'])
        self.assertLessEqual(result['acceptedPackets'], 1)
        self.receive(result['acceptedPackets'])

    def test_signal_cancels_pacing_without_repeating_the_frame(self):
        process = self.start(packets=4096, rate=1, seconds=30)
        self.receive(1); process.send_signal(signal.SIGTERM)
        result = self.finish(process, 2)
        self.assertEqual(result['reason'], 'cancelled'); self.assertFalse(result['complete'])
        self.assertEqual(result['acceptedPackets'], 1)

    def test_kernel_mtu_rejection_is_not_counted_as_sent(self):
        process = self.start(frame=self.frame + b'x' * 4000, packets=1)
        result = self.finish(process, 1)
        self.assertEqual(result['reason'], 'inject_error'); self.assertFalse(result['complete'])
        self.assertEqual((result['acceptedPackets'], result['acceptedBytes'], result['partialBytes']), (0, 0, 0))

    def test_short_write_adapter_is_not_retried_or_counted_as_a_whole_frame(self):
        process = self.start(worker=FAULT_WORKER)
        result = self.finish(process, 1)
        self.assertEqual(result['reason'], 'partial_write'); self.assertFalse(result['complete'])
        self.assertEqual((result['acceptedPackets'], result['acceptedBytes'], result['partialBytes']), (0, 30, 30))

    def test_malformed_input_and_any_interface_never_become_ready(self):
        for frame, device in [('00', 'aurora_tx'), ('gg' * 60, 'aurora_tx'), (self.frame.hex(), 'any')]:
            result = subprocess.run([str(WORKER), device, '1', '1', '1', '1000', frame], capture_output=True, timeout=5)
            self.assertEqual(result.returncode, 64); self.assertEqual(result.stdout, b''); self.assertEqual(result.stderr, b'')


if __name__ == '__main__':
    unittest.main()
