#!/usr/bin/env python3
"""Collect pinned upstream source/notices and compare frozen native bytes; never import downloaded code."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import re
import tarfile
import tomllib
from urllib.parse import urlsplit
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'tools/network-proxy'


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def material_plan():
    pin = json.loads((CATALOG / 'upstream.json').read_text())
    raw = (CATALOG / 'upstream-uv.lock').read_bytes()
    if len(raw) != pin['lockBytes'] or hashlib.sha256(raw).hexdigest() != pin['lockSha256'] or \
            hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() != pin['lockGitBlobSha1']:
        raise ValueError('Upstream fixed source lock differs')
    locked = {item['name']: item for item in tomllib.loads(raw.decode())['package']}
    license_bytes = (CATALOG / 'mitmproxy-LICENSE').read_bytes()
    if hashlib.sha256(license_bytes).hexdigest() != pin['licenseSha256']:
        raise ValueError('Upstream license differs')
    plan = json.loads((CATALOG / 'source-materials.json').read_text())
    for package in plan['packages']:
        original = locked[package['name']]
        if package['version'] != original['version'] or package['sdist'] != original['sdist'] or \
                any(wheel not in original['wheels'] for wheel in package['wheels']):
            raise ValueError('Material is not pinned by the exact upstream lock')
    return pin, plan


def acquire(entry, directory):
    url = urlsplit(entry['url']); name = PurePosixPath(url.path).name
    if url.scheme != 'https' or url.hostname != 'files.pythonhosted.org' or url.username or url.password or url.query or url.fragment or \
            not re.fullmatch(r'[A-Za-z0-9_.+-]{1,200}', name) or not re.fullmatch(r'sha256:[a-f0-9]{64}', entry['hash']) or not 0 < entry['size'] <= 64 * 1024 ** 2:
        raise ValueError('Invalid pinned package transport')
    helper = module('material_fetch', 'verify-static-reverse-sources.py')
    retry = module('material_retry', 'prepare-frida-node.py')
    target = directory / name; digest = entry['hash'].removeprefix('sha256:')
    if not target.exists():
        retry.fetch_binding(helper, target, entry['url'], digest, entry['size'])
    helper.verify(target, digest, entry['size'])
    return target


def selected(name):
    base = PurePosixPath(name).name.lower()
    return base.startswith(('license', 'licence', 'copying', 'copyright', 'notice')) or base.endswith('.license') or base == 'cargo.lock'


def safe_name(name):
    parts = PurePosixPath(name).parts
    if not parts or '..' in parts or name.startswith('/') or '\\' in name or name.rstrip('/') != '/'.join(parts) or len(name) > 2048:
        raise ValueError('Unsafe source member name')


def notices(archive, directory):
    found = []; count = 0; total = 0; stored = 0
    with tarfile.open(archive) as source:
        for item in source:
            safe_name(item.name); count += 1; total += item.size
            if count > 50000 or total > 512 * 1024 ** 2:
                raise ValueError('Source archive exceeds audit budget')
            if not selected(item.name) or item.isdir():
                continue
            if not item.isfile() or item.size > 4 * 1024 ** 2:
                raise ValueError('Nonregular or oversized source notice')
            data = source.extractfile(item).read(); stored += len(data)
            if stored > 32 * 1024 ** 2:
                raise ValueError('Source notices exceed budget')
            digest = hashlib.sha256(data).hexdigest()
            suffix = '.lock' if PurePosixPath(item.name).name == 'Cargo.lock' else '.txt'
            target = directory / (digest + suffix)
            if target.is_symlink():
                raise ValueError('Notice content address is a link')
            if target.exists():
                if target.read_bytes() != data:
                    raise ValueError('Notice content address differs')
            else:
                with target.open('xb') as output:
                    output.write(data)
            found.append({'entry': item.name, 'bytes': len(data), 'sha256': digest, 'stored': target.name})
    return found


def native_matches(archive, embedded):
    matches = []; count = 0; total = 0
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            safe_name(item.filename); count += 1; total += item.file_size
            if count > 30000 or total > 128 * 1024 ** 2 or item.file_size > 32 * 1024 ** 2:
                raise ValueError('Wheel exceeds native audit budget')
            if item.is_dir():
                continue
            with source.open(item) as stream:
                if stream.read(4) != b'\x7fELF':
                    continue
            data = source.read(item); digest = hashlib.sha256(data).hexdigest()
            for native in embedded:
                if native['sha256'] == digest and native['bytes'] == len(data):
                    matches.append({'embedded': native['name'], 'wheelMember': item.filename, 'sha256': digest})
    return matches


def prepare(directory, audit):
    directory.mkdir(parents=True, exist_ok=True)
    pin, plan = material_plan(); audit = json.loads(audit.read_text())
    if audit['archiveSha256'] != json.loads((CATALOG / 'catalog.json').read_text())['sha256']:
        raise ValueError('Frozen audit belongs to another archive')
    versions = {package['name']: package['version'] for package in plan['packages']}
    for metadata in audit['packagedMetadata']:
        if metadata['entry'].startswith('setuptools/_vendor/'):
            continue
        name = re.sub(r'[-_.]+', '-', metadata['name']).lower()
        if versions.get(name) != metadata['version']:
            raise ValueError('Frozen distribution metadata disagrees with upstream source lock')
    entries = [entry for package in plan['packages'] for entry in [package['sdist']] + package['wheels']]
    if sum(entry['size'] for entry in entries) > 128 * 1024 ** 2:
        raise ValueError('Material download plan exceeds budget')
    if len({PurePosixPath(urlsplit(entry['url']).path).name for entry in entries}) != len(entries):
        raise ValueError('Conflicting material archive filenames')
    downloads = directory / 'downloads'; downloads.mkdir(exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as workers:
        archives = list(workers.map(lambda entry: acquire(entry, downloads), entries))
    paths = {entry['url']: path for entry, path in zip(entries, archives)}
    store = directory / 'notices'; store.mkdir(exist_ok=True)
    results = []
    for package in plan['packages']:
        found = notices(paths[package['sdist']['url']], store); matches = []
        for wheel in package['wheels']:
            for match in native_matches(paths[wheel['url']], audit['native']):
                matches.append({**match, 'wheelSha256': wheel['hash'].removeprefix('sha256:')})
        results.append({'package': package['name'], 'version': package['version'], 'sourceSha256': package['sdist']['hash'],
            'notices': found, 'nativeMatches': matches})
    matched = {item['embedded'] for package in results for item in package['nativeMatches']}
    report = {'upstream': pin, 'archiveSha256': audit['archiveSha256'], 'packages': results, 'sourcePackages': len(results), 'downloadBytes': sum(entry['size'] for entry in entries),
        'unmatchedNative': [item['name'] for item in audit['native'] if item['name'] not in matched],
        'licenseClosureVerified': False, 'staticRustOpenSslAndPythonClosure': 'PENDING', 'androidRuntime': 'NOT TESTED'}
    (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ['sourcePackages', 'downloadBytes', 'unmatchedNative', 'licenseClosureVerified']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--audit', type=Path, required=True)
    args = parser.parse_args(); prepare(args.directory, args.audit)
