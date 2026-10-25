#!/usr/bin/env python3
"""Read-only exact Debian libgcc correspondence, source and runtime exception material audit."""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def deb_data(path):
    raw = path.read_bytes()
    if len(raw) > 1024 ** 2 or raw[:8] != b'!<arch>\n':
        raise ValueError('Invalid bounded Debian archive')
    offset = 8; found = None; names = set()
    while offset < len(raw):
        header = raw[offset:offset + 60]
        if len(header) != 60 or header[58:] != b'`\n' or not header[48:58].strip().isdigit():
            raise ValueError('Invalid Debian member header')
        size = int(header[48:58]); name = header[:16].decode('ascii').strip().rstrip('/')
        if name in names or offset + 60 + size > len(raw):
            raise ValueError('Duplicate or truncated Debian member')
        names.add(name)
        if name.startswith('data.tar.'):
            if found is not None:
                raise ValueError('Multiple Debian payloads')
            found = raw[offset + 60:offset + 60 + size]
        offset += 60 + size + size % 2
    if offset != len(raw) or found is None:
        raise ValueError('Missing or truncated Debian payload')
    return tarfile.open(fileobj=io.BytesIO(found))


def member(archive, name, budget):
    entries = [item for item in archive if item.name == name]
    if len(entries) != 1 or not entries[0].isfile() or not 0 < entries[0].size <= budget:
        raise ValueError('Expected one bounded regular source member: ' + name)
    return archive.extractfile(entries[0]).read()


def store(directory, name, data):
    digest = hashlib.sha256(data).hexdigest(); target = directory / (digest + '.txt')
    if target.is_symlink() or target.exists() and target.read_bytes() != data:
        raise ValueError('Notice content address differs')
    if not target.exists():
        with target.open('xb') as output:
            output.write(data)
    return {'entry': name, 'bytes': len(data), 'sha256': digest, 'stored': target.name}


def prepare(directory, inputs, audit):
    pin = json.loads((ROOT / 'tools/network-proxy/gcc-materials.json').read_text())
    native = next(item for item in json.loads(audit.read_text())['native'] if item['name'] == 'libgcc_s.so.1')
    if native['sha256'] != pin['elfSha256']:
        raise ValueError('GCC plan belongs to another embedded runtime')
    inputs.mkdir(parents=True, exist_ok=True); directory.mkdir(parents=True, exist_ok=True)
    notices = directory / 'notices'; notices.mkdir(exist_ok=True)
    fetch = module('gcc_fixed_fetch', 'prepare-proxy-python-materials.py')
    paths = fetch.acquire_inputs(list(pin['inputs'].values()), inputs)
    paths = {key: paths[entry['archive']] for key, entry in pin['inputs'].items()}
    with deb_data(paths['runtime']) as archive:
        binary = member(archive, './lib/aarch64-linux-gnu/libgcc_s.so.1', 1024 ** 2)
    if len(binary) != native['bytes'] or hashlib.sha256(binary).hexdigest() != native['sha256']:
        raise ValueError('Debian libgcc does not byte-match frozen runtime')
    with deb_data(paths['base']) as archive:
        copyright = member(archive, './usr/share/doc/gcc-12-base/copyright', 1024 ** 2)
    with tarfile.open(paths['debian']) as archive:
        if copyright != member(archive, 'debian/copyright', 1024 ** 2):
            raise ValueError('Binary and source Debian copyright differ')
    captured = [store(notices, 'debian/copyright', copyright)]
    with tarfile.open(paths['original']) as archive:
        nested = member(archive, 'gcc-12-12.2.0/gcc-12.2.0-dfsg.tar.xz', 96 * 1024 ** 2)
    with tarfile.open(fileobj=io.BytesIO(nested)) as archive:
        for name in ['COPYING3', 'COPYING.RUNTIME', 'libgcc/libgcc2.c']:
            data = member(archive, 'gcc-12.2.0/' + name, 4 * 1024 ** 2)
            if name == 'libgcc/libgcc2.c':
                if b'GCC Runtime Library Exception' not in data:
                    raise ValueError('Runtime source exception attribution missing')
            else:
                captured.append(store(notices, name, data))
    report = {'inputs': pin, 'nativeByteCorrespondence': {'name': native['name'], 'bytes': len(binary), 'sha256': native['sha256']},
        'notices': captured, 'binarySourceCopyrightCorrespondence': True, 'licenseClosureVerified': False,
        'scope': 'Exact Debian runtime bytes, original source, Debian modifications and runtime exception; overall proxy closure is separate'}
    (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'nativeByteCorrespondence': report['nativeByteCorrespondence'], 'noticeCount': len(captured)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['directory', 'inputs', 'audit']:
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args(); prepare(args.directory, args.inputs, args.audit)
