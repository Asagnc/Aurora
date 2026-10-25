"""Preserve a complete owned compiler candidate and execute fixtures after relocation."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess


def run(command, log):
    with log.open('ab') as output:
        subprocess.run(list(map(str, command)), stdout=output, stderr=subprocess.STDOUT, timeout=60, check=True)


def assemble(build, work, log):
    stage = work / 'stage'; binaries = stage / 'bin'; binaries.mkdir(parents=True)
    for name in ['clang', 'lld', 'llvm-ar', 'llvm-objcopy', 'llvm-readobj']:
        source = build / 'bin' / name
        with source.open('rb') as stream:
            header = stream.read(64)
        if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 183:
            raise ValueError('Compiler host tool is not ARM64 ELF')
        target = binaries / name; shutil.copyfile(source, target); target.chmod(0o755)
        run(['strip', '--strip-unneeded', target], log)
        needed = subprocess.check_output(['ldd', str(target)], text=True, timeout=30)
        if 'not found' in needed:
            raise ValueError('Compiler has missing native host dependencies')
    wrappers = {'clang++': ('clang', '--driver-mode=g++'), 'ld.lld': ('lld', '-flavor gnu')}
    for name, (tool, mode) in wrappers.items():
        target = binaries / name
        target.write_text('#!/bin/sh\nexec "$(dirname "$0")/' + tool + '" ' + mode + ' "$@"\n')
        target.chmod(0o755)
    resource = Path(subprocess.check_output([str(build / 'bin/clang'), '-print-resource-dir'], text=True, timeout=30).strip())
    resource.resolve().relative_to(build.resolve())
    if not (resource / 'include/stdarg.h').is_file() or not (resource / 'include/arm_neon.h').is_file():
        raise ValueError('Missing actual built compiler resource headers')
    shutil.copytree(resource, stage / 'lib/clang' / resource.name)
    return stage


def fixtures(stage, work, log):
    resource = subprocess.check_output([str(stage / 'bin/clang'), '-print-resource-dir'], text=True, timeout=30).strip()
    Path(resource).resolve().relative_to(stage.resolve())
    source = work / 'owned.c'; executable = work / 'owned-c'
    source.write_text('#include <stdio.h>\n#include <arm_neon.h>\nint main(void) { '
        'int32x4_t x = vdupq_n_s32(7); if(vgetq_lane_s32(x, 0) != 7) return 1; '
        'return puts("aurora-native-host-ok") < 0; }\n')
    run([stage / 'bin/clang', '-fuse-ld=lld', source, '-o', executable], log)
    if subprocess.check_output([str(executable)], text=True, timeout=30) != 'aurora-native-host-ok\n':
        raise ValueError('Relocated native C and builtin-header fixture failed')
    source = work / 'owned.cpp'; executable = work / 'owned-cxx'
    source.write_text('#include <vector>\nint main() { std::vector<int> x{1,2,3}; return x.at(2) != 3; }\n')
    run([stage / 'bin/clang++', '-fuse-ld=lld', source, '-o', executable], log)
    run([executable], log)


def preserve(build, work, directory, log):
    stage = assemble(build, work, log)
    fixtures(stage, work, log)
    files = []
    for path in sorted(stage.rglob('*')):
        if path.is_symlink():
            raise ValueError('Compiler candidate must contain only regular files and directories')
        if path.is_file():
            with path.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            files.append({'path': path.relative_to(stage).as_posix(), 'bytes': path.stat().st_size, 'sha256': digest})
    if len(files) > 5000 or sum(item['bytes'] for item in files) > 768 * 1024 ** 2:
        raise ValueError('Compiler candidate exceeds publication budget')
    os.rename(stage, directory / 'toolchain')
    return files
