#!/usr/bin/env python3
"""Build a host test tracer from the already verified, Android-built source set.

No downloads, APK execution or source-cache mutations. Host glibc replaces
Bionic and Android shmem is excluded; this cannot qualify Android execution.
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def build(output):
    if sys.platform != 'linux':
        raise RuntimeError('Linux native host qualification only')
    output = output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    builds = sorted((ROOT / 'app/.cxx/Debug').glob('*/x86_64'))
    if len(builds) != 1:
        raise RuntimeError('Select one current Android Debug x86_64 source generation')
    cache = builds[0]
    upstream = cache / '_deps/aurora_proot_source-src'
    talloc = cache / '_deps/aurora_talloc_source-src'
    library = output / 'library'
    library.mkdir()
    shutil.copyfile(ROOT / 'app/src/main/cpp/linux/talloc-replace.h', library / 'replace.h')
    subprocess.run(['cc', '-shared', '-fPIC', '-O2', '-D_GNU_SOURCE',
                    '-DTALLOC_BUILD_VERSION_MAJOR=2', '-DTALLOC_BUILD_VERSION_MINOR=5',
                    '-DTALLOC_BUILD_VERSION_RELEASE=0', '-DHAVE_SYS_AUXV_H',
                    '-DHAVE_GETAUXVAL', '-DHAVE_CONSTRUCTOR_ATTRIBUTE', '-DHAVE_INTPTR_T',
                    '-DHAVE_VA_COPY', '-I' + str(library), '-I' + str(talloc),
                    str(talloc / 'talloc.c'), '-o', str(library / 'libtalloc.so')], check=True)
    source = output / 'proot'
    shutil.copytree(upstream, source)
    for generated, target in [('proot-event.c', 'tracee/event.c'),
                              ('sysvipc_shm.c', 'extension/sysvipc/sysvipc_shm.c')]:
        shutil.copyfile(cache / 'linux-config' / generated, source / 'src' / target)
    ashmem = source / 'src/extension/ashmem_memfd/ashmem_memfd.c'
    ashmem.write_text('#include <string.h>\n' + ashmem.read_text())
    subprocess.run(['make', '-C', str(source / 'src'), '-j2',
                    'CPPFLAGS=-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -I' + str(talloc),
                    'CFLAGS=-Wall -Wextra -O2 -DNDEBUG',
                    'LDFLAGS=-L' + str(library) + ' -Wl,-rpath,' + str(library) + ' -ltalloc -Wl,-z,noexecstack',
                    'proot'], check=True)
    binary = source / 'src/proot'
    subprocess.run([str(binary), '--version'], check=True, env={**os.environ, 'PROOT_NO_SECCOMP': '1'})
    return binary


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: build-host-proot.py NEW_OUTPUT_DIRECTORY')
    print(build(Path(sys.argv[1])))
