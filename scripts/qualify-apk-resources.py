#!/usr/bin/env python3
"""Build pinned resource-tool sources on a real ARM64 host, without publishing a phone bundle."""
import argparse
import concurrent.futures
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def source_helper():
    spec = importlib.util.spec_from_file_location('source_audit', ROOT / 'scripts/prepare-frida-sources.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def unpack(entry, archive, work):
    destination = entry['destination']
    if destination != '.' and (destination.startswith('/') or '..' in destination.split('/') or '\\' in destination):
        raise ValueError('Unsafe source destination')
    prefix = entry['repository'].startswith('https://github.com/')
    skipped = []
    with tarfile.open(archive) as source:
        for item in source:
            parts = PurePosixPath(item.name).parts
            if prefix:
                parts = parts[1:]
            if not parts:
                continue
            if item.issym() or item.islnk():
                # Formatting/compatibility links are not needed by this build. Original archives
                # remain unchanged; any required missing include must fail compilation explicitly.
                skipped.append('/'.join(parts))
                continue
            target = work / destination / Path(*parts)
            target.resolve().relative_to(work.resolve())
            if item.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif item.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(item) as incoming, target.open('xb') as output:
                    shutil.copyfileobj(incoming, output, 65536)
                target.chmod(0o755 if item.mode & 0o111 else 0o644)
            else:
                raise ValueError('Unexpected source member')
    return {'source': entry['id'], 'linksNotMaterialized': skipped}


def run(argv, log, timeout=900, cwd=None):
    with log.open('ab') as output:
        result = subprocess.run(list(map(str, argv)), stdout=output, stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)
    result.check_returncode()


def qualify(args):
    if platform.system() != 'Linux' or platform.machine().lower() not in {'aarch64', 'arm64'}:
        raise ValueError('Requires a real Linux ARM64 host')
    directory = args.directory.resolve(); directory.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((ROOT / 'tools/apk-build/sources.json').read_text())
    sources = source_helper()
    downloads = directory / 'sources'; downloads.mkdir(exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(sources.acquire_with_retries, entry, downloads) for entry in catalog['repositories']]
        acquired = [future.result() for future in futures]
    (directory / 'materials.json').write_text(json.dumps(acquired, indent=2) + '\n')
    with tempfile.TemporaryDirectory(prefix='.resource-build-', dir=directory) as temporary:
        work = Path(temporary); skipped = []
        for entry in acquired:
            skipped.append(unpack(entry, downloads / entry['archive'], work))
        log = directory / 'build.txt'
        for patch in sorted((work / 'patches').glob('*/*.patch')):
            text = patch.read_text()
            names = re.findall(r'^\+\+\+ b/(.+)$', text, re.M)
            if not names or any(name.startswith('/') or '..' in name.split('/') or '\\' in name for name in names):
                raise ValueError('Unsafe source patch path')
            run(['patch', '--batch', '--forward', '--fuzz=0', '-p1', '-i', patch], log,
                cwd=work / 'vendor' / patch.parent.name)
        # Disable upstream git/submodule updates and implicit patching; all inputs above are pinned.
        build = work / 'build'
        run(['cmake', '-S', work, '-B', build, '-G', 'Ninja', '-DCMAKE_BUILD_TYPE=Release',
             '-DANDROID_BUILD_TOOLS_PATCH_VENDOR=OFF'], log)
        run(['cmake', '--build', build, '--target', 'aapt2', 'aidl', 'zipalign', '-j', '2'], log)
        candidates = [build / 'vendor' / name for name in ['aapt2', 'aidl', 'zipalign']]
        reports = []
        for binary in candidates:
            header = binary.read_bytes()[:64]
            if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 183:
                raise ValueError('Resource tool is not native ARM64 ELF')
            result = subprocess.run(['ldd', str(binary)], capture_output=True, text=True, check=True)
            if 'not found' in result.stdout:
                raise ValueError('Resource tool has missing dynamic dependencies')
            with binary.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            reports.append({'tool': binary.name, 'bytes': binary.stat().st_size, 'sha256': digest, 'ldd': result.stdout})
        run([candidates[0], 'version'], directory / 'aapt2-version.txt', timeout=30)
        report = {'linuxArm64ResourceToolsBuilt': True, 'apkBuildQualified': False, 'androidRuntime': 'NOT TESTED',
                  'tools': reports, 'sourceLinks': skipped, 'sourceArchives': len(acquired)}
        (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({key: value for key, value in report.items() if key not in {'tools', 'sourceLinks'}}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    qualify(parser.parse_args())
