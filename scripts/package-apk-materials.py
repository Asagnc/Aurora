#!/usr/bin/env python3
"""Package fixed SDK source inputs and an exact own-source snapshot. Never executes source or overwrites output."""
import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import zipfile
from apk_release_materials import ROOT, entries, sha, snapshot, verify_file, verify_sources


def add_checked(archive, source, row):
    spec = importlib.util.spec_from_file_location('fixed_packager', ROOT / 'scripts/package-frida-materials.py')
    helper = importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)
    verify_file(source, row)
    helper.add_checked(archive, source, row['entry'], row['sha256'])


def package(args):
    output = args.output.resolve()
    if output.exists() or output.is_symlink():
        raise FileExistsError('Preserve existing SDK source output')
    directories = {key: getattr(args, key).resolve() for key in ['resources', 'compiler', 'runtime', 'host']}
    expected = entries()
    for row in expected:
        verify_file(directories[row['group']] / row['archive'], row)
    with tempfile.TemporaryDirectory(prefix='.aurora-sdk-materials-', dir=output.parent) as temporary:
        own = Path(temporary) / 'aurora-source.zip'; snapshot(own)
        manifest = {'format': 1, 'bundle': json.loads((ROOT / 'tools/apk-build/native-bundle.json').read_text()),
            'inputs': expected, 'auroraSource': {'bytes': own.stat().st_size, 'sha256': sha(own)},
            'scope': 'Fixed native SDK source and notices, not complete Android-patched NDK equivalence or bit-reproducible toolchain proof'}
        selected = Path(temporary) / 'sources.zip'
        with zipfile.ZipFile(selected, 'w', compression=zipfile.ZIP_STORED) as archive:
            for row in expected:
                add_checked(archive, directories[row['group']] / row['archive'], row)
            add_checked(archive, own, dict(manifest['auroraSource'], entry='aurora-source.zip'))
            archive.writestr('manifest.json', json.dumps(manifest, indent=2) + '\n')
        verify_sources(selected)
        # Exclusive publication preserves other invocations' outputs; a failed copy removes only this file.
        created = False
        try:
            with output.open('xb') as destination, selected.open('rb') as source:
                created = True
                while block := source.read(65536):
                    destination.write(block)
        except BaseException:
            if created:
                output.unlink(missing_ok=True)
            raise
    print(json.dumps({'output': str(output), 'sourceInputs': len(expected), 'bytes': output.stat().st_size, 'sha256': sha(output)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['resources', 'compiler', 'runtime', 'host', 'output']:
        parser.add_argument('--' + name, type=Path, required=True)
    package(parser.parse_args())
