#!/usr/bin/env python3
"""Compile and link owned API37 resources and Binder Java stubs using native ARM64 tools."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import platform
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def run(command, directory, name, success=True):
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True, timeout=60)
    (directory / name).write_text(result.stdout + result.stderr, encoding='utf-8')
    if success:
        result.check_returncode()
    elif result.returncode == 0:
        raise ValueError('Malformed owned resource unexpectedly compiled')
    return result.stdout + result.stderr


def fixture(binary, android, work, directory):
    resource = work / 'res/values'; resource.mkdir(parents=True)
    (resource / 'strings.xml').write_text('<resources><string name="app_name">Aurora fixture</string></resources>')
    manifest = work / 'AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
        'package="io.github.asagnc.aurora.resourcefixture" android:versionCode="1" android:versionName="1">'
        '<uses-sdk android:minSdkVersion="36" android:targetSdkVersion="37"/>'
        '<application android:label="@string/app_name" android:hasCode="false"/></manifest>')
    compiled = work / 'resources.zip'; apk = work / 'owned.apk'; generated = work / 'java'
    run([binary / 'aapt2', 'compile', '--dir', resource.parent, '-o', compiled], directory, 'compile.txt')
    run([binary / 'aapt2', 'link', '-I', android / 'android.jar', '--manifest', manifest,
         '--java', generated, '-o', apk, compiled], directory, 'link.txt')
    badging = run([binary / 'aapt2', 'dump', 'badging', apk], directory, 'badging.txt')
    if "package: name='io.github.asagnc.aurora.resourcefixture'" not in badging or "targetSdkVersion:'37'" not in badging:
        raise ValueError('Linked resource package differs from owned API37 fixture')
    if not (generated / 'io/github/asagnc/aurora/resourcefixture/R.java').is_file():
        raise ValueError('No actual generated resource identifiers')
    aligned = work / 'aligned.apk'
    run([binary / 'zipalign', '-P', '16', '-f', '4', apk, aligned], directory, 'align.txt')
    run([binary / 'zipalign', '-c', '-P', '16', '4', aligned], directory, 'align-check.txt')
    (resource / 'strings.xml').write_text('<resources><string name="broken">')
    run([binary / 'aapt2', 'compile', '--dir', resource.parent, '-o', work / 'bad.zip'],
        directory, 'malformed-rejected.txt', success=False)


def binder(binary, android, work, directory):
    source = work / 'aidl/io/github/asagnc/aurora/resourcefixture/IOwned.aidl'
    source.parent.mkdir(parents=True)
    source.write_text('package io.github.asagnc.aurora.resourcefixture; interface IOwned { int ping(int value); }')
    output = work / 'aidl-java'; output.mkdir()
    run([binary / 'aidl', '--lang=java', '-p' + str(android / 'framework.aidl'),
         '-I' + str(work / 'aidl'), '-o' + str(output), source], directory, 'aidl.txt')
    generated = output / 'io/github/asagnc/aurora/resourcefixture/IOwned.java'
    text = generated.read_text()
    if 'android.os.Binder' not in text or 'ping(int value)' not in text:
        raise ValueError('Native aidl did not generate the owned Binder interface')


def qualify(directory):
    if platform.system() != 'Linux' or platform.machine().lower() not in {'aarch64', 'arm64'}:
        raise ValueError('Requires real Linux ARM64')
    directory = directory.resolve()
    if (directory / 'fixture-report.json').exists():
        raise FileExistsError('Preserve completed qualification')
    build = json.loads((directory / 'report.json').read_text())
    for tool in build['tools']:
        data = (directory / 'bin' / tool['tool']).read_bytes()
        if len(data) != tool['bytes'] or hashlib.sha256(data).hexdigest() != tool['sha256']:
            raise ValueError('Resource tool differs from actual source build')
    pin = json.loads((ROOT / 'tools/apk-build/platform.json').read_text())
    helper = module('inputs', 'verify-static-reverse-sources.py')
    retry = module('transport', 'prepare-frida-node.py')
    archive = directory / pin['archive']
    if not archive.exists():
        retry.fetch_binding(helper, archive, pin['url'], pin['sha256'], pin['bytes'])
    helper.verify(archive, pin['sha256'], pin['bytes'])
    with tempfile.TemporaryDirectory(prefix='.owned-api37-', dir=directory) as temporary:
        work = Path(temporary); android = work / 'android'; android.mkdir()
        with zipfile.ZipFile(archive) as source:
            for name in ['android.jar', 'framework.aidl']:
                data = source.read('android-37.0/' + name)
                if len(data) > 100 * 1024 ** 2:
                    raise ValueError('Platform member exceeds budget')
                (android / name).write_bytes(data)
        fixture(directory / 'bin', android, work, directory)
        binder(directory / 'bin', android, work, directory)
    report = {'linuxArm64Api37ResourcesQualified': True, 'aidlJavaGenerated': True,
              'malformedResourceRejected': True, 'zipalign16kChecked': True,
              'platformSha256': pin['sha256'], 'fullAgpBuildQualified': False, 'androidRuntime': 'NOT TESTED'}
    (directory / 'fixture-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    qualify(parser.parse_args().directory)
