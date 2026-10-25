#!/usr/bin/env python3
"""Read-only byte correspondence and source/license material coverage for embedded CPython."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys
import tarfile
import urllib.error

ROOT = Path(__file__).resolve().parents[1]


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


class FullArchive:
    """Python 3.14's bounded member reads, or the existing Windows bsdtar in stdout-only mode."""
    def __init__(self, path):
        self.path = path
        self.archive = tarfile.open(path, 'r:zst') if sys.version_info >= (3, 14) else None
        if self.archive is None and os.name != 'nt':
            raise ValueError('Python3.14 zstd support required on the qualification host')

    def close(self):
        if self.archive is not None:
            self.archive.close()

    def command(self, arguments, limit):
        process = subprocess.Popen(['tar.exe'] + arguments, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        reader = ThreadPoolExecutor(max_workers=1)
        try:
            output = reader.submit(process.stdout.read, limit + 1).result(timeout=30)
            if len(output) > limit:
                raise ValueError('Python archive reader exceeds output budget')
            if process.wait(timeout=30) != 0:
                raise ValueError('Python archive member read failed')
            return output
        finally:
            if process.poll() is None:
                process.kill(); process.wait(timeout=10)
            reader.shutdown(wait=True); process.stdout.close()

    def names(self):
        names = self.archive.getnames() if self.archive is not None else self.command(['-tf', str(self.path)], 2 * 1024 ** 2).decode().splitlines()
        if len(names) > 100000:
            raise ValueError('Python full archive member count exceeds budget')
        return names

    def read(self, name):
        module('python_source_names', 'prepare-network-proxy-materials.py').safe_name(name)
        if self.archive is None:
            return self.command(['--fast-read', '-xOf', str(self.path), name], 4 * 1024 ** 2)
        item = self.archive.getmember(name)
        if not item.isfile() or item.size > 4 * 1024 ** 2:
            raise ValueError('Python metadata/notice member is not bounded regular data')
        return self.archive.extractfile(item).read()


def python_correspondence(archive, audit):
    native = next(item for item in audit['native'] if item['name'] == 'libpython3.14.so.1.0')
    with tarfile.open(archive) as source:
        item = source.getmember('python/lib/libpython3.14.so.1.0')
        if not item.isfile() or item.size != native['bytes'] or item.size > 64 * 1024 ** 2:
            raise ValueError('Python shared runtime shape differs')
        with source.extractfile(item) as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != native['sha256']:
            raise ValueError('Embedded Python does not byte-match fixed standalone runtime')
    return {'member': item.name, 'bytes': item.size, 'sha256': digest}


def required_notices(metadata, identity):
    required = {metadata['license_path']}
    for name in identity['builtinModules']:
        for extension in metadata['build_info']['extensions'].get(name, []):
            required.update(extension.get('license_paths', []))
    return required


def acquire_inputs(entries, inputs):
    helper = module('python_material_fetch', 'qualify-apk-jvm.py')
    def acquire(entry):
        failures = []
        for url in [entry['url']] + entry.get('mirrors', []):
            try:
                return helper.acquire({**entry, 'url': url}, inputs)
            except (urllib.error.URLError, ConnectionError, TimeoutError) as failure:
                failure.add_note('Fixed source URL: ' + url); failures.append(failure)
        raise ExceptionGroup('Fixed Python source transports failed', failures)
    paths = {}; failures = []
    with ThreadPoolExecutor(max_workers=4) as workers:
        pending = {workers.submit(acquire, entry): entry for entry in entries}
        for future in as_completed(pending):
            try:
                paths[pending[future]['archive']] = future.result()
            except Exception as failure:
                failures.append(failure)
    if failures:
        raise ExceptionGroup('Python material acquisition incomplete', failures)
    return paths


def prepare(directory, inputs, audit, identity):
    pin = json.loads((ROOT / 'tools/network-proxy/python-materials.json').read_text())
    directory.mkdir(parents=True, exist_ok=True); inputs.mkdir(parents=True, exist_ok=True)
    audit = json.loads(audit.read_text()); identity = json.loads(identity.read_text())
    catalog = json.loads((ROOT / 'tools/network-proxy/catalog.json').read_text())
    if audit['archiveSha256'] != catalog['sha256'] or not identity['python'].startswith(pin['version'] + ' '):
        raise ValueError('Python material inputs belong to another frozen runtime')
    entries = [pin['install'], pin['full']] + list(pin['sources'].values())
    if sum(item['bytes'] for item in entries) > 384 * 1024 ** 2:
        raise ValueError('Python source material exceeds total budget')
    paths = acquire_inputs(entries, inputs)
    matched = python_correspondence(paths[pin['install']['archive']], audit)
    store = directory / 'notices'; store.mkdir(exist_ok=True)
    source = module('python_source_notices', 'prepare-network-proxy-materials.py')
    sources = {}
    for name, entry in pin['sources'].items():
        sources[name] = {'pin': entry, 'notices': source.notices(paths[entry['archive']], store)}
    full = FullArchive(paths[pin['full']['archive']])
    try:
        metadata = json.loads(full.read('python/PYTHON.json'))
        if metadata['python_version'] != pin['version'] or metadata['target_triple'] != 'aarch64-unknown-linux-gnu':
            raise ValueError('Full Python build metadata differs')
        required = required_notices(metadata, identity); found = {}
        for name in full.names():
            if not name.startswith('python/licenses/') or name.endswith('/'):
                continue
            data = full.read(name); digest = hashlib.sha256(data).hexdigest(); stored = digest + '.txt'
            target = store / stored
            if target.is_symlink() or target.exists() and target.read_bytes() != data:
                raise ValueError('Python notice content address differs')
            if not target.exists():
                with target.open('xb') as output:
                    output.write(data)
            found[name.removeprefix('python/')] = {'entry': name, 'bytes': len(data), 'sha256': digest, 'stored': stored, 'origin': 'fixed_full_archive'}
        for name, item in sources.items():
            reference = 'licenses/' + item['pin']['licenseFile']
            if reference not in required or reference in found:
                continue
            candidates = [entry for entry in item['notices'] if PurePosixPath(entry['entry']).name.lower().startswith(('license', 'copying', 'copyright'))]
            if candidates:
                primary = min(candidates, key=lambda entry: (len(PurePosixPath(entry['entry']).parts), entry['entry']))
                found[reference] = {**primary, 'origin': 'fixed_source_archive', 'source': name}
        report = {'pythonByteCorrespondence': matched, 'inputs': pin, 'sourceMaterials': sources, 'notices': found,
            'missingReferencedNotices': sorted(required - found.keys()), 'observedStdlibOpenSsl': identity['stdlibOpenSsl'],
            'licenseClosureVerified': False, 'scope': 'Matched Python runtime and upstream recipe source/notice coverage; overall proxy closure remains separate'}
        (directory / 'build-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
        (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({'pythonByteCorrespondence': matched, 'sourceCount': len(sources), 'missingReferencedNotices': report['missingReferencedNotices']}))
    finally:
        full.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['directory', 'inputs', 'audit', 'identity']:
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args(); prepare(args.directory, args.inputs, args.audit, args.identity)
