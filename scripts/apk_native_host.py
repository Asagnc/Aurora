"""Pinned native ARM64 CMake/Ninja and data-only NDK CMake layout for qualification."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import tarfile
import zipfile
from apk_ndk_targets import module, extract

ROOT = Path(__file__).resolve().parents[1]


def prepare_host(directory, work):
    pins = json.loads((ROOT / 'tools/apk-build/native-host.json').read_text())
    jvm = module('native_host_fetch', 'qualify-apk-jvm.py')
    cmake = jvm.acquire(pins['cmake'], directory); ninja = jvm.acquire(pins['ninja'], directory)
    with tarfile.open(cmake) as source:
        total = 0; names = set(); selected = []
        for item in source.getmembers():
            parts = jvm.member_name(item.name, pins['cmake']['prefix'])
            if item.name in names or not (item.isfile() or item.isdir() or item.issym()):
                raise ValueError('Unsafe CMake archive member')
            names.add(item.name); total += item.size
            if len(names) > 30000 or total > 512 * 1024 ** 2:
                raise ValueError('CMake archive expansion exceeds budget')
            if len(parts) == 3 and parts[1] == 'bin' and parts[2] in {'cmake-gui', 'ccmake'}:
                continue
            selected.append(item)
        source.extractall(work, members=selected, filter='data')
    cmake = work / pins['cmake']['prefix']
    with zipfile.ZipFile(ninja) as source:
        if source.namelist() != ['ninja'] or source.getinfo('ninja').file_size > 4 * 1024 ** 2:
            raise ValueError('Unexpected Ninja archive layout')
        (cmake / 'bin/ninja').write_bytes(source.read('ninja')); (cmake / 'bin/ninja').chmod(0o755)
    audit = module('native_host_elf', 'audit-python-bundle.py'); closure = []
    for path in sorted(cmake.rglob('*')):
        if not path.is_file() or path.is_symlink():
            continue
        with path.open('rb') as stream:
            header = stream.read(20)
        if not header.startswith(b'\x7fELF'):
            continue
        metadata = audit.elf(path.read_bytes())
        if header[:6] != b'\x7fELF\x02\x01' or metadata['machine'] != 183 or not metadata['page16kCompatible'] or metadata['writableExecutable']:
            raise ValueError('Foreign ELF in native build tools')
        result = subprocess.run(['ldd', str(path)], text=True, capture_output=True, timeout=15)
        if result.returncode or 'not found' in result.stdout + result.stderr:
            raise ValueError('Unresolved native build tool dependency')
        closure.append({'path': path.relative_to(cmake).as_posix(), **metadata, 'ldd': result.stdout})
    return cmake, {'pins': pins, 'excludedExecutables': ['cmake-gui', 'ccmake'], 'elfClosure': closure}


def prepare_ndk(archive, pin, candidate, work):
    ndk = work / 'ndk'; toolchain = ndk / 'toolchains/llvm/prebuilt/linux-x86_64'
    # The official CMake layout names this directory linux-x86_64 even on ARM64. Its contents are verified ARM64.
    shutil.copytree(candidate, toolchain)
    for path in toolchain.rglob('*'):
        if path.is_file():
            path.chmod(0o755 if path.parent == toolchain / 'bin' else 0o644)
    files = extract(archive, toolchain, pin)
    with zipfile.ZipFile(archive) as source:
        names = set(); total = 0
        for item in source.infolist():
            name = item.filename.removeprefix('android-ndk-r30/')
            if item.filename == name or not (name.startswith('build/cmake/') or name.startswith('meta/') or name == 'source.properties'):
                continue
            parts = PurePosixPath(name).parts; mode = item.external_attr >> 16
            if name in names or '..' in parts or name.rstrip('/') != '/'.join(parts) or name.startswith('/') or '\\' in name or stat.S_IFMT(mode) not in {0, stat.S_IFREG, stat.S_IFDIR}:
                raise ValueError('Unsafe NDK build metadata')
            names.add(name); total += item.file_size
            if total > 8 * 1024 ** 2 or len(names) > 1000:
                raise ValueError('NDK build metadata exceeds budget')
            target = ndk.joinpath(*parts)
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True); continue
            data = source.read(item)
            if data.startswith(b'\x7fELF'):
                raise ValueError('Foreign executable in NDK metadata')
            target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(data); target.chmod(0o644)
    # LLVM's native multi-call drivers select strip/ranlib semantics from argv[0].
    # A shell forwarding objcopy arguments breaks strip's -o output option used by AGP.
    aliases = {'llvm-ranlib': 'llvm-ar', 'llvm-strip': 'llvm-objcopy'}
    for name, executable in aliases.items():
        target = toolchain / 'bin' / name
        shutil.copyfile(toolchain / 'bin' / executable, target)
        target.chmod(0o755)
    return ndk, toolchain, files
