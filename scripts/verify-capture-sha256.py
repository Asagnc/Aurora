#!/usr/bin/env python3
"""Compile the actual native artifact hasher and compare known/boundary/streaming vectors with hashlib."""
import hashlib
from pathlib import Path
import random
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
DRIVER = r'''
#include "Sha256.h"
#include <stdio.h>
#include <stdlib.h>
int main(int argc, char **argv) {
    unsigned chunk = argc == 2 ? (unsigned)strtoul(argv[1], NULL, 10) : 0;
    if (!chunk || chunk > 65536) return 64;
    unsigned char data[65536], digest[32]; struct aurora_sha256 hash;
    aurora_sha256_init(&hash); size_t bytes;
    while ((bytes = fread(data, 1, chunk, stdin))) aurora_sha256_update(&hash, data, bytes);
    if (ferror(stdin)) return 1;
    aurora_sha256_finish(&hash, digest);
    for (unsigned i = 0; i < 32; i++) printf("%02x", digest[i]);
    putchar('\n'); return ferror(stdout) ? 1 : 0;
}
'''


class CaptureHashTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.stage = tempfile.TemporaryDirectory(prefix='aurora-capture-hash-'); cls.addClassCleanup(cls.stage.cleanup)
        root = Path(cls.stage.name); driver = root / 'main.c'; driver.write_text(DRIVER)
        cls.binary = root / 'hash'
        subprocess.run(['cc', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror', '-I', str(ROOT / 'app/src/main/cpp/packet'),
            str(driver), str(ROOT / 'app/src/main/cpp/packet/Sha256.c'), '-o', str(cls.binary)], check=True)

    def check(self, data, chunks):
        for chunk in chunks:
            result = subprocess.run([str(self.binary), str(chunk)], input=data, capture_output=True, check=True, timeout=10)
            self.assertEqual(result.stdout.decode().strip(), hashlib.sha256(data).hexdigest(), (len(data), chunk))

    def test_known_empty_abc_and_multiblock_vectors(self):
        for data in [b'', b'abc', b'abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq']:
            self.check(data, [1, 7, 64, 4096])

    def test_padding_boundary_and_unaligned_chunk_vectors(self):
        for size in [1, 55, 56, 57, 63, 64, 65, 119, 120, 127, 128, 129, 65535, 65536, 65537]:
            self.check(bytes(i % 251 for i in range(size)), [3, 63, 65, 65536])

    def test_million_a_and_reproducible_binary_streams(self):
        self.check(b'a' * 1000000, [1, 4093, 65536])
        random_source = random.Random(0xA070CA)
        for size in [1024, 16384, 262144]:
            self.check(random_source.randbytes(size), [13, 1024, 65536])

    def test_maximum_segment_size_stream(self):
        self.check(b'\0' * (64 * 1024 * 1024), [65536])


if __name__ == '__main__':
    unittest.main()
