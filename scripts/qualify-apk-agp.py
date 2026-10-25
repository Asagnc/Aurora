#!/usr/bin/env python3
"""Build owned Java and Kotlin Compose APKs on actual ARM64, with no x86 execution."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import stat
import subprocess
import tempfile
import zipfile
from apk_owned_dex import verify as verify_owned_dex

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / filename)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def extract_sdk(archive, target):
    """Extract fixed SDK data and fail closed if AGP tries any foreign native tool."""
    names = set(); total = 0; rejected = []
    with zipfile.ZipFile(archive) as source:
        for item in source.infolist():
            parts = PurePosixPath(item.filename).parts
            mode = item.external_attr >> 16
            if not parts or item.filename.startswith('/') or '..' in parts or '\\' in item.filename:
                raise ValueError('Unsafe SDK member')
            if item.filename in names or stat.S_IFMT(mode) not in {0, stat.S_IFREG, stat.S_IFDIR}:
                raise ValueError('Duplicate or non-regular SDK member')
            names.add(item.filename); total += item.file_size
            if total > 768 * 1024 ** 2 or item.file_size > 128 * 1024 ** 2 or len(names) > 30000:
                raise ValueError('SDK archive exceeds extraction budget')
            destination = target.joinpath(*parts[1:])
            if len(parts) == 1 or item.is_dir():
                destination.mkdir(parents=True, exist_ok=True); continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            data = source.read(item)
            if data.startswith(b'\x7fELF'):
                rejected.append('/'.join(parts[1:]))
                data = b'#!/bin/sh\necho "Unsupported foreign SDK executable" >&2\nexit 126\n'
            destination.write_bytes(data)
            destination.chmod(0o755 if mode & 0o111 or data.startswith(b'#!') else 0o644)
    return rejected


def project(directory, compose):
    directory.mkdir(exist_ok=True)
    (directory / 'settings.gradle').write_text('''
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = 'owned-arm64-apk'
''')
    plugin = "id 'org.jetbrains.kotlin.plugin.compose' version '2.4.21'" if compose else ''
    dependencies = '''dependencies {
implementation platform('androidx.compose:compose-bom:2026.09.00')
implementation 'androidx.compose.foundation:foundation'
}''' if compose else ''
    (directory / 'build.gradle').write_text('''
plugins {
 id 'com.android.application' version '9.4.1'
 id 'org.jetbrains.kotlin.jvm' version '2.4.21' apply false
 ''' + plugin + '''
}
android {
 namespace 'io.github.asagnc.aurora.buildfixture'
 compileSdk 37
 buildToolsVersion '36.0.0'
 defaultConfig { applicationId 'io.github.asagnc.aurora.buildfixture'; minSdk 36; targetSdk 37; versionCode 1; versionName '1' }
 compileOptions { sourceCompatibility JavaVersion.VERSION_25; targetCompatibility JavaVersion.VERSION_25 }
 buildFeatures { compose = ''' + str(compose).lower() + ''' }
}
''' + dependencies)
    source = directory / 'src/main/java/io/github/asagnc/aurora/buildfixture'; source.mkdir(parents=True, exist_ok=True)
    (source / 'Owned.java').write_text('package io.github.asagnc.aurora.buildfixture; public class Owned { public static String marker() { return "owned-java"; } }')
    manifest = directory / 'src/main/AndroidManifest.xml'
    manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:hasCode="true" android:label="Owned fixture"/></manifest>')
    if compose:
        (source / 'OwnedScreen.kt').write_text('''package io.github.asagnc.aurora.buildfixture
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicText
@Composable fun OwnedScreen() { BasicText("Owned Compose fixture") }
''')


def build(gradle, environment, work, directory, stage):
    compose, native = stage
    name = ('compose' if compose else 'java') + ('-jni' if native else '')
    fixture = work / 'project'; project(fixture, compose)
    if native:
        native.configure(fixture)
    command = [str(gradle / 'bin/gradle'), '--no-daemon', '--console=plain', '--rerun-tasks',
               '-p', str(fixture), 'assembleRelease']
    with (directory / (name + '-agp.txt')).open('wb') as log:
        subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=900)
    apk = fixture / 'build/outputs/apk/release/project-release-unsigned.apk'
    candidates = list((fixture / 'build/outputs/apk/release').glob('*-release-unsigned.apk'))
    if len(candidates) != 1:
        raise ValueError('Expected exactly one owned unsigned APK')
    apk = candidates[0]
    # Preserve the actual APK even if a following correspondence check fails.
    shutil.copyfile(apk, directory / (name + '-owned-unsigned.apk'))
    with zipfile.ZipFile(apk) as archive:
        classes = verify_owned_dex(archive, compose)
    result = {'stage': name, 'apkBytes': apk.stat().st_size, 'apkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'ownedClassDefinitions': classes}
    if native:
        result['jni'] = native.inspect(apk)
    return result


def qualify(directory, resources, compiler=None):
    if platform.system() != 'Linux' or platform.machine().lower() not in {'aarch64', 'arm64'}:
        raise ValueError('Requires real Linux ARM64')
    directory = directory.resolve(); resources = resources.resolve(); directory.mkdir(parents=True, exist_ok=True)
    if (directory / 'report.json').exists():
        raise FileExistsError('Preserve completed qualification')
    proof = json.loads((resources / 'fixture-report.json').read_text())
    if proof.get('linuxArm64Api37ResourcesQualified') is not True:
        raise ValueError('Native resource functional qualification required')
    jvm = module('jvm', 'qualify-apk-jvm.py')
    inputs = json.loads((ROOT / 'tools/apk-build/catalog.json').read_text())['jvm']
    downloads = [(entry, jvm.acquire(entry, directory)) for entry in inputs]
    pins = [json.loads((ROOT / 'tools/apk-build' / name).read_text()) for name in ['platform.json', 'build-tools.json']]
    helper = module('inputs', 'verify-static-reverse-sources.py'); retry = module('transport', 'prepare-frida-node.py')
    for pin in pins:
        target = directory / pin['archive']
        if not target.exists():
            retry.fetch_binding(helper, target, pin['url'], pin['sha256'], pin['bytes'])
        helper.verify(target, pin['sha256'], pin['bytes'])
    with tempfile.TemporaryDirectory(prefix='.owned-agp-', dir=directory) as temporary:
        work = Path(temporary)
        tools = {entry['id']: jvm.unpack(entry, archive, work) for entry, archive in downloads}
        sdk = work / 'sdk'; sdk.mkdir()
        extract_sdk(directory / pins[0]['archive'], sdk / 'platforms/android-37.0')
        native = sdk / 'build-tools/36.0.0'
        rejected = extract_sdk(directory / pins[1]['archive'], native)
        # sdkmanager supplies package.xml separately from archive source.properties.
        metadata = ROOT / 'tools/apk-build/sdk-metadata'
        shutil.copyfile(metadata / 'platform-package.xml', sdk / 'platforms/android-37.0/package.xml')
        shutil.copyfile(metadata / 'build-tools-package.xml', native / 'package.xml')
        references = json.loads((resources / 'report.json').read_text())['tools']
        for reference in references:
            original = resources / 'bin' / reference['tool']; data = original.read_bytes()
            if hashlib.sha256(data).hexdigest() != reference['sha256']:
                raise ValueError('Native resource executable changed')
            shutil.copyfile(original, native / reference['tool']); (native / reference['tool']).chmod(0o755)
        fixture = work / 'project'; fixture.mkdir()
        (fixture / 'local.properties').write_text('sdk.dir=' + str(sdk) + '\n')
        (fixture / 'gradle.properties').write_text('android.aapt2FromMavenOverride=' + str(native / 'aapt2') + '\nandroid.builder.sdkDownload=false\n')
        environment = os.environ.copy()
        for key in ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'GRADLE_OPTS', 'ANDROID_HOME', 'ANDROID_SDK_ROOT']:
            environment.pop(key, None)
        environment.update(JAVA_HOME=str(tools['jdk']), GRADLE_USER_HOME=str(work / 'cache'), HOME=str(work / 'home'))
        environment['PATH'] = str(tools['jdk'] / 'bin') + ':/usr/bin:/bin'
        native_fixture = module('agp_jni', 'apk_agp_jni_fixture.py').NativeAgpFixture(directory, compiler, work,
            environment, native / 'zipalign') if compiler is not None else None
        results = [build(tools['gradle'], environment, work, directory, (compose, native_fixture)) for compose in [False, True]]
    report = {'linuxArm64AgpJavaComposeQualified': True, 'agp': '9.4.1', 'kotlin': '2.4.21',
              'artifacts': results, 'foreignElfReplacedByRejection': rejected,
              'sdkMetadataSha256': {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(metadata.glob('*.xml'))},
              'androidRuntime': 'NOT TESTED', 'androidJniQualified': False, 'fullAgpJniBuildQualified': compiler is not None,
              'ndkEquivalenceVerified': False, 'phoneInstallReady': False}
    (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--resources', type=Path, required=True)
    parser.add_argument('--compiler', type=Path)
    args = parser.parse_args(); qualify(args.directory, args.resources, args.compiler)
