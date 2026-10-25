#!/usr/bin/env python3
"""Execute real libpcap rotation seals and interrupted write/fsync/registration paths on owned files."""
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest

BINARY = Path(sys.argv.pop()).resolve()


class CaptureStoreTest(unittest.TestCase):
    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(prefix='aurora-capture-seal-'); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name); self.root.chmod(0o700)

    def invoke(self, count=3, segment=100, total=300, mode='normal', fail_sync=None):
        environment = {**os.environ}
        if fail_sync is not None:
            environment['AURORA_TEST_FAIL_FSYNC'] = str(fail_sync)
        result = subprocess.run([str(BINARY), str(count), str(segment), str(total), mode], cwd=self.root,
            env=environment, capture_output=True, check=True, timeout=10)
        return json.loads(result.stdout)

    def check_seal(self, ordinal, packets):
        path = self.root / f'capture-{ordinal:03d}.pcap'; data = path.read_bytes()
        seal = json.loads(path.with_suffix('.seal').read_text()); entry = seal['segment']; physical = path.stat()
        self.assertEqual(len(data), 24 + packets * 76)
        self.assertEqual(entry['sha256'], hashlib.sha256(data).hexdigest())
        self.assertEqual((entry['device'], entry['inode'], entry['bytes'], entry['packets']), (physical.st_dev, physical.st_ino, len(data), packets))
        self.assertEqual(entry['snapshotTruncated'], packets); self.assertEqual(entry['linkType'], 1)
        self.assertEqual(physical.st_mode & 0o777, 0o400); self.assertEqual(physical.st_nlink, 1)
        self.assertGreaterEqual(entry['sealedBootMs'], entry['openedBootMs'])
        self.assertEqual(struct.unpack_from('<IHHIIII', data), (0xa1b2c3d4, 2, 4, 0, 0, 262144, 1))
        for index in range(packets):
            self.assertEqual(struct.unpack_from('<IIII', data, 24 + 76 * index), (123, 456, 60, 80))
        self.assertLess(len(path.with_suffix('.seal').read_bytes()), 1024)

    def test_two_whole_segments_then_segment_limit(self):
        self.assertEqual(self.invoke(), {'result': 1, 'accepted': 2, 'segments': 2, 'bytes': 200, 'packets': 2})
        self.check_seal(0, 1); self.check_seal(1, 1)
        self.assertEqual(len(list(self.root.iterdir())), 4)

    def test_total_budget_accounts_for_each_global_header(self):
        result = self.invoke(total=199)
        self.assertEqual((result['result'], result['accepted'], result['bytes']), (1, 1, 100))
        self.check_seal(0, 1); self.assertFalse((self.root / 'capture-001.partial').exists())

    def test_empty_quiet_capture_and_packet_too_large_remain_whole(self):
        result = self.invoke(count=0); self.assertEqual(result['bytes'], 24); self.check_seal(0, 0)

    def test_oversized_single_packet_stops_without_rotating_empty_files(self):
        result = self.invoke(segment=99)
        self.assertEqual((result['result'], result['accepted'], result['segments']), (1, 0, 1)); self.check_seal(0, 0)

    def test_short_file_write_never_registers_a_seal(self):
        result = self.invoke(count=1, mode='short')
        self.assertEqual((result['result'], result['segments']), (-1, 0))
        self.assertFalse(list(self.root.glob('*.seal'))); self.assertFalse(list(self.root.glob('*.pcap')))

    def test_fsync_failures_never_count_uncommitted_records(self):
        for ordinal in range(1, 5):
            with self.subTest(sync=ordinal):
                for path in self.root.iterdir():
                    path.unlink()
                result = self.invoke(count=1, fail_sync=ordinal)
                self.assertEqual((result['result'], result['segments']), (-1, 0))
                if ordinal < 4:
                    self.assertFalse(list(self.root.glob('*.seal')))
                # A failed final directory fsync is uncertain. Existing seal must be independently revalidated on recovery.

    def test_conflicting_record_is_never_overwritten(self):
        old = self.root / 'capture-000.seal'; old.write_bytes(b'prior record')
        result = self.invoke(count=1)
        self.assertEqual((result['result'], result['segments']), (-1, 0)); self.assertEqual(old.read_bytes(), b'prior record')

    def test_existing_partial_symlink_never_modifies_target(self):
        target = self.root / 'unrelated'; target.write_bytes(b'preserve')
        (self.root / 'capture-000.partial').symlink_to(target)
        self.assertEqual(self.invoke()['result'], -1); self.assertEqual(target.read_bytes(), b'preserve')


if __name__ == '__main__':
    unittest.main()
