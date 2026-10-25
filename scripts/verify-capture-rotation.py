#!/usr/bin/env python3
"""Real Root FD bootstrap and one-handle loopback capture in a fresh owned network namespace."""
import array
import fcntl
import hashlib
import json
import os
from pathlib import Path
import select
import socket
import struct
import subprocess
import sys
import tempfile
import time
import unittest
import uuid

BINARY = Path(sys.argv.pop()).resolve()


def ticks(pid):
    raw = Path(f'/proc/{pid}/stat').read_text()
    return int(raw[raw.rfind(')') + 1:].split()[19])


def exact(channel, size):
    data = b''
    while len(data) < size:
        part = channel.recv(size - len(data))
        if not part:
            raise ValueError('Truncated worker handshake')
        data += part
    return data


class RotationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.getuid():
            raise RuntimeError('Explicit Root private-network host fixture only')
        subprocess.run(['ip', 'link', 'set', 'lo', 'up'], check=True)

    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(prefix='aurora-capture-live-'); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name); self.root.chmod(0o700)

    def start(self, seconds=2, packets=100, segment=256, total=512, corrupt=False):
        name = 'aurora-fd-' + uuid.uuid4().hex; nonce = bytes([0xbb]) * 32; ticket = bytes([0xaa]) * 32
        listener = socket.socket(socket.AF_UNIX); listener.bind('\0' + name); listener.listen(1); listener.settimeout(5)
        self.addCleanup(listener.close)
        parent, child = socket.socketpair(); parent.settimeout(5); self.addCleanup(parent.close)
        identity = [str(uuid.uuid4()), str(uuid.uuid4()), Path('/proc/sys/kernel/random/boot_id').read_text().strip(), str(uuid.uuid4())]
        device = b'lo'; expression = b'udp dst port 29387'
        data = struct.pack('<4sIIIQQQIIIQ36s36s36s36sII', b'AURC', 1, 212, seconds, segment, total, packets, 64, 262144,
            os.getpid(), ticks(os.getpid()), *[item.encode() for item in identity], len(device), len(expression)) + device + expression
        if corrupt:
            data = b'BAD!' + data[4:]
        fd = os.memfd_create('owned-capture-config', os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING)
        self.addCleanup(os.close, fd); os.write(fd, data)
        fcntl.fcntl(fd, fcntl.F_ADD_SEALS, fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL)
        process = subprocess.Popen([str(BINARY), 'capture-channel', name, nonce.hex(), str(os.getpid()), str(ticks(os.getpid()))],
            pass_fds=[child.fileno()], env={**os.environ, 'AURORA_MANAGED_FD': str(child.fileno()), 'AURORA_MANAGED_TICKET': ticket.hex()},
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=self.root)
        child.close()
        def reap():
            if process.poll() is None:
                process.kill()
            process.communicate(timeout=5)
        self.addCleanup(reap)
        peer, _ = listener.accept(); self.addCleanup(peer.close); peer.settimeout(5)
        expected = b'AFD1' + bytes([1, 1, 0, 0]) + nonce + ticket
        self.assertEqual(exact(peer, 72), expected)
        peer.sendmsg([b'AFD1' + bytes([2, 1, 0, 0]) + nonce + ticket], [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array('i', [fd]))])
        peer.shutdown(socket.SHUT_WR); self.assertEqual(exact(peer, 72), b'AFD1' + bytes([3, 1, 0, 0]) + nonce + ticket)
        if not corrupt:
            self.assertTrue(select.select([process.stderr], [], [], 5)[0]); self.assertEqual(process.stderr.readline(), b'aurora_capture_rotation_ready\n')
        return process, parent

    def finish(self, process, control):
        output, error = process.communicate(timeout=6)
        frame = exact(control, 52)
        self.assertEqual(frame[:5], b'AEX2\x02'); self.assertEqual(frame[8:40], bytes([0xaa]) * 32)
        self.assertEqual(struct.unpack_from('<I', frame, 40)[0], process.pid)
        self.assertGreater(struct.unpack_from('<Q', frame, 44)[0], 0)
        self.assertEqual(frame[6], process.returncode); self.assertEqual(frame[5], 193 if not process.returncode else 129)
        self.assertEqual(control.recv(1), b'')
        return json.loads(output) if output else None

    def send(self, count):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as output:
            for ordinal in range(count):
                output.sendto(b'aurora-sequence-' + struct.pack('<I', ordinal), ('127.0.0.1', 29387))

    def test_duration_seals_quiet_empty_segment(self):
        process, control = self.start(seconds=1)
        report = self.finish(process, control)
        self.assertEqual((process.returncode, report['reason'], report['bytes']), (0, 'duration', 24))
        self.assertEqual(len(list(self.root.glob('*.seal'))), 1)
        config = self.root / 'capture.config'
        self.assertEqual(config.stat().st_mode & 0o777, 0o400)
        self.assertEqual(config.read_bytes()[:4], b'AURC')
        self.assertEqual(struct.unpack_from('<I', config.read_bytes(), 48)[0], os.getpid())
        self.assertFalse((self.root / 'capture.config.partial').exists())

    def test_sequence_across_whole_segments_without_reopening_interface(self):
        process, control = self.start(packets=4, segment=170, total=1024)
        self.send(4); report = self.finish(process, control)
        self.assertEqual((process.returncode, report['reason'], report['packets']), (0, 'packet_budget', 4))
        observed = []
        for ordinal, path in enumerate(sorted(self.root.glob('*.pcap'))):
            data = path.read_bytes(); entry = json.loads(path.with_suffix('.seal').read_text())['segment']
            self.assertEqual(entry['ordinal'], ordinal); self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
            offset = 24
            while offset < len(data):
                size = struct.unpack_from('<I', data, offset + 8)[0]; packet = data[offset + 16:offset + 16 + size]
                marker = packet.index(b'aurora-sequence-'); observed.append(struct.unpack_from('<I', packet, marker + 16)[0])
                offset += 16 + size
            self.assertEqual(offset, len(data))
        self.assertEqual(observed, list(range(4)))

    def test_cancel_keeps_a_verified_prefix_and_reports_cancel(self):
        process, control = self.start(); self.send(1); time.sleep(0.15); control.sendall(b'\x02')
        report = self.finish(process, control)
        self.assertEqual((process.returncode, report['reason']), (2, 'cancelled'))
        self.assertEqual(len(list(self.root.glob('*.seal'))), 1)

    def test_corrupt_authenticated_config_never_opens_a_capture_file(self):
        process, control = self.start(corrupt=True); self.assertIsNone(self.finish(process, control))
        self.assertEqual(process.returncode, 64); self.assertEqual(list(self.root.iterdir()), [])

    def test_supervisor_protocol_failure_is_not_success(self):
        process, control = self.start(); control.sendall(b'\x09')
        report = self.finish(process, control)
        self.assertEqual((process.returncode, report['reason']), (1, 'supervisor_error'))


if __name__ == '__main__':
    unittest.main()
