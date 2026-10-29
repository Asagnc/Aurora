#!/usr/bin/env python3
"""Build offline/per-boot qualification fixtures; never runs them or changes policy."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def fixed_headers(materials, output):
    catalog = json.loads((ROOT / 'tools/frida-sources/catalog.json').read_text(encoding='utf-8'))
    entry = next(e for e in catalog['repositories'] if e['id'] == 'selinux')
    archive = materials / ('selinux-' + entry['commit'] + '.tar.gz')
    if archive.stat().st_size != entry['archiveBytes'] or digest(archive) != entry['archiveSha256']:
        raise ValueError('Pinned SELinux source archive mismatch')
    count = 0
    with tarfile.open(archive) as source:
        for member in source.getmembers():
            relative = member.name.split('/', 1)[-1]
            prefix = 'libsepol/include/'
            if not member.isfile() or not relative.startswith(prefix):
                continue
            parts = relative[len(prefix):].split('/')
            if any(p in {'', '.', '..'} or '\\' in p for p in parts) or member.size > 1024 * 1024:
                raise ValueError('Unexpected fixed source header')
            target = output.joinpath('include', *parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.extractfile(member).read())
            count += 1
    if count < 20:
        raise ValueError('Incomplete fixed libsepol headers')
    return entry['archiveSha256'], count


def build(args):
    spec = importlib.util.spec_from_file_location('frida_build', ROOT / 'scripts/build-frida-probe.py')
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    if digest(args.devkit) != helper.DIGEST or args.devkit.stat().st_size != helper.SIZE:
        raise ValueError('Pinned Frida devkit mismatch')
    output = args.directory.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # New directories avoid silently reusing headers or a stale SDK archive.
    if (output / 'libfrida-core.a').exists():
        raise ValueError('Use a fresh qualification directory')
    helper.extract(args.devkit, output)
    source_sha, header_count = fixed_headers(args.materials, output)
    suffix = '.exe' if os.name == 'nt' else ''
    host = 'windows-x86_64' if os.name == 'nt' else 'linux-x86_64'
    tools = args.ndk / 'toolchains/llvm/prebuilt' / host / 'bin'
    nm = subprocess.check_output([str(tools / ('llvm-nm' + suffix)), '-g', str(output / 'libfrida-core.a')], text=True)
    names = sorted(set(re.findall(r'\b_frida_((?:policydb|policy_file|type_datum|ebitmap|avtab|hashtab)\w*)', nm)))
    (output / 'frida-policy-symbols.h').write_text(''.join('#define ' + n + ' _frida_' + n + '\n' for n in names))
    compiler = tools / ('aarch64-linux-android36-clang' + ('.cmd' if os.name == 'nt' else ''))
    report = {'scope': 'qualification only; not packaged or production-enabled', 'devkitSha256': helper.DIGEST,
              'selinuxSourceSha256': source_sha, 'headerCount': header_count, 'binaries': {}, 'sources': {}}
    for name in ['frida-policy-fixture', 'frida-policy-load-fixture']:
        sources = [ROOT / 'scripts' / (name + '.c')]
        if name == 'frida-policy-fixture':
            sources.append(ROOT / 'scripts/frida-policy-types.c')
        binary = output / name.removeprefix('frida-')
        subprocess.run([str(compiler), '-D_GNU_SOURCE', '-Wall', '-Wextra', '-Werror', '-Os',
                        '-ffunction-sections', '-fdata-sections', '-fstack-protector-strong',
                        '-I' + str(output), '-I' + str(output / 'include'), *map(str, sources),
                        str(output / 'libfrida-core.a'), '-llog', '-ldl', '-lm', '-latomic', '-pthread',
                        '-Wl,--gc-sections', '-Wl,-z,relro,-z,now', '-Wl,-z,noexecstack',
                        '-Wl,-z,max-page-size=16384', '-o', str(binary)], check=True)
        elf, needed = helper.audit(tools / ('llvm-readelf' + suffix), binary)
        binary.with_suffix('.readelf.txt').write_text(elf)
        report['binaries'][binary.name] = {'sha256': digest(binary), 'bytes': binary.stat().st_size, 'needed': needed}
        report['sources'].update({str(p.relative_to(ROOT)).replace('\\', '/'): digest(p) for p in sources})
    for name in ['frida-policy-types.h', 'build-frida-policy-qualification.py']:
        report['sources']['scripts/' + name] = digest(ROOT / 'scripts' / name)
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--materials', type=Path, required=True)
    parser.add_argument('--devkit', type=Path, required=True)
    build(parser.parse_args())
