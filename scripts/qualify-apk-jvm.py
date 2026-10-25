#!/usr/bin/env python3
"""Qualify fixed ARM64 JDK/Gradle on an owned Linux host; no phone or runtime installation."""
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
import tarfile
import tempfile
import urllib.error
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def helper():
    spec = importlib.util.spec_from_file_location('pinned', ROOT / 'scripts/verify-static-reverse-sources.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def acquire(entry, directory):
    target = directory / entry['archive']
    tools = helper()
    if not target.exists():
        for attempt in range(3):
            try:
                tools.fetch(target, entry['url'], entry['sha256'], entry['bytes'])
                break
            except urllib.error.HTTPError as failure:
                if attempt == 2 or failure.code not in {408, 429, 500, 502, 503, 504}:
                    raise
            except (urllib.error.URLError, TimeoutError, ConnectionError):
                if attempt == 2:
                    raise
    tools.verify(target, entry['sha256'], entry['bytes'])
    return target


def member_name(name, prefix):
    parts = PurePosixPath(name).parts
    if not parts or parts[0] != prefix or name.rstrip('/') != '/'.join(parts) or any(part in {'..', '.'} for part in parts) or '\\' in name:
        raise ValueError('Unsafe toolchain archive path')
    return parts


def unpack(entry, source, destination):
    total = 0; names = set()
    if entry['id'] == 'jdk':
        with tarfile.open(source) as archive:
            members = archive.getmembers()
            for member in members:
                member_name(member.name, entry['prefix'])
                if member.name in names or not (member.isfile() or member.isdir() or member.issym() or member.islnk()):
                    raise ValueError('Duplicate or unsupported JDK member')
                names.add(member.name); total += member.size
                if len(names) > 30000 or total > 1_073_741_824:
                    raise ValueError('JDK expansion exceeds budget')
            archive.extractall(destination, filter='data')
    else:
        with zipfile.ZipFile(source) as archive:
            for member in archive.infolist():
                member_name(member.filename, entry['prefix'])
                mode = member.external_attr >> 16
                if member.filename in names or stat.S_IFMT(mode) not in {0, stat.S_IFREG, stat.S_IFDIR}:
                    raise ValueError('Duplicate or unsupported Gradle member')
                names.add(member.filename); total += member.file_size
                if len(names) > 30000 or total > 1_073_741_824:
                    raise ValueError('Gradle expansion exceeds budget')
                target = destination / member.filename
                if member.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.open(member) as incoming, target.open('xb') as outgoing:
                        shutil.copyfileobj(incoming, outgoing, 65536)
                    target.chmod(0o755 if mode & 0o111 else 0o644)
    return destination / entry['prefix']


def run_checked(argv, environment, directory, name):
    result = subprocess.run(list(map(str, argv)), env=environment, capture_output=True, text=True, timeout=240)
    (directory / name).write_text(result.stdout + result.stderr, encoding='utf-8')
    result.check_returncode()
    return result.stdout + result.stderr


def qualify(args):
    if not args.acquire_only and (platform.system() != 'Linux' or platform.machine().lower() not in {'aarch64', 'arm64'}):
        raise ValueError('Execution requires a real Linux ARM64 host; no emulation fallback')
    args.directory = args.directory.resolve()
    entries = json.loads((ROOT / 'tools/apk-build/catalog.json').read_text())['jvm']
    args.directory.mkdir(parents=True, exist_ok=True)
    inputs = [(entry, acquire(entry, args.directory)) for entry in entries]
    if args.acquire_only:
        print(json.dumps({'downloaded': [entry['id'] for entry, _ in inputs], 'executed': False}))
        return
    with tempfile.TemporaryDirectory(prefix='.jvm-check-', dir=args.directory) as temporary:
        work = Path(temporary)
        unpacked = {entry['id']: unpack(entry, source, work) for entry, source in inputs}
        jdk = unpacked['jdk']; gradle = unpacked['gradle']
        header = (jdk / 'bin/java').read_bytes()[:64]
        if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 183:
            raise ValueError('Selected JVM is not ELF64 AArch64')
        environment = os.environ.copy()
        for name in ['JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'GRADLE_OPTS']:
            environment.pop(name, None)
        environment.update(JAVA_HOME=str(jdk), GRADLE_USER_HOME=str(work / 'cache'), HOME=str(work / 'home'))
        environment['PATH'] = str(jdk / 'bin') + os.pathsep + '/usr/bin:/bin'
        java = run_checked([jdk / 'bin/java', '-version'], environment, args.directory, 'java-version.txt')
        compiler = run_checked([jdk / 'bin/javac', '-version'], environment, args.directory, 'javac-version.txt')
        if '25.0.5' not in java or '25.0.5' not in compiler:
            raise ValueError('JDK launch differs from fixed version')
        project = work / 'fixture'; project.mkdir()
        (project / 'settings.gradle').write_text("rootProject.name='owned-jvm-fixture'\n")
        (project / 'build.gradle').write_text("plugins { id 'java' }\n")
        source = project / 'src/main/java/Fixture.java'; source.parent.mkdir(parents=True)
        source.write_text('public class Fixture { public static void main(String[] args) { System.out.print("aurora-jvm-ok"); } }')
        build = run_checked([gradle / 'bin/gradle', '--offline', '--no-daemon', '--console=plain', '-p', project, 'classes'],
                            environment, args.directory, 'gradle-fixture.txt')
        if 'BUILD SUCCESSFUL' not in build:
            raise ValueError('Gradle did not confirm the owned Java compilation')
        output = run_checked([jdk / 'bin/java', '-cp', project / 'build/classes/java/main', 'Fixture'],
                             environment, args.directory, 'java-fixture.txt')
        if output != 'aurora-jvm-ok':
            raise ValueError('Owned compiled fixture did not execute')
        report = {'linuxArm64JvmQualified': True, 'jdk': '25.0.5+8', 'gradle': '9.8.2',
                  'androidRuntime': 'NOT TESTED', 'apkBuildQualified': False,
                  'inputs': [{key: entry[key] for key in ['id', 'version', 'bytes', 'sha256']} for entry, _ in inputs]}
        (args.directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--acquire-only', action='store_true')
    qualify(parser.parse_args())
