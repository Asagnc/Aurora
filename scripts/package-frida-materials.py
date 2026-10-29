#!/usr/bin/env python3
"""Package exact source materials and the official relink archive. No APK/ready claim."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def builder():
    spec = importlib.util.spec_from_file_location('frida_builder', ROOT / 'scripts/build-frida-probe.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def source_validator():
    spec = importlib.util.spec_from_file_location('source_materials', ROOT / 'scripts/prepare-frida-sources.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def extra_sources():
    paths = ['tools/frida-sources/catalog.json', 'scripts/prepare-frida-node.py']
    paths += ['tools/frida-bridge/' + name for name in ['entrypoint.js', 'package.json', 'package-lock.json', 'bundle-inputs.json', 'README.md']]
    paths += ['tools/frida-licenses/sources.json']
    for entry in json.loads((ROOT / 'tools/frida-licenses/sources.json').read_text())['files']:
        paths.append('tools/frida-licenses/' + entry['file'])
    return {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in paths}


def add_checked(archive, source, name, expected):
    if source.is_symlink() or not source.is_file():
        raise ValueError('Expected ordinary pinned material')
    digest = hashlib.sha256()
    with source.open('rb') as incoming, archive.open(name, 'w', force_zip64=True) as outgoing:
        while block := incoming.read(65536):
            digest.update(block)
            outgoing.write(block)
    if digest.hexdigest() != expected:
        raise ValueError('Material changed during packaging: ' + name)


def package(args):
    build = builder()
    reference = json.loads(args.reference.read_text(encoding='utf-8'))
    if reference['sources'] != build.source_manifest():
        raise ValueError('Rebuild qualification after changing its inputs')
    reports = [json.loads((directory / 'materials.json').read_text(encoding='utf-8')) for directory in args.materials]
    if any(report['failures'] for report in reports):
        raise ValueError('Source acquisition has unresolved failures')
    entries = [(directory, entry) for directory, report in zip(args.materials, reports) for entry in report['repositories']]
    if len(entries) > 128 or len({entry['id'] for _, entry in entries}) != len(entries):
        raise ValueError('Duplicate or excessive source materials')
    catalog = json.loads((ROOT / 'tools/frida-sources/catalog.json').read_text(encoding='utf-8'))
    validator = source_validator()
    expected = {entry['id']: validator.material_identity(entry) for entry in catalog['repositories']}
    if {entry['id']: validator.material_identity(entry) for _, entry in entries} != expected:
        raise ValueError('Source materials differ from the trusted complete input catalogue')
    pins = {entry['id']: entry for entry in catalog['repositories']}
    for directory, entry in entries:
        pin = pins[entry['id']]
        _, prefixed = validator.location(pin)
        validator.inspect(directory / entry['archive'], pin, prefixed)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    partial = args.output.with_suffix('.partial')
    owned = False
    try:
        with partial.open('xb') as stream:
            owned = True
            with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_STORED) as archive:
                for name, digest in reference['sources'].items():
                    add_checked(archive, ROOT / name, name, digest)
                extra = extra_sources()
                for name, digest in extra.items():
                    add_checked(archive, ROOT / name, name, digest)
                add_checked(archive, args.devkit, 'inputs/' + build.NAME, build.DIGEST)
                for directory, entry in entries:
                    name = entry['archive']
                    if Path(name).name != name:
                        raise ValueError('Unsafe material filename')
                    add_checked(archive, directory / name, 'sources/' + name, entry['archiveSha256'])
                archive.writestr('qualification.json', json.dumps(reference, indent=2) + '\n')
                archive.writestr('source-materials.json', json.dumps({'sourceClosureVerified': False,
                    'scope': 'Pinned upstream source superset plus toolchain runtimes; SDK rebuild and full notice closure still require review',
                    'extraSources': extra, 'repositories': [entry for _, entry in entries]}, indent=2) + '\n')
                archive.writestr('REBUILD.txt', 'Use the existing NDK30; no Android installation is performed.\n'
                    'Copy inputs/frida-core-devkit-17.24.3-android-arm64.tar.xz into an empty output directory.\n'
                    'Also copy sources/selinux-507985d931eb1f0227218d5d845634e602426c20.tar.gz into that output directory.\n'
                    'Run: python scripts/build-frida-probe.py --directory OUTPUT --ndk PATH_TO_NDK30\n'
                    'No --download option is needed. The official devkit contains the linkable static archive.\n'
                    'Source archives preserve their original repository layout and notices; nested gitlinks are listed in source-materials.json.\n'
                    'This relinks the Aurora worker. It does not rebuild the upstream SDK or prove Android runtime qualification.\n')
        partial.replace(args.output)
    except BaseException:
        if owned:
            partial.unlink(missing_ok=True)
        raise
    with args.output.open('rb') as incoming:
        digest = hashlib.file_digest(incoming, 'sha256').hexdigest()
    print(json.dumps({'archive': str(args.output), 'sha256': digest, 'bytes': args.output.stat().st_size,
                      'sourceArchives': len(entries), 'ownedSources': len(reference['sources'])}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--materials', type=Path, action='append', required=True)
    parser.add_argument('--devkit', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    package(parser.parse_args())
