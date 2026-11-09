#!/usr/bin/env python3
"""Prepare a separate qualification APK source tree; never replace the installed Aurora."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import stat
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'io.github.asagnc.aurora.fridaqualification'
NATIVE = {'aurora_runner', 'aurora_pty', 'aurora_proot', 'aurora_proot_loader', 'aurora_linux_namespace',
          'aurora_linux_scope', 'aurora_tcpdump', 'aurora_packet_worker', 'aurora_packet_inject',
          'aurora_socket_diag', 'aurora_packet_rotate', 'aurora_managed_guard'}


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError('Frozen qualification source does not match the reviewed template')
    return text.replace(old, new, 1)


def extract(archive, destination):
    with zipfile.ZipFile(archive) as source:
        for entry in source.infolist():
            path = Path(entry.filename)
            if path.is_absolute() or '..' in path.parts or ':' in entry.filename or '\\' in entry.filename:
                raise ValueError('Unexpected frozen source path')
            if stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError('Frozen source links are not accepted')
            target = destination / path
            if entry.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(entry) as incoming, target.open('xb') as output:
                    shutil.copyfileobj(incoming, output, 65536)


def patch_build(source, worker_sha, version):
    path = source / 'app/build.gradle.kts'
    text = path.read_text(encoding='utf-8')
    text = replace_once(text, 'applicationId = "io.github.asagnc.aurora"', f'applicationId = "{PACKAGE}"')
    start = text.index('abstract class BuildFridaWorker : DefaultTask() {')
    end = text.index('\nval fridaWorker = ', start)
    block = text[start:end]
    old = block[block.index('    @TaskAction fun build() {'):]
    replacement = '''    @TaskAction fun build() {
        val binary = repository.file("qualification-input/aurora-frida-probe").get().asFile
        check(NativeDigests.sha256(binary) == "%s") { "Candidate worker changed" }
        val output = outputDirectory.get().asFile
        output.mkdirs()
        binary.copyTo(output.resolve("aurora-frida-probe"), overwrite = true)
        output.resolve("worker.sha256").writeText("%s", Charsets.US_ASCII)
    }
}
''' % (worker_sha, worker_sha)
    text = text[:start] + block.replace(old, replacement) + text[end:]
    text = replace_once(text, '    sources.from(rootProject.fileTree("app/src/main/cpp/frida")',
                        '    sources.from(rootProject.file("qualification-input/aurora-frida-probe"))\n'
                        '    sources.from(rootProject.fileTree("app/src/main/cpp/frida")')
    path.write_text(text, encoding='utf-8')
    manifest = source / 'app/src/main/AndroidManifest.xml'
    manifest.write_text(replace_once(manifest.read_text(encoding='utf-8'), 'android:label="Aurora"',
                                    'android:label="Aurora Frida Qualification"'), encoding='utf-8')
    tool = source / 'app/src/main/kotlin/io/github/asagnc/aurora/root/FridaTool.kt'
    tool.write_text(replace_once(tool.read_text(encoding='utf-8'), 'Root Frida17.24.3.',
                                f'Root Frida{version} qualification.'), encoding='utf-8')


def stage_owned_native(source, apk):
    accepted = json.loads((ROOT / 'docs/evidence/0.1.40/device-regression-20261031.json').read_text(encoding='utf-8'))
    accepted_hashes = {accepted['installedApksAtEnd']['io.github.asagnc.aurora']['apkSha256']}
    guidance = json.loads((ROOT / 'docs/evidence/0.1.40/root-guidance-20261109.json').read_text(encoding='utf-8'))
    accepted_hashes.add(guidance['sdkImportFeedback']['updatedDebugApkSha256'])
    expected = digest(apk)
    if expected not in accepted_hashes:
        raise ValueError('Use the exact accepted baseline APK for owned native executors')
    inputs = []
    with zipfile.ZipFile(apk) as archive:
        for abi in ['arm64-v8a', 'x86_64']:
            for name in sorted(NATIVE):
                entry = archive.getinfo(f'lib/{abi}/lib{name}.so')
                if not 0 < entry.file_size <= 64 * 1024 * 1024:
                    raise ValueError('Unexpected owned native executable size')
                data = archive.read(entry)
                if not data.startswith(b'\x7fELF'):
                    raise ValueError('Owned native executable is not ELF')
                target = source / 'qualification-input/native' / abi / name
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open('xb') as output:
                    output.write(data)
                inputs.append({'abi': abi, 'name': name, 'sha256': hashlib.sha256(data).hexdigest()})
    return {'acceptedApkSha256': expected, 'executables': inputs, 'oldFridaWorkerImported': False}


def reuse_native_build(source):
    path = source / 'app/build.gradle.kts'
    text = path.read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)
    removed = [line for line in lines if line.lstrip().startswith('externalNativeBuild { cmake {')]
    if len(removed) != 2:
        raise ValueError('Frozen native build declarations changed')
    text = ''.join(line for line in lines if line not in removed)
    text = replace_once(text, '        dependsOn("externalNativeBuild$capital")\n', '')
    text = replace_once(text, 'binaries.set(layout.buildDirectory.dir("native-runner/$kind"))',
                        'binaries.set(rootProject.layout.projectDirectory.dir("qualification-input/native"))')
    path.write_text(text, encoding='utf-8')


def stage_bridge(source):
    reference = json.loads((source / 'tools/frida-bridge/bundle-inputs.json').read_text(encoding='utf-8'))
    bundle = ROOT / 'tools/frida-bridge/build/java-bridge.js'
    expected = reference['script']
    if digest(bundle) != expected['sha256'] or bundle.stat().st_size != expected['bytes']:
        raise ValueError('Build the already pinned Java bridge before preparing qualification')
    target = source / 'tools/frida-bridge/build/java-bridge.js'
    target.parent.mkdir(parents=True, exist_ok=True)
    with bundle.open('rb') as incoming, target.open('xb') as output:
        shutil.copyfileobj(incoming, output)
    return expected


def prepare(args):
    candidate = args.candidate_directory.resolve()
    name = 'candidate.json' if args.version == '17.25.0' else 'candidate-17.26.2.json'
    pin = json.loads((ROOT / 'tools/frida-mapper-qualification' / name).read_text(encoding='utf-8'))
    if pin['version'] != args.version or pin['productionIntegrated']:
        raise ValueError('Candidate scope changed')
    report = json.loads((candidate / 'qualification.json').read_text(encoding='utf-8'))
    worker = candidate / 'aurora-frida-probe'
    worker_sha = digest(worker)
    if (not report['hostBuildPassed'] or report['productionIntegrated']
            or report['sdk']['sha256'] != pin['sha256'] or report['version'] != pin['version']
            or report['build']['workerSha256'] != worker_sha
            or report['build']['strippedBytes'] != worker.stat().st_size
            or worker_sha != pin['qualificationWorkerSha256']
            or worker.stat().st_size != pin['qualificationWorkerBytes']):
        raise ValueError('Candidate is not the independently built pinned worker')
    source = args.source_directory.resolve()
    if source.exists() or not args.sdk.resolve().is_dir():
        raise ValueError('Use a new source directory and the existing Android SDK')
    source.mkdir(parents=True, exist_ok=False)
    inputs = source / 'qualification-input'; inputs.mkdir()
    archive = inputs / 'frozen-source.zip'
    commit = subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).decode().strip()
    subprocess.run(['git', '-C', str(ROOT), 'archive', '--format=zip', '--output=' + str(archive), commit], check=True)
    extract(archive, source)
    shutil.copyfile(worker, inputs / worker.name)
    patch_build(source, worker_sha, pin['version'])
    bridge = stage_bridge(source)
    native = stage_owned_native(source, args.baseline_apk.resolve())
    reuse_native_build(source)
    (source / 'local.properties').write_text('sdk.dir=' + args.sdk.resolve().as_posix() + '\n', encoding='utf-8')
    changed = ['app/build.gradle.kts', 'app/src/main/AndroidManifest.xml',
               'app/src/main/kotlin/io/github/asagnc/aurora/root/FridaTool.kt']
    evidence = {'scope': 'Separate debug qualification identity, frozen source copy and pinned worker only',
                'baseCommit': commit, 'sourceArchiveSha256': digest(archive), 'applicationId': PACKAGE,
                'candidateVersion': pin['version'], 'candidateWorkerSha256': worker_sha,
                'javaBridge': bridge,
                'ownedNativeBaseline': native,
                'patchedSourceHashes': {name: digest(source / name) for name in changed},
                'preparerSha256': digest(Path(__file__)), 'productionIntegrated': False,
                'phoneRuntimeQualified': False, 'sharedWithProductionData': False}
    (inputs / 'preparation.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(evidence, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', choices=['17.25.0', '17.26.2'], default='17.25.0')
    parser.add_argument('--candidate-directory', type=Path, required=True)
    parser.add_argument('--source-directory', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--baseline-apk', type=Path, required=True,
                        help='Exact accepted baseline APK; SHA256 is checked against committed evidence')
    prepare(parser.parse_args())
