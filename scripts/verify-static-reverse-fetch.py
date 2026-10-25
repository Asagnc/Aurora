#!/usr/bin/env python3
"""Offline source-download publication/failure cleanup tests; no HTTP requests."""
import hashlib
import http.client
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("tool_sources", Path(__file__).with_name("verify-static-reverse-sources.py"))
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)


class FetchTest(unittest.TestCase):
    payload = b"verified source fixture"
    digest = hashlib.sha256(payload).hexdigest()

    def test_success_publishes_exact_bytes_without_partial(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "archive"
            with patch.object(source.urllib.request, "urlopen", return_value=io.BytesIO(self.payload)):
                source.fetch(target, "https://fixture.invalid/", self.digest, len(self.payload))
            self.assertEqual(self.payload, target.read_bytes())
            self.assertFalse(target.with_name("archive.partial").exists())

    def test_transport_digest_and_budget_failures_remove_owned_partial(self):
        class Broken(io.BytesIO):
            def read(self, size=-1):
                if self.tell():
                    raise OSError("fixture transport failure")
                return super().read(size)
        for kind in ("transport", "digest", "budget"):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as root:
                target = Path(root) / "archive"
                response = Broken(self.payload) if kind == "transport" else io.BytesIO(self.payload)
                digest = "0" * 64 if kind == "digest" else self.digest
                size = len(self.payload) - 1 if kind == "budget" else len(self.payload)
                with patch.object(source.urllib.request, "urlopen", return_value=response):
                    with self.assertRaises((OSError, ValueError)):
                        source.fetch(target, "https://fixture.invalid/", digest, size)
                self.assertEqual([], list(Path(root).iterdir()))

    def test_preexisting_partial_is_not_owned_or_silently_overwritten(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "archive"
            partial = target.with_name("archive.partial")
            partial.write_bytes(b"keep unknown")
            with patch.object(source.urllib.request, "urlopen") as network:
                with self.assertRaises(FileExistsError):
                    source.fetch(target, "https://fixture.invalid/", self.digest, len(self.payload))
                network.assert_not_called()
            self.assertEqual(b"keep unknown", partial.read_bytes())

    def test_short_eof_is_transport_failure_and_cannot_publish(self):
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "archive"
            with patch.object(source.urllib.request, "urlopen", return_value=io.BytesIO(self.payload[:-1])):
                with self.assertRaises(ConnectionError):
                    source.fetch(target, "https://fixture.invalid/", self.digest, len(self.payload))
            self.assertEqual([], list(Path(root).iterdir()))

    def test_incomplete_http_body_is_transport_failure_and_cleans_partial(self):
        class Incomplete(io.BytesIO):
            def read(self, size=-1):
                raise http.client.IncompleteRead(b"partial", 100)
        with tempfile.TemporaryDirectory() as root:
            with patch.object(source.urllib.request, "urlopen", return_value=Incomplete()):
                with self.assertRaises(ConnectionError):
                    source.fetch(Path(root) / "archive", "https://fixture.invalid/", self.digest, len(self.payload))
            self.assertEqual([], list(Path(root).iterdir()))

    def test_bounded_retry_recovers_short_body_without_weakening_digest(self):
        spec = importlib.util.spec_from_file_location('retry_fetch', Path(__file__).with_name('prepare-frida-node.py'))
        retry = importlib.util.module_from_spec(spec); spec.loader.exec_module(retry)
        with tempfile.TemporaryDirectory() as root:
            target = Path(root) / "archive"
            with patch.object(source.urllib.request, 'urlopen', side_effect=[io.BytesIO(self.payload[:-1]), io.BytesIO(self.payload)]) as request:
                retry.fetch_binding(source, target, 'https://fixture.invalid/', self.digest, len(self.payload))
            self.assertEqual(request.call_count, 2); self.assertEqual(target.read_bytes(), self.payload)
            self.assertFalse(target.with_name('archive.partial').exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
