#!/usr/bin/env python3
"""Build an isolated approved mapper candidate; never edits production pins or touches a device."""
import argparse
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import tarfile
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]


def builder():
    spec = importlib.util.spec_from_file_location('unchanged_frida_builder', ROOT / 'scripts/build-frida-probe.py')
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


def extract_candidate(archive, directory, pin):
    expected = {row['name']: row for row in pin['members']}
    with tarfile.open(archive) as source:
        entries = source.getmembers()
        if len(entries) != len(expected) or {row.name for row in entries} != set(expected):
            raise ValueError('Candidate devkit inventory differs')
        if sum(row.size for row in entries) != pin['expandedBytes']:
            raise ValueError('Candidate devkit expanded byte count differs')
        for row in entries:
            if row.size != expected[row.name]['bytes'] or row.issparse():
                raise ValueError('Candidate devkit member differs')
            if row.name == '.':
                if not row.isdir():
                    raise ValueError('Candidate devkit root is not a directory')
                continue
            if not row.isfile():
                raise ValueError('Candidate devkit links or special entries are forbidden')
            if row.name in {'./frida-core.h', './libfrida-core.a'}:
                with source.extractfile(row) as incoming, (directory / row.name[2:]).open('xb') as output:
                    shutil.copyfileobj(incoming, output, 65536)


def qualify(args):
    pin = json.loads((ROOT / 'tools/frida-mapper-qualification/candidate.json').read_text())
    if pin['version'] != '17.25.0' or pin['productionIntegrated']:
        raise ValueError('Only the approved isolated candidate is allowed')
    output = args.directory.resolve(); output.mkdir(parents=True, exist_ok=False)
    legacy = builder()
    archive = output / pin['archive']
    report = {'version': pin['version'], 'sdk': pin, 'hostBuildPassed': False, 'phoneRuntimeQualified': False,
              'productionIntegrated': False, 'globalPolicyModified': False,
              'scope': 'Existing worker against one new devkit, isolated build/ELF/resource compatibility only',
              'candidateAdapterSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    try:
        helpers = legacy.source_helpers()
        if args.archive:
            helpers.verify(args.archive, pin['sha256'], pin['bytes'])
            shutil.copyfile(args.archive, archive)
        elif args.download:
            helpers.fetch(archive, pin['url'], pin['sha256'], pin['bytes'])
        helpers.verify(archive, pin['sha256'], pin['bytes'])
        # This changes only the imported module's local report metadata. Its
        # source, production archive pin and compiler/source selection stay intact.
        legacy.DIGEST = pin['sha256']
        legacy.extract = lambda source, directory: extract_candidate(source, directory, pin)
        with contextlib.redirect_stdout(io.StringIO()):
            legacy.build(SimpleNamespace(ndk=args.ndk, download=args.download), archive, output)
        built = json.loads((output / 'report.json').read_text(encoding='utf-8'))
        report['legacyBuilderMetadataVersion'] = built.pop('version')
        built['version'] = pin['version']
        built['scope'] = 'Candidate devkit pin determines linked SDK; actual runtime version still requires a phone probe'
        (output / 'report.json').write_text(json.dumps(built, indent=2) + '\n', encoding='utf-8')
        report['build'] = built
        report['hostBuildPassed'] = True
        report['legacyPolicySymbolScope'] = 'Expected private names are linkable; structure ABI and policy runtime remain unqualified'
    except Exception as failure:
        report['failureType'] = type(failure).__name__
        report['failure'] = str(failure)
        raise
    finally:
        (output / 'qualification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--download', action='store_true')
    parser.add_argument('--archive', type=Path)
    qualify(parser.parse_args())
