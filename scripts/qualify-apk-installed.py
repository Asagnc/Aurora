#!/usr/bin/env python3
"""Exercise the production Kotlin SDK installer and fixed guest probe on real Linux ARM64 only."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import uuid
import zipfile
from apk_owned_dex import verify as verify_dex

ROOT = Path(__file__).resolve().parents[1]


def module(filename):
    spec = importlib.util.spec_from_file_location('pinned_jvm', ROOT / 'scripts' / filename)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def acquire(directory, artifact, cache_qualification=None):
    directory.mkdir(parents=True, exist_ok=True)
    jvm = module('qualify-apk-jvm.py')
    entries = json.loads((ROOT / 'tools/apk-build/catalog.json').read_text())['jvm']
    entries += [dict(json.loads((ROOT / 'tools/apk-build' / name).read_text()), id=identity)
        for name, identity in [('platform.json', 'platform'), ('build-tools.json', 'build-tools')]]
    for entry in entries:
        selected = dict(entry, archive=entry['id'] + '.archive')
        if entry['id'] == 'jdk':
            incoming = artifact / entry['archive']
            jvm.helper().verify(incoming, entry['sha256'], entry['bytes'])
            shutil.copyfile(incoming, directory / selected['archive'])
        jvm.acquire(selected, directory)
    native = json.loads((ROOT / 'tools/apk-build/native-bundle.json').read_text())
    supplied = artifact / native['archive']
    if cache_qualification is not None:
        pin = json.loads((ROOT / 'tools/apk-build/dependency-cache.json').read_text())
        candidate = directory / 'android-host-candidate.tar.gz'
        subprocess.run(['python3' if os.name != 'nt' else 'python', str(ROOT / 'scripts/prepare-apk-cache-bundle.py'),
            '--native', str(supplied), '--qualification', str(cache_qualification),
            '--qualification-sha256', pin['qualificationReportSha256'], '--output', str(candidate)], check=True)
        supplied = candidate
    jvm.helper().verify(supplied, native['sha256'], native['bytes'])
    shutil.copyfile(supplied, directory / 'android-host.archive')


def probe(directory):
    if os.geteuid() != 0 or platform.system() != 'Linux' or platform.machine() not in {'aarch64', 'arm64'}:
        raise ValueError('Requires a real ARM64 private Root mount namespace')
    stage = directory / 'prepared'
    workspace = directory / 'workspace'; workspace.mkdir()
    project = workspace / 'project'; project.mkdir()
    for target in ['/opt', '/mnt']:
        if not Path(target).is_dir():
            raise ValueError('Expected host mount directory is absent')
    subprocess.run(['mount', '--bind', str(stage), '/opt'], check=True)
    subprocess.run(['mount', '-o', 'remount,bind,ro', '/opt'], check=True)
    subprocess.run(['mount', '--bind', str(project), '/mnt'], check=True)
    run_id = str(uuid.uuid4())
    script = Path('/opt/android-host/bin/aurora-apk-probe')
    report = {'scope': 'Production Kotlin installer and fixed probe on a real ARM64 Linux host; no Android runtime proof',
        'runId': run_id, 'probeSha256': hashlib.sha256(script.read_bytes()).hexdigest(), 'phoneRuntimeQualified': False, 'qualified': False}
    try:
        result = subprocess.run([str(script), run_id], capture_output=True, text=True, timeout=270)
        (directory / 'installed-probe.txt').write_text(result.stdout + result.stderr)
        report['exitCode'] = result.returncode
        result.check_returncode()
        if result.stdout.splitlines()[-1] != 'aurora-apk-probe-ok':
            raise ValueError('Missing fixed production probe completion marker')
        apk = workspace / 'project/.aurora-apk-probes' / run_id / 'build/outputs/apk/release/aurora-sdk-probe-release-unsigned.apk'
        with zipfile.ZipFile(apk) as archive:
            report['ownedDex'] = verify_dex(archive, True, {b'Lio/github/asagnc/aurora/sdkprobe/Owned;',
                b'Lio/github/asagnc/aurora/sdkprobe/ScreenKt;'})
        report['apkSha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
        shutil.copyfile(apk, directory / 'installed-owned-unsigned.apk')
        report['qualified'] = True
    except subprocess.TimeoutExpired as failure:
        (directory / 'installed-probe.txt').write_bytes((failure.stdout or b'') + (failure.stderr or b''))
        report['qualified'] = False; report['timedOut'] = True
        raise
    finally:
        (directory / 'installed-report.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--artifact', type=Path)
    parser.add_argument('--cache-qualification', type=Path)
    parser.add_argument('--probe', action='store_true')
    args = parser.parse_args()
    if args.probe:
        probe(args.directory.resolve())
    else:
        acquire(args.directory.resolve(), args.artifact.resolve(),
                args.cache_qualification.resolve() if args.cache_qualification else None)
