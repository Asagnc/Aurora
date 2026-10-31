#!/usr/bin/env python3
"""Seal verified public metadata, notices and source inputs; never executes source or overwrites output."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import tempfile
import zipfile
import apk_dependency_cache as cache

ROOT = Path(__file__).resolve().parents[1]


def safe_name(name):
    if not name or any(part in {'', '.', '..'} or '\\' in part or ':' in part for part in name.split('/')):
        raise ValueError('Unsafe public material path')
    return name


def verify_file(root, row):
    path = root / safe_name(row['path'])
    current = root
    for part in Path(row['path']).parts:
        current = current / part
        if current.is_symlink():
            raise ValueError('Public source material links are forbidden')
    if not path.is_file() or path.stat().st_size != row['bytes'] or cache.digest(path) != row['sha256']:
        raise ValueError('Public source material digest differs: ' + row['path'])


def inventory(directory, pin):
    report_path = directory / 'report.json'
    if report_path.is_symlink() or report_path.stat().st_size > 8 * 1024 ** 2:
        raise ValueError('Invalid public source report')
    report = json.loads(report_path.read_text(encoding='utf-8'))
    if report['cachePin'] != pin or report['missingSources'] or report['missingLicenseDeclarations']:
        raise ValueError('Public sources or license declarations remain unresolved')
    coordinates = set(report['binaries'])
    if {row['coordinate'] for row in report['sources']} != coordinates or report['binaryCoordinates'] != len(coordinates):
        raise ValueError('Source coordinate coverage differs')
    rows = list(report['metadata']) + [row for row in report['sources'] if row.get('path')]
    for artifacts in report['binaries'].values():
        for artifact in artifacts:
            rows += artifact['notices']
    for name in ['report.json', 'ATTRIBUTIONS.txt']:
        path = directory / name
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 8 * 1024 ** 2:
            raise ValueError('Invalid public attribution/report file')
        rows.append({'path': name, 'bytes': path.stat().st_size, 'sha256': cache.digest(path)})
    selected = {safe_name(row['path']): {key: row[key] for key in ['path', 'bytes', 'sha256']} for row in rows}
    if len(selected) != len(rows) or not 1 <= len(rows) <= 5000 or sum(row['bytes'] for row in rows) > 512 * 1024 ** 2:
        raise ValueError('Duplicate or excessive public source inventory')
    actual = set()
    for path in directory.rglob('*'):
        if path.is_symlink() or not (path.is_dir() or path.is_file()):
            raise ValueError('Unexpected public source inode')
        if path.is_file():
            actual.add(path.relative_to(directory).as_posix())
    if actual != set(selected):
        raise ValueError('Unexpected or missing public source files')
    for row in selected.values():
        verify_file(directory, row)
    manifest_row = selected.get('cache-manifest.json')
    if not manifest_row or manifest_row['sha256'] != pin['manifestSha256']:
        raise ValueError('Public source baseline identity differs')
    return sorted(selected.values(), key=lambda row: row['path']), report


def verify_zip(path, pin):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or not 1 < len(names) <= 5001 or archive.getinfo('manifest.json').file_size > 2 * 1024 ** 2:
            raise ValueError('Invalid sealed public source inventory')
        manifest = json.loads(archive.read('manifest.json'))
        if manifest['format'] != 1 or manifest['cachePin'] != pin:
            raise ValueError('Sealed public sources belong to a different cache')
        rows = manifest['files']
        if not 1 <= len(rows) <= 5000 or len({row['path'] for row in rows}) != len(rows):
            raise ValueError('Duplicate or excessive sealed source records')
        if set(names) != {'manifest.json', *[safe_name(row['path']) for row in rows]}:
            raise ValueError('Missing or unexpected sealed source entries')
        total = 0
        for row in rows:
            entry = archive.getinfo(row['path']); total += entry.file_size
            if entry.is_dir() or stat.S_ISLNK(entry.external_attr >> 16) or entry.file_size != row['bytes'] or not 0 <= entry.file_size <= 256 * 1024 ** 2:
                raise ValueError('Sealed public source entry differs')
            with archive.open(entry) as incoming:
                if hashlib.file_digest(incoming, 'sha256').hexdigest() != row['sha256']:
                    raise ValueError('Sealed public source digest differs')
        if total > 512 * 1024 ** 2:
            raise ValueError('Sealed public source bytes exceed budget')
    return manifest


def package(args):
    pin = json.loads((ROOT / 'tools/apk-build/dependency-cache.json').read_text())
    rows, report = inventory(args.directory, pin)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    manifest = {'format': 1, 'cachePin': pin, 'files': rows, 'binaryCoordinates': report['binaryCoordinates'],
                'sourceArchives': report['sourcesFetched'], 'emptyBinaryCoordinates': report['emptyBinaryCoordinates'],
                'sourceLicenseClosureVerified': False}
    with tempfile.TemporaryDirectory(prefix='.apk-public-sources-', dir=args.output.parent) as temporary:
        path = Path(temporary) / 'materials.zip'
        with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_STORED) as archive:
            archive.writestr('manifest.json', json.dumps(manifest, sort_keys=True, indent=2) + '\n')
            for row in rows:
                verify_file(args.directory, row)
                info = zipfile.ZipInfo(row['path'], date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3; info.external_attr = (stat.S_IFREG | 0o444) << 16
                with (args.directory / row['path']).open('rb') as incoming, archive.open(info, 'w', force_zip64=True) as output:
                    while block := incoming.read(65536):
                        output.write(block)
        verify_zip(path, pin)
        os.link(path, args.output)
    print(json.dumps({'archive': args.output.name, 'sha256': cache.digest(args.output), 'bytes': args.output.stat().st_size,
        'files': len(rows), 'binaryCoordinates': report['binaryCoordinates'], 'sourceArchives': report['sourcesFetched'],
        'emptyBinaryCoordinates': report['emptyBinaryCoordinates'], 'missingSources': len(report['missingSources']),
        'missingLicenseDeclarations': len(report['missingLicenseDeclarations']), 'sourceLicenseClosureVerified': False}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    package(parser.parse_args())
