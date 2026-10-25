#!/usr/bin/env python3
"""Verify the approved official archive and embedded native payloads without executing code."""
import argparse
from email.parser import Parser
import hashlib
import importlib.util
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def prepare(directory):
    pin = json.loads((ROOT / 'tools/network-proxy/catalog.json').read_text())
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / pin['archive']
    helper = module('inputs', 'verify-static-reverse-sources.py')
    retry = module('transport', 'prepare-frida-node.py')
    if not target.exists():
        retry.fetch_binding(helper, target, pin['url'], pin['sha256'], pin['bytes'])
    helper.verify(target, pin['sha256'], pin['bytes'])
    parser = module('bundle', 'audit-python-bundle.py')
    outer = []; selected = None
    with tarfile.open(target) as archive:
        entries = archive.getmembers()
        if len(entries) != 3 or {entry.name for entry in entries} != set(pin['members']):
            raise ValueError('Unexpected standalone archive inventory')
        for entry in entries:
            expected = pin['members'][entry.name]
            if not entry.isfile() or entry.size != expected['bytes']:
                raise ValueError('Unexpected archive member type or length')
            data = archive.extractfile(entry).read()
            if hashlib.sha256(data).hexdigest() != expected['sha256']:
                raise ValueError('Standalone executable differs from fixed observation')
            outer.append({'name': entry.name, **parser.elf(data)})
            if entry.name == pin['selected']:
                selected = data
    metadata, contents = parser.archive(selected)
    native = []; licenses = []; packages = []; total = 0; modules = []
    for entry in contents:
        data = parser.content(selected, entry); name = entry[0]; total += len(data)
        if name == 'PYZ.pyz':
            modules = parser.python_modules(data)
        if data.startswith(b'\x7fELF'):
            native.append({'name': name, **parser.elf(data)})
        if name.lower().split('/')[-1].startswith(('license', 'copying', 'notice', 'copyright')):
            if len(data) > 2_097_152:
                raise ValueError('Oversized embedded notice')
            licenses.append({'name': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
        if name.endswith('.dist-info/METADATA'):
            if len(data) > 2_097_152:
                raise ValueError('Oversized package metadata')
            package = Parser().parsestr(data.decode('utf-8'))
            packages.append({'name': package.get('Name'), 'version': package.get('Version'), 'entry': name})
    report = {'version': pin['version'], 'archiveSha256': pin['sha256'], 'selected': pin['selected'], **metadata,
        'embeddedEntries': len(contents), 'embeddedBytes': total, 'outer': outer, 'native': native,
        'packagedNotices': licenses, 'packagedMetadata': packages,
        'pythonModules': modules, 'pythonTopLevelModules': sorted({name.split('.')[0] for name in modules}),
        'runtimeQualified': False, 'licenseClosureVerified': False, 'androidRuntime': 'NOT TESTED'}
    (directory / 'archive-audit.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: report[key] for key in ['version', 'embeddedEntries', 'embeddedBytes', 'pythonVersion', 'runtimeQualified', 'licenseClosureVerified']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    prepare(parser.parse_args().directory)
