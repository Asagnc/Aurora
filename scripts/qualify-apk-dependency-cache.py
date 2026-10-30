#!/usr/bin/env python3
"""Qualify a public readonly Gradle seed on ARM64 CI before product integration."""
import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import uuid
import zipfile
import apk_dependency_cache as cache
import apk_dependency_process as owned
from apk_owned_dex import verify as verify_dex

SDK_SHA = 'f899eda7aee47fe3edc11546f26d7853aa6f8962f08bb2225f59a327b2327042'
INVOCATION = '/opt/android-host/bin/aurora-gradle "$base" --quiet assembleRelease'


def mount(source, destination, readonly=False):
    subprocess.run(['mount', '--bind', str(source), str(destination)], check=True)
    if readonly:
        subprocess.run(['mount', '-o', 'remount,bind,ro', str(destination)], check=True)


def wrapper(script, destination, mode):
    text = script.read_text()
    if text.count(INVOCATION) != 1:
        raise ValueError('The fixed production probe invocation changed')
    replacement = INVOCATION.replace('--quiet', '--info' if mode == 'seed' else '--offline --quiet')
    destination.write_text(text.replace(INVOCATION, replacement))
    return cache.digest(destination)


def probe_apk(project, run_id):
    path = project / '.aurora-apk-probes' / run_id / 'build/outputs/apk/release/aurora-sdk-probe-release-unsigned.apk'
    with zipfile.ZipFile(path) as archive:
        classes = verify_dex(archive, True, {b'Lio/github/asagnc/aurora/sdkprobe/Owned;',
                                          b'Lio/github/asagnc/aurora/sdkprobe/ScreenKt;'})
    return {'apkSha256': cache.digest(path), 'ownedDex': classes}


def missing_artifact_fallback(output, baseline, script):
    """A genuine missing probe AAR must fail offline and write only to its private cache online."""
    manifest = cache.verify(baseline)
    names = [name for name, record in manifest['files'].items()
             if record.get('coordinate', '').startswith('androidx.compose.foundation:foundation-android:')
             and name.endswith('.aar')]
    if len(names) != 1:
        raise ValueError('Expected one fixed foundation Android AAR for the cache-miss boundary')
    selected = names[0]
    trimmed = output / 'missing-artifact-cache'
    shutil.copytree(baseline, trimmed)
    (trimmed / selected).unlink()
    record = manifest['files'].pop(selected)
    manifest['fileCount'] -= 1; manifest['artifactFiles'] -= 1; manifest['bytes'] -= record['bytes']
    (trimmed / 'manifest.json').write_text(json.dumps(manifest, sort_keys=True, indent=2) + '\n')
    cache.verify(trimmed)
    mount(trimmed, trimmed, True)
    project = output / 'missing-artifact-project'; project.mkdir()
    mount(project, '/mnt')
    candidate = output / 'missing-artifact-offline.sh'
    wrapper(script, candidate, 'offline')
    identifier = str(uuid.uuid4())
    try:
        owned.execute(['/bin/sh', str(candidate), identifier], output / 'missing-offline.log', 270,
                      {'GRADLE_RO_DEP_CACHE': str(trimmed)})
    except subprocess.CalledProcessError:
        log = (output / 'missing-offline.log').read_text(errors='replace')
        if 'offline mode' not in log.lower() or 'foundation-android' not in log:
            raise ValueError('The offline failure does not prove the deliberately missing dependency')
    else:
        raise ValueError('The deliberately absent artifact unexpectedly resolved offline')
    cache.verify(trimmed)
    # The failed offline invocation may have created the fixture; the production
    # probe is intentionally rerun with the same project and private cache.
    elapsed = owned.execute(['/bin/sh', str(script), identifier], output / 'missing-online.log', 270,
                            {'GRADLE_RO_DEP_CACHE': str(trimmed)})
    destination = project / '.gradle-cache/caches' / selected
    if cache.digest(destination) != record['sha256'] or (trimmed / selected).exists():
        raise ValueError('A cache miss was not confined to the private writable cache')
    cache.verify(trimmed); cache.verify(baseline)
    return {'offlineMissRejected': True, 'onlinePrivateWritePassed': True, 'seconds': elapsed,
            'coordinate': record['coordinate'], 'sha256': record['sha256'], **probe_apk(project, identifier)}


def qualify(args):
    if os.geteuid() != 0 or platform.system() != 'Linux' or platform.machine() not in {'aarch64', 'arm64'}:
        raise ValueError('An isolated real Linux ARM64 Root mount namespace is required')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    prepared = args.directory.resolve() / 'prepared'
    native = args.directory.resolve() / 'android-host.archive'
    if cache.digest(native) != SDK_SHA:
        raise ValueError('The approved native SDK archive differs')
    script = prepared / 'android-host/bin/aurora-apk-probe'
    report = {'format': 1, 'qualified': False, 'phoneRuntimeQualified': False,
              'scope': 'Isolated CI mechanism only; no production cache installation',
              'nativeSdkSha256': SDK_SHA, 'probeSha256': cache.digest(script), 'freshProbes': []}
    owned.subreaper()
    try:
        mount(prepared, '/opt', True)
        seed = output / 'seed-project'; seed.mkdir()
        mount(seed, '/mnt')
        seed_script = output / 'seed-probe.sh'
        seed_wrapper_sha = wrapper(script, seed_script, 'seed')
        run_id = str(uuid.uuid4())
        # This separate preparation budget never changes the 270-second build
        # qualification contract below. All checks, including lint, remain on.
        seconds = owned.execute(['/bin/sh', str(seed_script), run_id], output / 'seed.log', 900)
        log = (output / 'seed.log').read_text(errors='replace')
        if log.splitlines()[-1] != 'aurora-apk-probe-ok':
            raise ValueError('Public preparation did not complete its full probe')
        report['seed'] = {'seconds': seconds, 'wrapperSha256': seed_wrapper_sha, **probe_apk(seed, run_id)}
        context = {'gradle': '9.8.2', 'agp': '9.4.1', 'kotlin': '2.4.21', 'jdk': '25.0.5+8',
                   'nativeSdkSha256': SDK_SHA, 'probeSha256': report['probeSha256'],
                   'seedWrapperSha256': seed_wrapper_sha, 'allSeedChildrenExited': True}
        baseline = output / 'readonly-cache'
        manifest = cache.snapshot(seed / '.gradle-cache/caches/modules-2', baseline, context, log)
        if manifest['artifactFiles'] < 100:
            raise ValueError('Unexpectedly incomplete public probe dependency closure')
        mount(baseline, baseline, True)
        rejected = False
        try:
            (baseline / 'must-not-write').write_bytes(b'forbidden')
        except OSError as failure:
            import errno
            rejected = failure.errno == errno.EROFS
        if not rejected:
            raise ValueError('The baseline mount is writable')
        report['readonlyWriteRejected'] = True
        for index in range(2):
            project = output / f'fresh-project-{index}'; project.mkdir()
            mount(project, '/mnt')
            candidate = output / f'offline-probe-{index}.sh'
            candidate_sha = wrapper(script, candidate, 'offline')
            identifier = str(uuid.uuid4())
            elapsed = owned.execute(['/bin/sh', str(candidate), identifier], output / f'fresh-{index}.log', 270,
                                    {'GRADLE_RO_DEP_CACHE': str(baseline)})
            text = (output / f'fresh-{index}.log').read_text(errors='replace')
            if text.splitlines()[-1] != 'aurora-apk-probe-ok':
                raise ValueError('Fresh offline probe did not complete')
            cache.verify(baseline)
            report['freshProbes'].append({'seconds': elapsed, 'offline': True, 'independentWritableCache': True,
                                         'wrapperSha256': candidate_sha, **probe_apk(project, identifier)})
        report['missingArtifactFallback'] = missing_artifact_fallback(output, baseline, script)
        report['archive'] = cache.export(baseline, output / 'public-apk-dependency-cache.tar.gz')
        report['cacheBytes'] = manifest['bytes']; report['cacheFiles'] = manifest['fileCount']
        report['qualified'] = True
    finally:
        owned.stop_owned()
        (output / 'qualification.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    qualify(parser.parse_args())
