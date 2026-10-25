#!/usr/bin/env python3
"""Build an ARM64 host compiler from the documented NDK30 LLVM base; not SDK equivalence."""
import argparse
import concurrent.futures
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def resources():
    spec = importlib.util.spec_from_file_location('resources', ROOT / 'scripts/qualify-apk-resources.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def run(argv, log, timeout=10800):
    with log.open('ab') as output:
        result = subprocess.run(list(map(str, argv)), stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
    result.check_returncode()


def qualify(directory):
    if platform.system() != 'Linux' or platform.machine().lower() not in {'aarch64', 'arm64'}:
        raise ValueError('Requires a real Linux ARM64 host')
    directory = directory.resolve(); directory.mkdir(parents=True, exist_ok=True)
    if (directory / 'report.json').exists() or (directory / 'bin').exists():
        raise FileExistsError('Select a new qualification output; prior results are preserved')
    if shutil.disk_usage(directory).free < 8 * 1024 ** 3:
        raise ValueError('Compiler qualification needs at least 8 GiB free host space')
    adapter = resources(); helper = adapter.source_helper()
    catalog = json.loads((ROOT / 'tools/apk-build/llvm-sources.json').read_text())
    downloads = directory / 'sources'; downloads.mkdir(exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(helper.acquire_with_retries, entry, downloads) for entry in catalog['repositories']]
        acquired = [future.result() for future in futures]
    (directory / 'materials.json').write_text(json.dumps(acquired, indent=2) + '\n')
    with tempfile.TemporaryDirectory(prefix='.native-llvm-', dir=directory) as temporary:
        work = Path(temporary)
        for entry in acquired:
            adapter.unpack(entry, downloads / entry['archive'], work)
        build = work / 'build'; log = directory / 'build.txt'
        run(['cmake', '-S', work / 'llvm', '-B', build, '-G', 'Ninja', '-DCMAKE_BUILD_TYPE=Release',
            '-DLLVM_ENABLE_PROJECTS=clang;lld', '-DLLVM_TARGETS_TO_BUILD=AArch64;X86',
            '-DLLVM_INCLUDE_TESTS=OFF', '-DLLVM_INCLUDE_BENCHMARKS=OFF', '-DLLVM_INCLUDE_EXAMPLES=OFF',
            '-DCLANG_ENABLE_ARCMT=OFF', '-DCLANG_ENABLE_STATIC_ANALYZER=OFF', '-DLLVM_ENABLE_ZLIB=OFF',
            '-DLLVM_ENABLE_ZSTD=OFF', '-DLLVM_ENABLE_LIBXML2=OFF', '-DLLVM_ENABLE_TERMINFO=OFF',
            '-DLLVM_PARALLEL_LINK_JOBS=1'], log, 300)
        run(['cmake', '--build', build, '--target', 'clang', 'lld', 'llvm-ar', 'llvm-objcopy', 'llvm-readobj', '-j', '3'], log)
        clang = build / 'bin/clang'
        version = subprocess.check_output([str(clang), '--version'], text=True, timeout=30)
        (directory / 'clang-version.txt').write_text(version)
        if 'aarch64' not in version or 'clang version 21.' not in version:
            raise ValueError('Built compiler differs from the NDK30 source-base candidate')
        fixture = work / 'fixture.c'; executable = work / 'fixture'
        fixture.write_text('#include <stdio.h>\nint main(void) { return puts("aurora-native-host-ok") < 0; }\n')
        run([clang, '-fuse-ld=lld', fixture, '-o', executable], log, 60)
        output = subprocess.check_output([str(executable)], text=True, timeout=30)
        if output != 'aurora-native-host-ok\n':
            raise ValueError('Owned native compilation did not execute')
        stage = work / 'stage'; stage.mkdir()
        files = []
        for name in ['clang', 'lld', 'llvm-ar', 'llvm-objcopy', 'llvm-readobj']:
            source = build / 'bin' / name
            with source.open('rb') as incoming:
                header = incoming.read(64)
            if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 183:
                raise ValueError('Compiler host tool is not ARM64 ELF')
            target = stage / name; shutil.copyfile(source, target); target.chmod(0o755)
            run(['strip', '--strip-unneeded', target], log, 60)
            needed = subprocess.check_output(['ldd', str(target)], text=True, timeout=30)
            if 'not found' in needed:
                raise ValueError('Native compiler has missing host dependencies')
            with target.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            files.append({'name': name, 'bytes': target.stat().st_size, 'sha256': digest, 'ldd': needed})
        # Publish only complete owned host candidates; these are not installed Android tools.
        shutil.copytree(stage, directory / 'bin')
        report = {'linuxArm64CompilerBuilt': True, 'nativeHostFixtureExecuted': True,
                  'ndkEquivalenceVerified': False, 'androidJniQualified': False, 'apkBuildQualified': False,
                  'scope': catalog['scope'], 'files': files, 'sourceArchives': len(acquired)}
        (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({key: value for key, value in report.items() if key != 'files'}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    qualify(parser.parse_args().directory)
