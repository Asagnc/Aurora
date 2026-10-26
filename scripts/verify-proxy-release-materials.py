#!/usr/bin/env python3
"""Owned inert fixtures for proxy source inventory, APK profiles and distribution mismatch rejection."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile
import proxy_release_materials as materials


class ProxyReleaseMaterials(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='aurora-proxy-release-test-')
        self.root = Path(self.temporary.name)
        self.binary = b'owned inert binary'; self.own = b'owned inert snapshot'; self.script = b'owned inert profile'
        self.pin = dict(bytes=len(self.binary), sha256=self.sha(self.binary))
        self.profile = {'aurora-proxy': self.sha(self.script)}
        self.payload = b'owned source bytes'
        self.rows = [dict(entry='inputs/rust/fixture.crate', bytes=None, sha256=self.sha(self.payload))]
        self.patches = [patch.object(materials, 'entries', lambda: self.rows),
            patch.object(materials, 'load', lambda _: self.pin), patch.object(materials, 'profiles', lambda: self.profile),
            patch.object(materials, 'snapshot', lambda path: path.write_bytes(self.own))]
        for selected in self.patches:
            selected.start()

    def tearDown(self):
        for selected in reversed(self.patches):
            selected.stop()
        self.temporary.cleanup()

    @staticmethod
    def sha(data):
        return hashlib.sha256(data).hexdigest()

    def archive(self, mutate=None):
        path = self.root / 'sources.zip'
        manifest = dict(format=1, distribution=self.pin, profiles=self.profile, inputs=self.rows,
            auroraSource=dict(bytes=len(self.own), sha256=self.sha(self.own)))
        payloads = {self.rows[0]['entry']: self.payload, 'aurora-source.zip': self.own}
        if mutate:
            mutate(manifest, payloads)
        with zipfile.ZipFile(path, 'w') as archive:
            for name, data in payloads.items():
                archive.writestr(name, data)
            archive.writestr('manifest.json', json.dumps(manifest))
        return path

    def test_exact_inventory_and_hash_only_crate_are_accepted(self):
        self.assertEqual(self.rows, materials.verify_sources(self.archive())['inputs'])

    def test_changed_missing_extra_and_other_profile_are_rejected(self):
        def changed(_, data): data[self.rows[0]['entry']] = b'changed source'
        def missing(_, data): del data[self.rows[0]['entry']]
        def extra(_, data): data['untrusted.py'] = b'never execute'
        def wrong(manifest, _): manifest['profiles'] = {'aurora-proxy': 'f' * 64}
        for mutate in [changed, missing, extra, wrong]:
            with self.subTest(mutate=mutate.__name__), self.assertRaises(ValueError):
                materials.verify_sources(self.archive(mutate))

    def test_duplicate_and_other_checkout_are_rejected(self):
        path = self.archive()
        with warnings.catch_warnings(), zipfile.ZipFile(path, 'a') as archive:
            warnings.simplefilter('ignore'); archive.writestr('aurora-source.zip', self.own)
        with self.assertRaises(ValueError):
            materials.verify_sources(path)
        path = self.archive()
        with patch.object(materials, 'snapshot', lambda selected: selected.write_bytes(b'other checkout')):
            with self.assertRaises(ValueError):
                materials.verify_sources(path)

    def test_apk_profiles_and_distribution_must_both_match(self):
        sources = self.archive(); binary = self.root / 'binary'; binary.write_bytes(self.binary)
        apk = self.root / 'app.apk'
        for data, accepted in [(self.script, True), (b'changed profile', False)]:
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('assets/network-proxy/aurora-proxy', data)
            if accepted:
                self.assertFalse(materials.verify_release(apk, binary, sources)['phoneRuntimeQualified'])
            else:
                with self.assertRaises(ValueError):
                    materials.verify_release(apk, binary, sources)
        binary.write_bytes(b'other binary')
        with self.assertRaises(ValueError):
            materials.verify_release(apk, binary, sources)


if __name__ == '__main__':
    unittest.main()
