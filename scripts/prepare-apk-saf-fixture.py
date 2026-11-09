#!/usr/bin/env python3
"""Add an unexported, opt-in SAF fixture only to an existing isolated source copy."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'io.github.asagnc.aurora.fridaqualification'


def prepare(source):
    source = source.resolve()
    if source.is_relative_to(ROOT) or ROOT.is_relative_to(source):
        raise ValueError('Use the separate qualification source copy, never the production checkout')
    evidence = source / 'qualification-input/preparation.json'
    report = json.loads(evidence.read_text(encoding='utf-8'))
    if report['applicationId'] != PACKAGE or report['productionIntegrated']:
        raise ValueError('Not the isolated qualification identity')
    build = (source / 'app/build.gradle.kts').read_text(encoding='utf-8')
    if f'applicationId = "{PACKAGE}"' not in build:
        raise ValueError('Qualification application ID changed')
    hashes = {}
    for name in ['ApkSafQualificationActivity.kt', 'ApkSafOfflineInputs.kt', 'ApkSafDeviceTest.kt']:
        folder = 'androidTest' if name.endswith('DeviceTest.kt') else 'main'
        relative = f'app/src/{folder}/kotlin/io/github/asagnc/aurora/qualification/{name}'
        target = source / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        template = ROOT / 'tools/apk-saf-qualification' / name
        with template.open('rb') as incoming, target.open('xb') as output:
            shutil.copyfileobj(incoming, output)
        hashes[relative] = hashlib.sha256(target.read_bytes()).hexdigest()
    manifest = source / 'app/src/main/AndroidManifest.xml'
    text = manifest.read_text(encoding='utf-8')
    if text.count('</application>') != 1:
        raise ValueError('Unexpected qualification manifest')
    text = text.replace('</application>', '<activity android:name="io.github.asagnc.aurora.qualification.ApkSafQualificationActivity" android:exported="false" />\n    </application>')
    manifest.write_text(text, encoding='utf-8')
    report['patchedSourceHashes']['app/src/main/AndroidManifest.xml'] = hashlib.sha256(manifest.read_bytes()).hexdigest()
    report['apkSafFixture'] = {'sourceHashes': hashes, 'exported': False,
        'officialNetworkQualified': False, 'productionIntegrated': False,
        'preparerSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    evidence.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Prepared only the isolated unexported fixture')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-directory', type=Path, required=True)
    prepare(parser.parse_args().source_directory)
