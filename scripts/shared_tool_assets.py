"""Validate actual APK shared-tool profiles against the same compiled trust inputs; never executes a profile."""
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def bounded(archive, name, limit=131072):
    entry = archive.getinfo(name)
    if not 0 < entry.file_size <= limit:
        raise ValueError('Shared tool asset exceeds the fixed budget')
    return archive.read(entry)


def verify(path):
    spec = importlib.util.spec_from_file_location('production_proxy_profiles', ROOT / 'scripts/qualify-proxy-product.py')
    profile = importlib.util.module_from_spec(spec); spec.loader.exec_module(profile)
    sdk_source = (ROOT / 'core/src/main/kotlin/io/github/asagnc/aurora/core/AndroidBuildTools.kt').read_text()
    metadata = dict(re.findall(r'"([a-z-]+\.xml)" to "([a-f0-9]{64})"', sdk_source))
    if set(metadata) != {'platform-package.xml', 'build-tools-package.xml'}:
        raise ValueError('Compiled SDK metadata pins differ')
    expected = {**{'assets/network-proxy/' + name: digest for name, digest in profile.profiles().items()},
        **{'assets/apk-build/' + name: digest for name, digest in metadata.items()}}
    with zipfile.ZipFile(path) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Duplicate APK entries cannot select a trusted shared tool profile')
        for name, digest in expected.items():
            if hashlib.sha256(bounded(archive, name)).hexdigest() != digest:
                raise ValueError('Actual APK shared tool asset differs: ' + name)
        if json.loads(bounded(archive, 'assets/apk-build/native-bundle.json', 16384)) != json.loads((ROOT / 'tools/apk-build/native-bundle.json').read_text()):
            raise ValueError('Actual APK native SDK selection differs')
    return {'checkedAssets': len(expected) + 1}
