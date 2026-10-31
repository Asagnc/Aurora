#!/usr/bin/env python3
"""Reject tampered/cross-baseline public materials and preserve unresolved POM inheritance."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from apk_dependency_licenses import declarations

spec = importlib.util.spec_from_file_location('public_material_package', Path(__file__).with_name('package-apk-dependency-materials.py'))
package = importlib.util.module_from_spec(spec); spec.loader.exec_module(package)


class PublicMaterialsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(); self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.data = b'owned inert public source fixture'
        self.pin = {'sha256': 'a' * 64}

    def archive(self, alter=None):
        row = {'path': 'sources/owned.java', 'bytes': len(self.data), 'sha256': hashlib.sha256(self.data).hexdigest()}
        manifest = {'format': 1, 'cachePin': self.pin, 'files': [row]}
        payloads = {row['path']: self.data}
        if alter:
            alter(manifest, payloads)
        path = self.root / 'materials.zip'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('manifest.json', json.dumps(manifest))
            for name, data in payloads.items():
                archive.writestr(name, data)
        return path

    def test_complete_pinned_inventory_passes(self):
        self.assertEqual(1, len(package.verify_zip(self.archive(), self.pin)['files']))

    def test_tampering_cross_baseline_extra_and_duplicate_records_are_rejected(self):
        def tamper(_, data): data['sources/owned.java'] = b'x' * len(self.data)
        def other(manifest, _): manifest['cachePin'] = {'sha256': 'b' * 64}
        def extra(_, data): data['private.properties'] = b'inert unknown marker'
        def duplicate(manifest, _): manifest['files'] *= 2
        for alter in [tamper, other, extra, duplicate]:
            with self.subTest(alter=alter.__name__), self.assertRaises(ValueError):
                package.verify_zip(self.archive(alter), self.pin)

    def test_traversal_inventory_is_rejected_without_reading_an_external_file(self):
        def escape(manifest, data):
            manifest['files'][0]['path'] = '../outside'
            data['../outside'] = data.pop('sources/owned.java')
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            package.verify_zip(self.archive(escape), self.pin)

    def test_parent_license_is_recorded_and_missing_parent_stays_unresolved(self):
        child = self.root / 'child.pom'; parent = self.root / 'parent.pom'
        child.write_text('<project><parent><groupId>example</groupId><artifactId>parent</artifactId><version>1</version></parent></project>')
        parent.write_text('<project><licenses><license><name>Owned license fixture</name><url>https://example.invalid/license</url></license></licenses></project>')
        report = {'metadata': [{'path': 'child.pom', 'coordinate': 'example:child:1'},
                               {'path': 'parent.pom', 'coordinate': 'example:parent:1'}],
                  'binaries': {'example:child:1': []}, 'cachePin': self.pin}
        declarations(self.root, report)
        self.assertEqual([], report['missingLicenseDeclarations'])
        self.assertEqual('example:parent:1', report['licenseDeclarations'][0]['licenses'][0]['fromCoordinate'])
        report['metadata'].pop()
        declarations(self.root, report)
        self.assertEqual(['example:child:1'], report['missingLicenseDeclarations'])
        self.assertEqual('parent_pom_missing', report['licenseDeclarations'][0]['unresolved'])


if __name__ == '__main__':
    unittest.main()
