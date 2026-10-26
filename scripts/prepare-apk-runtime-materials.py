#!/usr/bin/env python3
"""Fixed Ubuntu source and notice correspondence for the seven private ARM64 SDK runtime libraries. No source execution."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def collect(archive, required, destination):
    found = {}
    for item in archive:
        if item.name not in required:
            continue
        if item.name in found or not item.isfile() or not 0 < item.size <= 4 * 1024 ** 2:
            raise ValueError('Source notice must be one bounded regular file')
        data = archive.extractfile(item).read()
        found[item.name] = module('notice_store', 'prepare-proxy-gcc-materials.py').store(destination, item.name, data)
        if set(found) == set(required):
            break
    if set(found) != set(required):
        raise ValueError('Fixed source notice is missing')
    return list(found.values())


def native_correspondence(bundle, packages):
    pin = json.loads((ROOT / 'tools/apk-build/native-bundle.json').read_text())
    module('runtime_verify', 'verify-static-reverse-sources.py').verify(bundle, pin['sha256'], pin['bytes'])
    expected = {item['soname']: item for package in packages for item in package['libraries']}
    found = {}
    with tarfile.open(bundle, 'r|gz') as source:
        for member in source:
            name = member.name.removeprefix('android-host/runtime-lib/')
            if name not in expected:
                continue
            entry = expected[name]
            if name in found or not member.isfile() or member.size != entry['bytes']:
                raise ValueError('Bundled runtime shape differs')
            with source.extractfile(member) as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            if digest != entry['sha256']:
                raise ValueError('Bundled runtime bytes differ')
            found[name] = {'bytes': member.size, 'sha256': digest, 'sourcePackage': entry['sourcePackage']}
    if found.keys() != expected.keys():
        raise ValueError('A private runtime library is absent')
    return found


def prepare(directory, inputs, bundle):
    pin = json.loads((ROOT / 'tools/apk-build/runtime-materials.json').read_text())
    correspondence = native_correspondence(bundle, pin['packages'])
    directory.mkdir(parents=True, exist_ok=True); inputs.mkdir(parents=True, exist_ok=True)
    notices = directory / 'notices'; notices.mkdir(exist_ok=True)
    entries = [entry for package in pin['packages'] for entry in [package['descriptor'], *package['sources']]]
    paths = module('runtime_fetch', 'prepare-proxy-python-materials.py').acquire_inputs(entries, inputs)
    captured = []
    for package in pin['packages']:
        descriptor = paths[package['descriptor']['archive']].read_text()
        for field, value in [('Source', package['sourcePackage']), ('Version', package['sourceVersion'])]:
            if not re.search('^' + field + ': ' + re.escape(value) + '$', descriptor, re.M):
                raise ValueError('Source descriptor package identity differs')
        selected = next(item for item in package['sources'] if '.debian.tar.' in item['archive'])
        with tarfile.open(paths[selected['archive']], 'r|*') as archive:
            captured.extend(collect(archive, ['debian/copyright'], notices))
        selected = next(item for item in package['sources'] if '.orig.tar.' in item['archive'])
        with tarfile.open(paths[selected['archive']], 'r|*') as archive:
            if 'nestedSource' not in package:
                captured.extend(collect(archive, package['originalNotices'], notices))
                continue
            for member in archive:
                if member.name == package['nestedSource']:
                    if not member.isfile() or not 0 < member.size <= 128 * 1024 ** 2:
                        raise ValueError('Nested GCC source must be bounded regular data')
                    with tarfile.open(fileobj=archive.extractfile(member), mode='r|xz') as nested:
                        captured.extend(collect(nested, package['originalNotices'], notices))
                    break
            else:
                raise ValueError('Fixed nested GCC source is absent')
    report = {'inputs': pin, 'nativeByteCorrespondence': correspondence, 'notices': captured,
        'sourceBytes': sum(entry['bytes'] for entry in entries), 'licenseClosureVerified': False,
        'scope': 'Exact portable archive/runtime bytes, observed source-package versions, fixed Ubuntu source descriptors/originals/packaging and notices; not a bit-reproducible compiler or full NDK proof'}
    (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'nativeLibraries': len(correspondence), 'sourceInputs': len(entries), 'sourceBytes': report['sourceBytes'], 'notices': len(captured)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['directory', 'inputs', 'bundle']:
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args(); prepare(args.directory, args.inputs, args.bundle)
