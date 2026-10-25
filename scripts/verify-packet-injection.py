#!/usr/bin/env python3
"""Actual libpcap sends only on an owned veth pair inside a fresh private network namespace."""
import json
import ctypes
import os
from pathlib import Path
import selectors
import signal
import select
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
        if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0):
            raise RuntimeError('Owned selector lifecycle fixture needs subreaper adoption')
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

    def start(self, packets=3, rate=20, seconds=3, budget=16777216, frame=None, device='aurora_tx', worker=WORKER, frames=None):
        process = subprocess.Popen([str(worker), device, str(seconds), str(rate), str(packets), str(budget),
            *[item.hex() for item in (frames or [frame or self.frame])]], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
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

    def receive(self, count, timeout=3, frames=None):
        expected = frames or [self.frame] * count
        received = []; deadline = time.monotonic() + timeout
        while len(received) < count and time.monotonic() < deadline:
            try:
                data, ancillary, _, _ = self.receiver.recvmsg(65536, 128)
            except socket.timeout:
                continue
            if data not in expected:
                continue
            self.assertEqual(data, expected[len(received)])
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

    def pcap(self, frames, endian='<', nano=False, link=1, truncated=False):
        data = struct.pack(endian + 'IHHIIII', 0xa1b23c4d if nano else 0xa1b2c3d4, 2, 4, 0, 0, 65535, link)
        for frame in frames:
            caplen = len(frame) - 1 if truncated else len(frame)
            data += struct.pack(endian + 'IIII', 1, 0, caplen, len(frame)) + frame[:caplen]
        return data

    def select(self, data, first=0, count=1, budget=16384, declared=None):
        result = subprocess.run([str(WORKER), '--select', str(len(data) if declared is None else declared), str(first),
            str(count), str(budget)], input=data, capture_output=True, timeout=5)
        self.assertEqual(result.stderr, b'aurora_select_ready\n')
        return result.returncode, json.loads(result.stdout)

    def test_offline_selection_uses_libpcap_for_all_classic_endianness_and_precision(self):
        second = self.frame + b'v' * 20
        for endian in ['<', '>']:
            for nano in [False, True]:
                data = self.pcap([self.frame, second], endian, nano)
                code, result = self.select(data, first=1)
                self.assertEqual(code, 0); self.assertEqual(result['frames'], [second.hex()])
                self.assertEqual(result['sourceBytes'], len(data)); self.assertEqual(result['selectedBytes'], 80)

    def test_offline_cooked_fcs_and_pcapng_never_produce_sendable_frames(self):
        inputs = [(self.pcap([self.frame], link=113), 'ethernet_without_fcs_required'),
            (self.pcap([self.frame], link=0x24000001), 'ethernet_without_fcs_required'),
            (b'\x0a\x0d\x0d\x0a' + bytes(20), 'classic_pcap_required')]
        for data, error in inputs:
            code, result = self.select(data)
            self.assertEqual(code, 1); self.assertEqual(result, {'error': error})

    def test_offline_snapshot_length_is_exact(self):
        data = self.pcap([self.frame])
        for declared in [len(data) - 1, len(data) + 1]:
            code, result = self.select(data, declared=declared)
            self.assertEqual(code, 1); self.assertEqual(result, {'error': 'snapshot_failed'})

    def test_offline_selection_rejects_missing_truncated_or_over_budget_frames_as_a_whole(self):
        cases = [(self.pcap([self.frame]), 2, 16384, 'selection_incomplete'),
            (self.pcap([self.frame], truncated=True), 1, 16384, 'complete_bounded_frame_required'),
            (self.pcap([self.frame])[:-1], 1, 16384, 'pcap_invalid'),
            (self.pcap([self.frame, self.frame]), 2, 119, 'complete_bounded_frame_required')]
        for data, count, budget, error in cases:
            code, result = self.select(data, count=count, budget=budget)
            self.assertEqual(code, 1); self.assertEqual(result, {'error': error})

    def test_selected_variable_length_frames_keep_wire_order_and_exact_bytes(self):
        frames = [self.frame, self.frame + b'v' * 20]
        code, selection = self.select(self.pcap(frames), count=2)
        self.assertEqual(code, 0)
        selected = [bytes.fromhex(value) for value in selection['frames']]
        process = self.start(packets=2, frames=selected)
        self.receive(2, frames=frames)
        result = self.finish(process, 0)
        self.assertTrue(result['complete']); self.assertEqual(result['protocol'], 2)
        self.assertEqual((result['selectedFrames'], result['frameBytes'], result['acceptedPackets'], result['acceptedBytes']), (2, 140, 2, 140))

    def test_offline_selector_dies_with_its_actual_parent_even_when_input_stays_open(self):
        read_fd, write_fd = os.pipe()
        code = '''import os,subprocess,sys
p=subprocess.Popen([sys.argv[1],'--select','24','0','1','16384'],stdin=int(sys.argv[2]),stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
assert p.stderr.readline()==b'aurora_select_ready\\n'
print(p.pid,flush=True)
p.wait()
'''
        parent = subprocess.Popen([sys.executable, '-c', code, str(WORKER), str(read_fd)], pass_fds=(read_fd,),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        os.close(read_fd); pidfd = None; pid = None
        try:
            with selectors.DefaultSelector() as ready:
                ready.register(parent.stdout, selectors.EVENT_READ)
                self.assertTrue(ready.select(5))
                pid = int(parent.stdout.readline())
            pidfd = os.pidfd_open(pid, 0)
            parent.kill(); parent.communicate(timeout=5)
            poller = select.poll(); poller.register(pidfd, select.POLLIN)
            self.assertTrue(poller.poll(3000), 'Selector escaped a dead creating parent')
            waited, _ = os.waitpid(pid, 0); self.assertEqual(waited, pid); pid = None
        finally:
            os.close(write_fd)
            self.stop(parent)
            if pid is not None and pidfd is not None:
                try:
                    signal.pidfd_send_signal(pidfd, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                os.waitpid(pid, 0)
            if pidfd is not None:
                os.close(pidfd)


if __name__ == '__main__':
    unittest.main()
