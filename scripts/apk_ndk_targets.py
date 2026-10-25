"""Bounded target-only NDK material extraction; no foreign host executables."""
import hashlib
from contextlib import nullcontext
import importlib.util
import json
from pathlib import Path, PurePosixPath
import stat
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def acquire(directory):
    pin = json.loads((ROOT / 'tools/apk-build/ndk-targets.json').read_text())
    target = directory / pin['archive']
    helper = module('ndk_inputs', 'verify-static-reverse-sources.py')
    retry = module('ndk_transport', 'prepare-frida-node.py')
    if not target.exists():
        retry.fetch_binding(helper, target, pin['url'], pin['sha256'], pin['bytes'])
    helper.verify(target, pin['sha256'], pin['bytes'])
    return target, pin


def destination(name, pin):
    prefix = 'android-ndk-r30/toolchains/llvm/prebuilt/linux-x86_64/'
    if name in {'android-ndk-r30/' + item for item in ['source.properties', 'NOTICE', 'NOTICE.toolchain']}:
        return 'ndk-metadata/' + name.split('/')[-1]
    if not name.startswith(prefix):
        return None
    relative = name.removeprefix(prefix)
    if relative.startswith('sysroot/usr/include/'):
        component = relative.removeprefix('sysroot/usr/include/').split('/')[0]
        if component.endswith(('-linux-android', '-linux-androideabi')) and component not in pin['targetTriples']:
            return None
        return relative
    for triple in pin['targetTriples']:
        if relative.startswith('sysroot/usr/lib/' + triple + '/'):
            tail = relative.removeprefix('sysroot/usr/lib/' + triple + '/')
            first = tail.split('/')[0]
            if first.isdigit() and int(first) not in pin['androidApis']:
                return None
            return relative
    runtime = 'lib/clang/' + pin['resourceVersion'] + '/lib/linux/'
    selected = {'libclang_rt.builtins-aarch64-android.a', 'libclang_rt.builtins-x86_64-android.a',
                'aarch64/libunwind.a', 'x86_64/libunwind.a', 'aarch64/libatomic.a', 'x86_64/libatomic.a'}
    if relative.startswith(runtime) and relative.removeprefix(runtime) in selected:
        return relative
    return None


def extract(archive, toolchain, pin):
    files = []; names = set(); total = 0
    with zipfile.ZipFile(archive) as source:
        for entry in source.infolist():
            name = destination(entry.filename, pin)
            if name is None:
                continue
            parts = PurePosixPath(name).parts; mode = entry.external_attr >> 16
            if not parts or name.startswith('/') or '..' in parts or '\\' in name or name.rstrip('/') != '/'.join(parts):
                raise ValueError('Unsafe selected NDK target path')
            if name in names or stat.S_IFMT(mode) not in {0, stat.S_IFREG, stat.S_IFDIR}:
                raise ValueError('Duplicate or non-regular selected NDK target member')
            names.add(name); total += entry.file_size
            if total > 512 * 1024 ** 2 or entry.file_size > 64 * 1024 ** 2 or len(names) > 20000:
                raise ValueError('Selected NDK data exceeds extraction budget')
            target = toolchain.joinpath(*parts) if toolchain is not None else None
            if entry.is_dir():
                if target is not None:
                    target.mkdir(parents=True, exist_ok=True)
                continue
            if target is not None:
                target.parent.mkdir(parents=True, exist_ok=True)
            with source.open(entry) as incoming, (target.open('xb') if target is not None else nullcontext(None)) as output:
                digest = hashlib.sha256(); copied = 0
                while data := incoming.read(65536):
                    copied += len(data)
                    if copied > entry.file_size:
                        raise ValueError('NDK member exceeds declared byte count')
                    if output is not None:
                        output.write(data)
                    digest.update(data)
            if copied != entry.file_size:
                raise ValueError('Partial NDK target member')
            if target is not None:
                target.chmod(0o644)
            files.append({'path': name, 'bytes': copied, 'sha256': digest.hexdigest()})
    if not any(entry['path'] == 'sysroot/usr/include/jni.h' for entry in files):
        raise ValueError('Selected NDK target headers incomplete')
    return files
