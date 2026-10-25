#!/usr/bin/env python3
"""Compile/link owned Android JNI with real ARM64 host LLVM and fixed official target data."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import subprocess
import tempfile
from apk_ndk_targets import acquire, extract

ROOT = Path(__file__).resolve().parents[1]


def verify_candidate(directory):
    proof = json.loads((directory / 'report.json').read_text())
    if not proof.get('linuxArm64CompilerBuilt') or not proof.get('relocatedCxxFixtureExecuted') or not proof.get('resourceHeadersPreserved'):
        raise ValueError('Requires the actual source-built relocated ARM64 compiler qualification')
    root = directory / 'toolchain'; names = set()
    for entry in proof['files']:
        relative = PurePosixPath(entry['path'])
        if relative.is_absolute() or '..' in relative.parts or relative.as_posix() != entry['path']:
            raise ValueError('Unsafe candidate file reference')
        target = root.joinpath(*relative.parts)
        if target.is_symlink() or not target.is_file() or target.stat().st_size != entry['bytes']:
            raise ValueError('Compiler candidate member differs')
        with target.open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != entry['sha256']:
                raise ValueError('Compiler candidate content differs')
        names.add(entry['path'])
    actual = {path.relative_to(root).as_posix() for path in root.rglob('*') if path.is_file()}
    if names != actual:
        raise ValueError('Unrecorded compiler candidate files')
    return root, proof


def compile_fixture(toolchain, work, directory, triple, machine, env):
    source = work / (triple + '.cpp'); output = directory / ('libowned-' + triple + '.so')
    source.write_text('''#include <jni.h>
#include <vector>
#include <string>
extern "C" JNIEXPORT jstring JNICALL Java_io_github_asagnc_aurora_buildfixture_Owned_nativeMarker(JNIEnv* env, jclass) {
  std::vector<int> values{1,2,3};
  std::string text = "owned-jni-" + std::to_string(values.at(2));
  return env->NewStringUTF(text.c_str());
}
''')
    command = [str(toolchain / 'bin/clang++'), '--target=' + triple + '36', '--sysroot=' + str(toolchain / 'sysroot'),
        '-stdlib=libc++', '--rtlib=compiler-rt', '--unwindlib=libunwind', '-std=c++17', '-O2', '-fPIC', '-shared',
        '-fuse-ld=lld', '-Wl,--no-undefined,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384,-z,common-page-size=16384',
        '-Wl,-soname,' + output.name, str(source), '-o', str(output)]
    with (directory / (triple + '-build.txt')).open('wb') as log:
        subprocess.run(command, env=env, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)
    data = output.read_bytes()
    spec = importlib.util.spec_from_file_location('native_audit', ROOT / 'scripts/verify-linux-native.py')
    audit = importlib.util.module_from_spec(spec); spec.loader.exec_module(audit)
    needed = audit.inspect(data, machine, False)
    if not needed <= {'libc.so', 'libm.so', 'libdl.so', 'libc++_shared.so'} or 'libc++_shared.so' not in needed:
        raise ValueError('Unexpected Android JNI dependency closure')
    report = subprocess.check_output([str(toolchain / 'bin/llvm-readobj'), '--dyn-symbols', str(output)], env=env, text=True, timeout=30)
    (directory / (triple + '-symbols.txt')).write_text(report)
    if 'Java_io_github_asagnc_aurora_buildfixture_Owned_nativeMarker' not in report:
        raise ValueError('Compiled JNI export missing')
    runtime = toolchain / 'sysroot/usr/lib' / triple / 'libc++_shared.so'
    return {'triple': triple, 'machine': machine, 'api': 36, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'needed': sorted(needed), 'cxxRuntimeSha256': hashlib.sha256(runtime.read_bytes()).hexdigest()}


def qualify(directory, compiler):
    if platform.system() != 'Linux' or platform.machine().lower() not in {'aarch64', 'arm64'}:
        raise ValueError('Requires actual Linux ARM64 host, with no emulation fallback')
    directory = directory.resolve(); directory.mkdir(parents=True, exist_ok=True)
    if (directory / 'report.json').exists():
        raise FileExistsError('Preserve completed qualification')
    candidate, proof = verify_candidate(compiler.resolve())
    archive, pin = acquire(directory)
    with tempfile.TemporaryDirectory(prefix='.owned-jni-', dir=directory) as temporary:
        work = Path(temporary); toolchain = work / 'toolchain'
        shutil.copytree(candidate, toolchain)
        for path in toolchain.rglob('*'):
            if path.is_file():
                path.chmod(0o755 if path.parent == toolchain / 'bin' else 0o644)
        files = extract(archive, toolchain, pin)
        (directory / 'ndk-selected.json').write_text(json.dumps(files, indent=2) + '\n')
        env = os.environ.copy()
        for key in ['LD_PRELOAD', 'LD_LIBRARY_PATH', 'CPATH', 'C_INCLUDE_PATH', 'CPLUS_INCLUDE_PATH', 'OBJC_INCLUDE_PATH',
                    'LIBRARY_PATH', 'COMPILER_PATH', 'GCC_EXEC_PREFIX', 'CCC_OVERRIDE_OPTIONS', 'CLANG_CONFIG_FILE_SYSTEM_DIR', 'CLANG_CONFIG_FILE_USER_DIR']:
            env.pop(key, None)
        env['HOME'] = str(work / 'home'); env['PATH'] = str(toolchain / 'bin') + ':/usr/bin:/bin'
        results = [compile_fixture(toolchain, work, directory, triple, machine, env)
                   for triple, machine in [('aarch64-linux-android', 183), ('x86_64-linux-android', 62)]]
        report = {'linuxArm64AndroidJniCompileLinked': True, 'jni': results, 'ndkVersion': pin['version'],
                  'ndkArchiveSha256': pin['sha256'], 'compilerSourceArchives': proof['sourceArchives'],
                  'targetFiles': len(files), 'targetBytes': sum(item['bytes'] for item in files),
                  'ndkEquivalenceVerified': False, 'androidRuntime': 'NOT TESTED', 'fullAgpJniBuildQualified': False, 'phoneInstallReady': False}
        (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--compiler', type=Path, required=True)
    args = parser.parse_args(); qualify(args.directory, args.compiler)
