#!/usr/bin/env python3
"""Real native binary config parsing/sealed-memfd rejection before Root capture dispatch."""
import fcntl
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
DRIVER = r'''
#include "RotateConfig.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
int main(int argc, char **argv) {
    struct aurora_capture_config config;
    if (argc == 2) {
        if (aurora_capture_config_fd(atoi(argv[1]), &config)) return 1;
    } else {
        unsigned char data[4373]; size_t size = fread(data, 1, sizeof(data), stdin);
        if (ferror(stdin) || aurora_capture_config_decode(data, size, &config)) return 1;
    }
    printf("{\"seconds\":%u,\"segmentBytes\":%llu,\"bytes\":%llu,\"packets\":%llu,\"segments\":%u,"
        "\"pid\":%u,\"ticks\":%llu,\"run\":\"%s\",\"device\":\"%s\",\"filterBytes\":%zu}\n",
        config.seconds, (unsigned long long)config.segment_bytes, (unsigned long long)config.bytes, (unsigned long long)config.packets,
        config.segments, config.root_pid, (unsigned long long)config.root_ticks, config.run, config.device, strlen(config.filter));
    return ferror(stdout) ? 1 : 0;
}
'''


def config():
    ids = [f'00000000-0000-0000-0000-{i:012d}'.encode() for i in [1, 2, 3, 4]]
    device = b'lo'; expression = b'udp port 1234'
    return struct.pack('<4sIIIQQQIIIQ36s36s36s36sII', b'AURC', 1, 212, 30, 128, 256, 10, 2, 262144,
        123, 456, *ids, len(device), len(expression)) + device + expression


class CaptureConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stage = tempfile.TemporaryDirectory(prefix='aurora-capture-config-'); cls.addClassCleanup(cls.stage.cleanup)
        root = Path(cls.stage.name); driver = root / 'main.c'; driver.write_text(DRIVER); cls.binary = root / 'config'
        packet = ROOT / 'app/src/main/cpp/packet'
        subprocess.run(['cc', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror', '-I', str(packet), str(driver),
            str(packet / 'RotateConfig.c'), '-o', str(cls.binary)], check=True)

    def invoke(self, data):
        return subprocess.run([str(self.binary)], input=data, capture_output=True, timeout=5)

    def test_exact_fixed_golden_fields(self):
        result = self.invoke(config()); self.assertEqual(result.returncode, 0)
        self.assertEqual(json.loads(result.stdout), {'seconds': 30, 'segmentBytes': 128, 'bytes': 256, 'packets': 10,
            'segments': 2, 'pid': 123, 'ticks': 456, 'run': '00000000-0000-0000-0000-000000000001', 'device': 'lo', 'filterBytes': 13})

    def test_header_lengths_caps_and_identity_are_independently_bounded(self):
        cases = [(0, '<I', 0), (4, '<I', 2), (8, '<I', 211), (12, '<I', 1801), (16, '<Q', 39),
            (24, '<Q', 127), (24, '<Q', 536870913), (32, '<Q', 10000001), (40, '<I', 65),
            (44, '<I', 65536), (48, '<I', 0), (52, '<Q', 0), (52, '<Q', 0x8000000000000000), (204, '<I', 0xffffffff), (208, '<I', 4097)]
        for offset, fmt, value in cases:
            data = bytearray(config()); struct.pack_into(fmt, data, offset, value)
            self.assertEqual(self.invoke(data).returncode, 1, (offset, value))
        for data in [config()[:-1], config() + b'\0', config()[:212], b'x' * 4373]:
            self.assertEqual(self.invoke(data).returncode, 1)

    def test_uuid_interface_nul_and_invalid_utf8_are_not_repaired(self):
        for offset, value in [(60, ord('G')), (68, ord('0')), (212, ord(';')), (220, 0), (220, 0xff)]:
            data = bytearray(config()); data[offset] = value
            self.assertEqual(self.invoke(data).returncode, 1)
        for expression in [b'\xc0\x80', b'\xed\xa0\x80', b'\xf4\x90\x80\x80', b'\xe0\x80\x80', b'\xe2\x82']:
            data = bytearray(config()[:214]); struct.pack_into('<I', data, 208, len(expression))
            self.assertEqual(self.invoke(data + expression).returncode, 1)

    def test_all_four_memfd_seals_are_required_and_regular_file_rejected(self):
        required = fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL
        for seals in [required, required & ~fcntl.F_SEAL_SEAL, 0]:
            fd = os.memfd_create('owned-capture-config', os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING)
            try:
                os.write(fd, config()); fcntl.fcntl(fd, fcntl.F_ADD_SEALS, seals)
                result = subprocess.run([str(self.binary), str(fd)], pass_fds=(fd,), capture_output=True, timeout=5)
                self.assertEqual(result.returncode, 0 if seals == required else 1)
            finally:
                os.close(fd)
        with tempfile.TemporaryFile() as file:
            file.write(config()); file.flush()
            result = subprocess.run([str(self.binary), str(file.fileno())], pass_fds=(file.fileno(),), capture_output=True, timeout=5)
            self.assertEqual(result.returncode, 1)


if __name__ == '__main__':
    unittest.main()
