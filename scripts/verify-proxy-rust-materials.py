#!/usr/bin/env python3
"""Pinned crate transfers must fail closed and retain unowned cache state."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('rust_materials', Path(__file__).with_name('prepare-proxy-rust-materials.py'))
rust = importlib.util.module_from_spec(spec); spec.loader.exec_module(rust)


class RustMaterialTest(unittest.TestCase):
    payload = b'owned crate source bytes'
    crate = {'name': 'owned', 'version': '1.0.0', 'sha256': hashlib.sha256(payload).hexdigest()}

    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name); self.target = self.root / 'owned-1.0.0.crate'

    def response(self, data, size=None):
        incoming = io.BytesIO(data); incoming.headers = {'Content-Length': str(len(data) if size is None else size)}
        return incoming

    def test_verified_source_publishes_exact_bytes(self):
        with patch.object(rust.urllib.request, 'urlopen', return_value=self.response(self.payload)):
            rust.download_once(self.crate, self.target)
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertFalse(self.target.with_name(self.target.name + '.partial').exists())

    def test_digest_mismatch_and_oversized_declaration_never_publish(self):
        for response in [self.response(b'x' * len(self.payload)), self.response(b'x', 64 * 1024 ** 2 + 1)]:
            with patch.object(rust.urllib.request, 'urlopen', return_value=response), self.assertRaises(ValueError):
                rust.download_once(self.crate, self.target)
            self.assertEqual(list(self.root.iterdir()), [])

    def test_short_body_cleans_owned_partial_and_is_transport_failure(self):
        with patch.object(rust.urllib.request, 'urlopen', return_value=self.response(self.payload[:-1], len(self.payload))), self.assertRaises(ConnectionError):
            rust.download_once(self.crate, self.target)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_preexisting_partial_is_retained_without_network(self):
        partial = self.target.with_name(self.target.name + '.partial'); partial.write_bytes(b'unknown')
        with patch.object(rust.urllib.request, 'urlopen') as network, self.assertRaises(FileExistsError):
            rust.download_once(self.crate, self.target)
        network.assert_not_called(); self.assertEqual(partial.read_bytes(), b'unknown')

    def test_checksum_failure_is_not_retried(self):
        with patch.object(rust, 'download_once', side_effect=ValueError('checksum')) as transfer, self.assertRaises(ValueError):
            rust.acquire(self.crate, self.root)
        self.assertEqual(transfer.call_count, 1)

    def test_cache_corruption_is_rejected_without_network(self):
        self.target.write_bytes(b'changed')
        with patch.object(rust, 'download_once') as transfer, self.assertRaisesRegex(ValueError, 'content differs'):
            rust.acquire(self.crate, self.root)
        transfer.assert_not_called()


if __name__ == '__main__':
    unittest.main()
