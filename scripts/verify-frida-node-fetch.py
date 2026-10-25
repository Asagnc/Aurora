#!/usr/bin/env python3
"""Transport retries must not weaken pinned build-host binding verification."""
import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock
import urllib.error

spec = importlib.util.spec_from_file_location('binding', Path(__file__).with_name('prepare-frida-node.py'))
binding = importlib.util.module_from_spec(spec)
spec.loader.exec_module(binding)


class BindingFetchTest(unittest.TestCase):
    def invoke(self, failures):
        helper = SimpleNamespace(fetch=Mock(side_effect=failures))
        binding.fetch_binding(helper, Path('unused'), 'https://example.invalid/fixed', 'a' * 64, 1)
        return helper

    def test_transport_timeout_can_recover_within_three_attempts(self):
        helper = self.invoke([urllib.error.URLError(TimeoutError()), TimeoutError(), None])
        self.assertEqual(3, helper.fetch.call_count)

    def test_persistent_failure_is_bounded(self):
        helper = SimpleNamespace(fetch=Mock(side_effect=TimeoutError()))
        with self.assertRaises(TimeoutError):
            binding.fetch_binding(helper, Path('unused'), 'https://example.invalid', '', 1)
        self.assertEqual(3, helper.fetch.call_count)

    def test_digest_filesystem_and_permanent_http_failures_are_not_retried(self):
        for failure in [ValueError('SHA mismatch'), FileExistsError('partial belongs to another run'),
                        urllib.error.HTTPError('https://example.invalid', 404, 'missing', {}, None)]:
            helper = SimpleNamespace(fetch=Mock(side_effect=failure))
            with self.assertRaises(type(failure)):
                binding.fetch_binding(helper, Path('unused'), 'https://example.invalid', '', 1)
            self.assertEqual(1, helper.fetch.call_count)


if __name__ == '__main__':
    unittest.main()
