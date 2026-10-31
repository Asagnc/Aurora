#!/usr/bin/env python3
"""Release correspondence and fail-closed signature/output checks."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('package-release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def test_guard_apk_digest_and_owned_sources_are_checked_without_faking_profile_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); apk = root / 'app.apk'; source = root / 'source.zip'
            files = {'app/src/main/cpp/CMakeLists.txt': b'owned top-level config',
                     'app/src/main/cpp/managed/Guard.cmake': b'owned config',
                     'app/src/main/cpp/managed/sources.txt': b'managed/guard.c\n',
                     'app/src/main/cpp/managed/guard.c': b'owned inert guard source'}
            for name, data in files.items():
                path = root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
            guard = b'owned inert guard binary'; digest = hashlib.sha256(guard).hexdigest()
            reference = {'sha256': 'b' * 64, 'sources': {name: hashlib.sha256(data).hexdigest()
                         for name, data in files.items() if name.endswith('.c')}}
            def artifacts(asset, changed=False):
                with zipfile.ZipFile(apk, 'w') as archive:
                    archive.writestr('lib/arm64-v8a/libaurora_managed_guard.so', guard)
                    archive.writestr('assets/managed/arm64-v8a.guard.sha256', asset)
                with zipfile.ZipFile(source, 'w') as archive:
                    for name, data in files.items():
                        archive.writestr(name, b'changed' if changed and name.endswith('.c') else data)
            with patch.object(release, 'ROOT', root):
                artifacts(digest)
                result = release.guard_source_identity(apk, source, reference)
                self.assertEqual(digest, result['apkGuardSha256']); self.assertFalse(result['byteIdentical'])
                artifacts('a' * 64)
                with self.assertRaises(ValueError): release.guard_source_identity(apk, source, reference)
                artifacts(digest, True)
                with self.assertRaises(ValueError): release.guard_source_identity(apk, source, reference)

    def test_wrong_package_debuggable_and_wrong_platform_are_rejected(self):
        sample = "package: name='io.github.asagnc.aurora' versionCode='41' versionName='0.1.40'\nminSdkVersion:'36'\ntargetSdkVersion:'37'\n"
        self.assertEqual('io.github.asagnc.aurora', release.manifest_identity(sample)['applicationId'])
        for invalid in [sample.replace('io.github.asagnc.aurora', 'other.app'), sample + 'application-debuggable',
                        sample.replace("minSdkVersion:'36'", "minSdkVersion:'35'"), sample.replace("versionCode='41'", "versionCode='40'")]:
            with self.assertRaises(ValueError):
                release.manifest_identity(invalid)
    def test_wrong_worker_and_bridge_cannot_accompany_apk(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'app.apk'
            sources = Path(temporary) / 'source.zip'
            worker = b'owned-worker'
            digest = hashlib.sha256(worker).hexdigest()
            bridge = {'version': 'fixture', 'sha256': 'a' * 64, 'bytes': 1}
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('assets/managed/arm64-v8a.worker.sha256', digest)
                archive.writestr('lib/arm64-v8a/libaurora_frida.so', worker)
                archive.writestr('assets/managed/java-bridge.json', json.dumps(bridge))
            def source(sha, selected):
                with zipfile.ZipFile(sources, 'w') as archive:
                    archive.writestr('qualification.json', json.dumps({'workerSha256': sha}))
                    archive.writestr('tools/frida-bridge/bundle-inputs.json', json.dumps({'script': selected}))
            source(digest, bridge)
            self.assertEqual(digest, release.source_identity(apk, sources)['workerSha256'])
            source('b' * 64, bridge)
            with self.assertRaises(ValueError):
                release.source_identity(apk, sources)
            source(digest, {**bridge, 'sha256': 'c' * 64})
            with self.assertRaises(ValueError):
                release.source_identity(apk, sources)

    def test_unsigned_review_never_claims_signature_and_release_requires_pin(self):
        args = SimpleNamespace(unsigned_review=True, apksigner=None, certificate_sha256=None)
        self.assertFalse(release.verify_signature(Path('unused'), args)['verified'])
        args.certificate_sha256 = 'a' * 64
        with self.assertRaises(ValueError):
            release.verify_signature(Path('unused'), args)
        args.unsigned_review = False
        with self.assertRaises(ValueError):
            release.verify_signature(Path('unused'), args)

    def test_existing_output_is_preserved_before_any_input_is_read(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'release'
            output.mkdir()
            marker = output / 'keep'
            marker.write_bytes(b'prior-release')
            with self.assertRaises(FileExistsError):
                release.stage(SimpleNamespace(output=output))
            self.assertEqual(b'prior-release', marker.read_bytes())


if __name__ == '__main__':
    unittest.main()
