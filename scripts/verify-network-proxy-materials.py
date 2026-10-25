#!/usr/bin/env python3
"""Read-only source/native correspondence gates and notice archive boundary tests."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('proxy_materials', Path(__file__).with_name('prepare-network-proxy-materials.py'))
materials = importlib.util.module_from_spec(spec); spec.loader.exec_module(materials)


class ProxyMaterialTest(unittest.TestCase):
    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name); self.store = self.root / 'notices'; self.store.mkdir()

    def test_changed_source_version_cannot_replace_pinned_lock_material(self):
        source = self.root / 'catalog'; source.mkdir()
        for name in ['upstream.json', 'upstream-uv.lock', 'source-materials.json', 'mitmproxy-LICENSE']:
            shutil.copyfile(materials.CATALOG / name, source / name)
        target = source / 'source-materials.json'; value = json.loads(target.read_text())
        value['packages'][0]['version'] = '999.0'; target.write_text(json.dumps(value))
        with patch.object(materials, 'CATALOG', source), self.assertRaisesRegex(ValueError, 'exact upstream lock'):
            materials.material_plan()

    def tar(self, name, symbolic=False):
        path = self.root / 'source.tar.gz'
        with tarfile.open(path, 'w:gz') as source:
            item = tarfile.TarInfo(name)
            if symbolic:
                item.type = tarfile.SYMTYPE; item.linkname = '../../outside'; source.addfile(item)
            else:
                data = b'owned notice'; item.size = len(data); source.addfile(item, io.BytesIO(data))
        return path

    def test_source_notice_keeps_exact_content_and_origin(self):
        found = materials.notices(self.tar('owned/LICENSE'), self.store)
        self.assertEqual(found[0]['entry'], 'owned/LICENSE')
        self.assertEqual((self.store / found[0]['stored']).read_bytes(), b'owned notice')

    def test_source_traversal_and_notice_link_do_not_escape_or_follow(self):
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            materials.notices(self.tar('../LICENSE'), self.store)
        with self.assertRaisesRegex(ValueError, 'Nonregular'):
            materials.notices(self.tar('owned/LICENSE', symbolic=True), self.store)
        self.assertEqual(list(self.store.iterdir()), [])

    def test_wheel_native_requires_exact_full_bytes_and_digest(self):
        path = self.root / 'owned.whl'; data = b'\x7fELFowned-native-byte-fixture'
        with zipfile.ZipFile(path, 'w') as source:
            source.writestr('owned/native.so', data)
        digest = hashlib.sha256(data).hexdigest()
        embedded = [{'name': 'exact.so', 'sha256': digest, 'bytes': len(data)},
                    {'name': 'wrong-size.so', 'sha256': digest, 'bytes': len(data) + 1},
                    {'name': 'wrong-hash.so', 'sha256': '0' * 64, 'bytes': len(data)}]
        self.assertEqual([item['embedded'] for item in materials.native_matches(path, embedded)], ['exact.so'])

    def test_wheel_path_traversal_is_rejected_even_during_read_only_scan(self):
        path = self.root / 'owned.whl'
        with zipfile.ZipFile(path, 'w') as source:
            source.writestr('../foreign', b'no execution')
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            materials.native_matches(path, [])


if __name__ == '__main__':
    unittest.main()
