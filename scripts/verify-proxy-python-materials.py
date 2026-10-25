#!/usr/bin/env python3
"""Pinned Python source fallback, bounded native reader and actual runtime byte correspondence gates."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import urllib.error

spec = importlib.util.spec_from_file_location('python_materials', Path(__file__).with_name('prepare-proxy-python-materials.py'))
source = importlib.util.module_from_spec(spec); spec.loader.exec_module(source)


class PythonMaterialTest(unittest.TestCase):
    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name)

    def test_alternate_transport_retains_the_same_fixed_integrity_identity(self):
        entry = {'archive': 'owned.tar.gz', 'url': 'https://primary.invalid/fixed', 'mirrors': ['https://mirror.invalid/fixed'],
            'bytes': 5, 'sha256': 'a' * 64}
        seen = []
        def acquire(pin, directory):
            seen.append(pin)
            if pin['url'].startswith('https://primary'):
                raise urllib.error.URLError('fixture certificate failure')
            return directory / pin['archive']
        with patch.object(source, 'module', return_value=SimpleNamespace(acquire=acquire)):
            result = source.acquire_inputs([entry], self.root)
        self.assertEqual(result['owned.tar.gz'], self.root / 'owned.tar.gz')
        self.assertEqual(len(seen), 2)
        self.assertEqual({pin['sha256'] for pin in seen}, {'a' * 64}); self.assertEqual({pin['bytes'] for pin in seen}, {5})

    def test_integrity_error_never_uses_a_mirror(self):
        helper = SimpleNamespace(acquire=Mock(side_effect=ValueError('checksum')))
        with patch.object(source, 'module', return_value=helper), self.assertRaises(ExceptionGroup):
            source.acquire_inputs([{'archive': 'owned', 'url': 'https://primary.invalid', 'mirrors': ['https://other.invalid']}], self.root)
        self.assertEqual(helper.acquire.call_count, 1)

    def test_independent_materials_are_awaited_despite_another_failure(self):
        def acquire(pin, directory):
            if pin['archive'] == 'bad':
                raise ValueError('bad input')
            (directory / 'good').write_bytes(b'complete independent fixture'); return directory / 'good'
        with patch.object(source, 'module', return_value=SimpleNamespace(acquire=acquire)), self.assertRaises(ExceptionGroup):
            source.acquire_inputs([{'archive': 'bad', 'url': 'https://bad.invalid'}, {'archive': 'good', 'url': 'https://good.invalid'}], self.root)
        self.assertEqual((self.root / 'good').read_bytes(), b'complete independent fixture')

    def test_native_reader_stops_and_reaps_after_output_budget_violation(self):
        process = SimpleNamespace(stdout=io.BytesIO(b'x' * 1025), poll=Mock(return_value=None), kill=Mock(), wait=Mock(return_value=0))
        archive = object.__new__(source.FullArchive)
        with patch.object(source.subprocess, 'Popen', return_value=process), self.assertRaisesRegex(ValueError, 'output budget'):
            archive.command(['fixture'], 1024)
        process.kill.assert_called_once(); process.wait.assert_called_once(); self.assertTrue(process.stdout.closed)

    def test_full_runtime_bytes_must_match_not_just_version_or_size(self):
        payload = b'owned runtime byte fixture'; path = self.root / 'owned.tar.gz'
        with tarfile.open(path, 'w:gz') as archive:
            item = tarfile.TarInfo('python/lib/libpython3.14.so.1.0'); item.size = len(payload); archive.addfile(item, io.BytesIO(payload))
        native = {'name': 'libpython3.14.so.1.0', 'bytes': len(payload), 'sha256': hashlib.sha256(payload).hexdigest()}
        self.assertEqual(source.python_correspondence(path, {'native': [native]})['sha256'], native['sha256'])
        with self.assertRaisesRegex(ValueError, 'byte-match'):
            source.python_correspondence(path, {'native': [{**native, 'sha256': '0' * 64}]})

    def test_only_observed_builtin_dependency_notices_are_required(self):
        metadata = {'license_path': 'licenses/python', 'build_info': {'extensions': {
            '_ssl': [{'license_paths': ['licenses/ssl']}], '_unused_gui': [{'license_paths': ['licenses/gui']}]}}}
        self.assertEqual(source.required_notices(metadata, {'builtinModules': ['_ssl', 'sys']}), {'licenses/python', 'licenses/ssl'})


if __name__ == '__main__':
    unittest.main()
