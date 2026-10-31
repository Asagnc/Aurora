#!/usr/bin/env python3
"""Exercise SDK source/release mismatch rejection using owned inert archives."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile
import apk_release_materials as materials


class ReleaseMaterials(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='aurora-sdk-release-test-')
        self.root = Path(self.temporary.name)
        tools = self.root / 'tools/apk-build'; tools.mkdir(parents=True)
        self.bundle = b'owned fixed binary'; self.own = b'inert source snapshot, never executed'
        self.pin = dict(archive='android-host-arm64.tar.gz', bytes=len(self.bundle), sha256=self.sha(self.bundle))
        self.payloads = {}
        def row(name, source=False):
            payload = ('inert ' + name).encode(); self.payloads[name] = payload
            if source:
                return dict(archive=name, archiveBytes=len(payload), archiveSha256=self.sha(payload))
            return dict(archive=name, bytes=len(payload), sha256=self.sha(payload))
        documents = {
            'native-bundle.json': self.pin,
            'sources.json': {'repositories': [row('resource.tar.gz', True)]},
            'llvm-sources.json': {'repositories': [row('compiler.tar.gz', True)]},
            'runtime-materials.json': {'packages': [{'descriptor': row('runtime.dsc'), 'sources': [row('runtime.tar.gz')]}]},
            'host-sources.json': {'sources': [row('host.tar.gz')]}}
        for name, value in documents.items():
            (tools / name).write_text(json.dumps(value))
        self.root_patch = patch.object(materials, 'ROOT', self.root); self.root_patch.start()
        self.snapshot_patch = patch.object(materials, 'snapshot', lambda path: path.write_bytes(self.own)); self.snapshot_patch.start()

    @staticmethod
    def sha(data):
        return hashlib.sha256(data).hexdigest()

    def tearDown(self):
        self.snapshot_patch.stop(); self.root_patch.stop(); self.temporary.cleanup()

    def archive(self, mutate=None):
        path = self.root / 'sources.zip'
        entries = materials.entries()
        manifest = dict(format=1, bundle=self.pin, inputs=entries, auroraSource=dict(bytes=len(self.own), sha256=self.sha(self.own)))
        payloads = {row['entry']: self.payloads[row['archive']] for row in entries}
        payloads['aurora-source.zip'] = self.own
        if mutate:
            mutate(manifest, payloads)
        payloads['manifest.json'] = json.dumps(manifest).encode()
        with zipfile.ZipFile(path, 'w') as archive:
            for name, data in payloads.items():
                archive.writestr(name, data)
        return path

    def test_complete_fixed_inventory_and_same_own_snapshot(self):
        self.assertEqual(5, len(materials.verify_sources(self.archive())['inputs']))

    def test_new_dependency_source_and_rebuild_inputs_cannot_be_omitted(self):
        name = 'owned-public-materials.zip'; self.payloads[name] = b'inert public source material'
        row = {'archive': name, 'sha256': self.sha(self.payloads[name]), 'bytes': len(self.payloads[name])}
        (self.root / 'tools/apk-build/dependency-materials.json').write_text(json.dumps({'inputs': [row]}))
        self.assertEqual(6, len(materials.verify_sources(self.archive())['inputs']))
        def missing(_, payloads): del payloads['inputs/dependencies/' + name]
        with self.assertRaises(ValueError):
            materials.verify_sources(self.archive(missing))

    def test_source_catalogue_uses_the_existing_id_commit_archive_convention(self):
        path = self.root / 'tools/apk-build/llvm-sources.json'
        row = dict(id='owned-clang', commit='a' * 40, archiveBytes=1, archiveSha256='b' * 64)
        path.write_text(json.dumps({'repositories': [row]}))
        selected = next(entry for entry in materials.entries() if entry['group'] == 'compiler')
        self.assertEqual('owned-clang-' + 'a' * 40 + '.tar.gz', selected['archive'])

    def test_tamper_missing_extra_and_wrong_pin_are_rejected(self):
        def tamper(_, payloads):
            name = next(iter(payloads)); payloads[name] = b'x' * len(payloads[name])
        def missing(_, payloads):
            del payloads[next(iter(payloads))]
        def extra(_, payloads):
            payloads['unreviewed.py'] = b'raise RuntimeError("must never run")'
        def wrong_pin(manifest, _):
            manifest['bundle'] = dict(self.pin, sha256='0' * 64)
        for mutate in [tamper, missing, extra, wrong_pin]:
            with self.subTest(mutate=mutate.__name__), self.assertRaises(ValueError):
                materials.verify_sources(self.archive(mutate))

    def test_duplicate_entries_and_other_checkout_are_rejected(self):
        path = self.archive()
        with warnings.catch_warnings(), zipfile.ZipFile(path, 'a') as archive:
            warnings.simplefilter('ignore'); archive.writestr('aurora-source.zip', self.own)
        with self.assertRaises(ValueError):
            materials.verify_sources(path)
        path = self.archive()
        with patch.object(materials, 'snapshot', lambda selected: selected.write_bytes(b'another checkout')):
            with self.assertRaises(ValueError):
                materials.verify_sources(path)

    def test_release_must_match_apk_selected_native_bundle(self):
        source = self.archive(); bundle = self.root / 'bundle'; bundle.write_bytes(self.bundle)
        apk = self.root / 'app.apk'
        for selected, accepted in [(self.pin, True), (dict(self.pin, sha256='f' * 64), False)]:
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('assets/apk-build/native-bundle.json', json.dumps(selected))
            if accepted:
                self.assertEqual(5, materials.verify_release(apk, bundle, source)['sourceInputs'])
            else:
                with self.assertRaises(ValueError):
                    materials.verify_release(apk, bundle, source)


if __name__ == '__main__':
    unittest.main()
