#!/usr/bin/env python3
"""Package fixed proxy inputs and exact own-source snapshot; never download or execute source."""
import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import zipfile
from proxy_release_materials import ROOT, entries, load, profiles, sha, snapshot, verify_file, verify_sources


def add_checked(archive, source, row):
    verify_file(source, row)
    spec = importlib.util.spec_from_file_location('proxy_fixed_packager', ROOT / 'scripts/package-frida-materials.py')
    helper = importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)
    helper.add_checked(archive, source, row['entry'], row['sha256'])


def package(args):
    output = args.output.resolve()
    if output.exists() or output.is_symlink():
        raise FileExistsError('Preserve existing proxy source output')
    directories = {key: getattr(args, key).resolve() for key in ['upstream', 'packages', 'rust', 'python', 'gcc', 'crypto']}
    expected = entries()
    for row in expected:
        verify_file(directories[row['group']] / row['archive'], row)
    with tempfile.TemporaryDirectory(prefix='.aurora-proxy-materials-', dir=output.parent) as temporary:
        own = Path(temporary) / 'aurora-source.zip'; snapshot(own)
        manifest = dict(format=1, distribution=load('catalog.json'), profiles=profiles(), inputs=expected,
            auroraSource=dict(bytes=own.stat().st_size, sha256=sha(own)), licenseClosureVerified=False)
        selected = Path(temporary) / 'sources.zip'
        with zipfile.ZipFile(selected, 'w', compression=zipfile.ZIP_STORED) as archive:
            for row in expected:
                add_checked(archive, directories[row['group']] / row['archive'], row)
            add_checked(archive, own, dict(manifest['auroraSource'], entry='aurora-source.zip'))
            archive.writestr('manifest.json', json.dumps(manifest, indent=2) + '\n')
        verify_sources(selected)
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
    print(json.dumps(dict(output=str(output), sourceInputs=len(expected), bytes=output.stat().st_size, sha256=sha(output))))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['upstream', 'packages', 'rust', 'python', 'gcc', 'crypto', 'output']:
        parser.add_argument('--' + name, type=Path, required=True)
    package(parser.parse_args())
