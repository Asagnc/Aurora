#!/usr/bin/env python3
"""Pinned build recipe parsing never executes downloaded Python; vendor headers reject links/ambiguity."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('crypto_materials', Path(__file__).with_name('prepare-proxy-crypto-materials.py'))
source = importlib.util.module_from_spec(spec); spec.loader.exec_module(source)


class CryptoMaterialTest(unittest.TestCase):
    def setUp(self):
        self.stage = tempfile.TemporaryDirectory(); self.addCleanup(self.stage.cleanup)
        self.root = Path(self.stage.name)

    def test_fixed_recipe_chain_is_parsed_without_importing_python(self):
        pin = json.loads((source.ROOT / 'tools/network-proxy/crypto-materials.json').read_text())
        target = self.root / 'tools/network-proxy/crypto-recipes'; target.mkdir(parents=True)
        for entry in pin['recipes']:
            raw = (source.ROOT / 'tools/network-proxy/crypto-recipes' / entry['file']).read_bytes()
            if entry['file'].endswith('.py'):
                raw += ('\nopen(' + repr(str(self.root / 'must-not-execute')) + ', "w").write("bad")\n').encode()
                entry.update(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest(),
                    gitBlobSha1=hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest())
            (target / entry['file']).write_bytes(raw)
        with patch.object(source, 'ROOT', self.root):
            source.recipes(pin)
            self.assertFalse((self.root / 'must-not-execute').exists())
            (target / 'aioquic-vendor.json').write_bytes(b'{}')
            with self.assertRaisesRegex(ValueError, 'Git blob'):
                source.recipes(pin)

    def archive(self, mode):
        path = self.root / 'vendor.tar.gz'
        with tarfile.open(path, 'w:gz') as archive:
            header = tarfile.TarInfo('include/openssl/opensslv.h')
            data = b'# define OPENSSL_VERSION_STR "3.3.0"\n'
            if mode == 'wrong':
                data = data.replace(b'3.3.0', b'3.3.1')
            header.size = len(data)
            if mode == 'link':
                header.type = tarfile.SYMTYPE; header.linkname = '/outside'; archive.addfile(header)
            else:
                archive.addfile(header, io.BytesIO(data))
                if mode == 'duplicate':
                    archive.addfile(header, io.BytesIO(data))
        return path

    def test_vendor_exact_header_and_wrong_version(self):
        self.assertEqual(source.vendor_identity(self.archive('valid'))['version'], '3.3.0')
        with self.assertRaisesRegex(ValueError, 'version differs'):
            source.vendor_identity(self.archive('wrong'))

    def test_vendor_link_and_duplicate_header_are_not_version_evidence(self):
        for mode in ['link', 'duplicate']:
            with self.assertRaisesRegex(ValueError, 'bounded regular data'):
                source.vendor_identity(self.archive(mode))


if __name__ == '__main__':
    unittest.main()
