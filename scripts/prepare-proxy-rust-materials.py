#!/usr/bin/env python3
"""Read-only fixed Cargo source/notices collection; never invokes cargo or a package build."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import http.client
import importlib.util
import json
from pathlib import Path
import re
import tarfile
import tomllib
import urllib.error
import urllib.request
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]


def download_once(crate, target):
    temporary = target.with_name(target.name + '.partial'); owned = False
    url = f'https://static.crates.io/crates/{crate["name"]}/{target.name}'
    try:
        with temporary.open('xb') as output:
            owned = True; count = 0; digest = hashlib.sha256()
            with urllib.request.urlopen(url, timeout=60) as incoming:
                declared = incoming.headers.get('Content-Length')
                if declared is not None and (not declared.isdigit() or not 0 < int(declared) <= 64 * 1024 ** 2):
                    raise ValueError('Crate declared size exceeds budget')
                while data := incoming.read(65536):
                    count += len(data)
                    if count > 64 * 1024 ** 2:
                        raise ValueError('Crate exceeds byte budget')
                    digest.update(data); output.write(data)
            if declared is not None and count != int(declared):
                raise ConnectionError('Incomplete crate HTTP body')
            if digest.hexdigest() != crate['sha256']:
                raise ValueError('Crate checksum differs from pinned source Cargo lock')
        temporary.replace(target)
    except BaseException as failure:
        if owned:
            try:
                temporary.unlink(missing_ok=True)
            except OSError as cleanup:
                failure.add_note('Owned partial cleanup failed: ' + str(cleanup))
        if isinstance(failure, http.client.IncompleteRead):
            raise ConnectionError('Incomplete crate download') from failure
        raise


def acquire(crate, directory):
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,100}', crate['name']) or not re.fullmatch(r'[A-Za-z0-9_.+-]{1,100}', crate['version']) or \
            not re.fullmatch(r'[a-f0-9]{64}', crate['sha256']):
        raise ValueError('Invalid locked crate identity')
    target = directory / f'{crate["name"]}-{crate["version"]}.crate'
    if target.is_symlink():
        raise ValueError('Crate cache path is a link')
    if not target.exists():
        for attempt in range(3):
            try:
                download_once(crate, target); break
            except urllib.error.HTTPError as failure:
                if attempt == 2 or failure.code not in {408, 429, 500, 502, 503, 504}:
                    raise
            except (urllib.error.URLError, ConnectionError, TimeoutError):
                if attempt == 2:
                    raise
    if target.is_symlink() or not target.is_file() or not 0 < target.stat().st_size <= 64 * 1024 ** 2:
        raise ValueError('Invalid cached crate file')
    with target.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != crate['sha256']:
            raise ValueError('Cached crate content differs')
    return target


def verify_locks(plan, materials):
    spec = importlib.util.spec_from_file_location('cargo_sources', ROOT / 'scripts/prepare-network-proxy-materials.py')
    source = importlib.util.module_from_spec(spec); spec.loader.exec_module(source)
    _, pinned = source.material_plan()
    packages = {item['name']: item for item in pinned['packages']}
    expected = {}
    for digest, origins in plan['sourceLocks'].items():
        raw = (materials / 'notices' / (digest + '.lock')).read_bytes()
        if hashlib.sha256(raw).hexdigest() != digest or not origins:
            raise ValueError('Source Cargo lock differs')
        for origin in origins:
            package = packages[origin['package']]
            if package['version'] != origin['version'] or package['sdist']['hash'] != origin['sourceSha256']:
                raise ValueError('Cargo origin differs from upstream Python source pin')
            archive = materials / 'downloads' / Path(urlsplit(package['sdist']['url']).path).name
            with archive.open('rb') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != package['sdist']['hash'].removeprefix('sha256:'):
                    raise ValueError('Cargo source archive differs')
            with tarfile.open(archive) as contents:
                member = contents.getmember(origin['entry'])
                if not member.isfile() or member.size > 4 * 1024 ** 2 or contents.extractfile(member).read() != raw:
                    raise ValueError('Cargo lock does not correspond to pinned source archive')
        for item in tomllib.loads(raw.decode())['package']:
            if not item.get('source'):
                continue
            if item['source'] != 'registry+https://github.com/rust-lang/crates.io-index':
                raise ValueError('Unexpected Cargo source backend')
            key = (item['name'], item['version'])
            if key in expected and expected[key] != item['checksum']:
                raise ValueError('Conflicting checksum in source Cargo locks')
            expected[key] = item['checksum']
    observed = {(item['name'], item['version']): item['sha256'] for item in plan['crates']}
    if len(observed) != len(plan['crates']) or observed != expected:
        raise ValueError('Crate plan differs from actual source Cargo locks')


def additional_notices(crate, store, archives, records):
    found = []
    for record in records:
        if record['package'] != crate['name'] or record['version'] != crate['version']:
            continue
        if record['file'] != record['sha256'] + '.txt' or not re.fullmatch(r'[a-f0-9]{64}', record['sha256']):
            raise ValueError('Unsafe supplementary notice reference')
        data = (ROOT / 'tools/network-proxy/licenses/rust' / record['file']).read_bytes()
        if len(data) > 1024 * 1024 or hashlib.sha256(data).hexdigest() != record['sha256']:
            raise ValueError('Supplementary notice bytes differ')
        origin = record['origin']
        if origin['type'] == 'git':
            if hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() != origin['blobSha1']:
                raise ValueError('Supplementary upstream Git blob differs')
        elif origin['type'] == 'same-project-crate':
            archive = archives[(origin['package'], origin['version'])]
            with archive.open('rb') as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != origin['sourceSha256']:
                    raise ValueError('Project license source differs')
            with tarfile.open(archive) as source:
                member = source.getmember(origin['path'])
                if not member.isfile() or member.size > 1024 * 1024 or source.extractfile(member).read() != data:
                    raise ValueError('Project notice does not match source crate')
        else:
            raise ValueError('Unknown supplementary notice source')
        target = store / record['file']
        if target.is_symlink() or target.exists() and target.read_bytes() != data:
            raise ValueError('Existing supplementary notice differs')
        if not target.exists():
            with target.open('xb') as output:
                output.write(data)
        found.append({'entry': origin['path'], 'bytes': len(data), 'sha256': record['sha256'], 'stored': target.name,
            'origin': origin, 'method': 'upstream_project_notice_supplement'})
    return found


def prepare(directory, materials):
    plan = json.loads((ROOT / 'tools/network-proxy/rust-materials.json').read_text())
    verify_locks(plan, materials)
    directory.mkdir(parents=True, exist_ok=True)
    downloads = directory / 'downloads'; downloads.mkdir(exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as workers:
        paths = list(workers.map(lambda crate: acquire(crate, downloads), plan['crates']))
    total = sum(path.stat().st_size for path in paths)
    if total > 512 * 1024 ** 2:
        raise ValueError('Rust source material exceeds total audit budget')
    spec = importlib.util.spec_from_file_location('source_notices', ROOT / 'scripts/prepare-network-proxy-materials.py')
    source = importlib.util.module_from_spec(spec); spec.loader.exec_module(source)
    store = directory / 'notices'; store.mkdir(exist_ok=True); results = []
    supplements = json.loads((ROOT / 'tools/network-proxy/rust-extra-notices.json').read_text())['notices']
    archives = {(item['name'], item['version']): path for item, path in zip(plan['crates'], paths)}
    for crate, path in zip(plan['crates'], paths):
        notices = source.notices(path, store) + additional_notices(crate, store, archives, supplements)
        with tarfile.open(path) as archive:
            member = archive.getmember(f'{crate["name"]}-{crate["version"]}/Cargo.toml')
            if not member.isfile() or member.size > 1024 * 1024:
                raise ValueError('Invalid crate metadata')
            metadata = tomllib.loads(archive.extractfile(member).read().decode())['package']
        if metadata['name'] != crate['name'] or metadata['version'] != crate['version']:
            raise ValueError('Crate package metadata differs from lock')
        results.append({**crate, 'bytes': path.stat().st_size, 'license': metadata.get('license'), 'licenseFile': metadata.get('license-file'),
            'authors': metadata.get('authors', []), 'notices': notices})
    report = {'crates': results, 'sourceBytes': total, 'crateCount': len(results),
        'withoutCapturedNotices': [item['name'] + '@' + item['version'] for item in results if not item['notices']],
        'licenseClosureVerified': False, 'scope': 'Cargo-lock source superset, not a measured linked-crate graph'}
    (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ['crateCount', 'sourceBytes', 'withoutCapturedNotices', 'licenseClosureVerified']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--materials', type=Path, required=True)
    args = parser.parse_args(); prepare(args.directory, args.materials)
