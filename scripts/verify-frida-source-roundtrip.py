#!/usr/bin/env python3
"""Offline Aurora worker relink from source materials; not an upstream SDK rebuild."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify(args):
    spec = importlib.util.spec_from_file_location('frida_build', ROOT / 'scripts/build-frida-probe.py')
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    reference = json.loads(args.reference.read_text(encoding='utf-8'))
    if reference['sources'] != builder.source_manifest():
        raise ValueError('Qualification differs from trusted current source tree')
    with tempfile.TemporaryDirectory(prefix='aurora-frida-relink-') as temporary:
        work = Path(temporary)
        with zipfile.ZipFile(args.archive) as archive:
            if len(archive.namelist()) != len(set(archive.namelist())):
                raise ValueError('Duplicate source entries')
            for name, digest in reference['sources'].items():
                entry = archive.getinfo(name)
                if entry.file_size > 1048576 or stat.S_IFMT(entry.external_attr >> 16) not in (0, stat.S_IFREG):
                    raise ValueError('Unsafe owned source')
                data = archive.read(entry)
                if hashlib.sha256(data).hexdigest() != digest:
                    raise ValueError('Source differs from qualification: ' + name)
                target = work / name
                target.resolve().relative_to(work)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            # No archive-provided build code runs unless it matches the current trusted tree.
            for name in reference['sources']:
                if (work / name).read_bytes() != (ROOT / name).read_bytes():
                    raise ValueError('Archive source differs from trusted checkout')
            output = work / 'output'
            output.mkdir()
            devkit = archive.getinfo('inputs/' + builder.NAME)
            if devkit.file_size != builder.SIZE:
                raise ValueError('Wrong official relink archive size')
            with archive.open(devkit) as incoming, (output / builder.NAME).open('xb') as outgoing:
                shutil.copyfileobj(incoming, outgoing, 65536)
            materials = json.loads(archive.read('source-materials.json'))
            package_spec = importlib.util.spec_from_file_location('source_package', ROOT / 'scripts/package-frida-materials.py')
            package = importlib.util.module_from_spec(package_spec)
            package_spec.loader.exec_module(package)
            extra = package.extra_sources()
            if materials['extraSources'] != extra:
                raise ValueError('Bridge build/source companion files differ from trusted inputs')
            for name, digest in extra.items():
                if hashlib.sha256(archive.read(name)).hexdigest() != digest:
                    raise ValueError('Bridge build/source companion file changed')
            catalog = json.loads((ROOT / 'tools/frida-sources/catalog.json').read_text(encoding='utf-8'))
            source_spec = importlib.util.spec_from_file_location('source_materials', ROOT / 'scripts/prepare-frida-sources.py')
            validator = importlib.util.module_from_spec(source_spec)
            source_spec.loader.exec_module(validator)
            expected = {entry['id']: validator.material_identity(entry) for entry in catalog['repositories']}
            if {entry['id']: validator.material_identity(entry) for entry in materials['repositories']} != expected:
                raise ValueError('Source materials differ from current trusted catalogue')
            pins = {entry['id']: entry for entry in catalog['repositories']}
            for entry in materials['repositories']:
                name = 'sources/' + entry['archive']
                if Path(entry['archive']).name != entry['archive']:
                    raise ValueError('Unsafe source material name')
                with archive.open(name) as incoming:
                    actual = hashlib.file_digest(incoming, 'sha256').hexdigest()
                if actual != entry['archiveSha256']:
                    raise ValueError('Source archive digest mismatch')
                if 'contentSha256' in pins[entry['id']]:
                    packed = work / 'source-input.tar.gz'
                    with archive.open(name) as incoming, packed.open('wb') as outgoing:
                        shutil.copyfileobj(incoming, outgoing, 65536)
                    validator.inspect(packed, pins[entry['id']], False)
                    packed.unlink()
        subprocess.run([sys.executable, str(work / 'scripts/build-frida-probe.py'), '--directory', str(output),
                        '--ndk', str(args.ndk.resolve())], capture_output=True, check=True)
        actual = json.loads((output / 'report.json').read_text(encoding='utf-8'))
        if actual['workerSha256'] != reference['workerSha256'] or actual['sources'] != reference['sources']:
            raise ValueError('Offline worker relink differs from qualification')
    with args.archive.open('rb') as incoming:
        digest = hashlib.file_digest(incoming, 'sha256').hexdigest()
    report = {'offlineWorkerRelink': True, 'upstreamSdkRebuilt': False, 'androidRuntime': 'NOT TESTED',
              'sourceArchiveSha256': digest, 'workerSha256': reference['workerSha256'],
              'workerBytes': reference['strippedBytes'], 'sourceArchives': len(materials['repositories'])}
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    verify(parser.parse_args())
