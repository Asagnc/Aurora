#!/usr/bin/env python3
"""Build offline/per-boot qualification fixtures; never runs them or changes policy."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


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
    policy = helper.policy_inputs()
    archive = args.materials / policy.NAME
    helper.source_helpers().verify(archive, policy.DIGEST, policy.SIZE)
    source_sha, header_count = policy.DIGEST, policy.headers(archive, output)
    suffix = '.exe' if os.name == 'nt' else ''
    host = 'windows-x86_64' if os.name == 'nt' else 'linux-x86_64'
    tools = args.ndk / 'toolchains/llvm/prebuilt' / host / 'bin'
    policy.symbols(tools / ('llvm-nm' + suffix), output / 'libfrida-core.a', output)
    compiler = tools / ('aarch64-linux-android36-clang' + ('.cmd' if os.name == 'nt' else ''))
    report = {'scope': 'qualification only; not packaged or production-enabled', 'devkitSha256': helper.DIGEST,
              'selinuxSourceSha256': source_sha, 'headerCount': header_count, 'binaries': {}, 'sources': {}}
    for name in ['frida-policy-fixture', 'frida-policy-load-fixture', 'frida-policy-audit-fixture']:
        sources = [ROOT / 'scripts' / (name + '.c')]
        if name != 'frida-policy-load-fixture':
            sources += [ROOT / 'app/src/main/cpp/frida' / item for item in ['policy_types.c', 'policy_edit.c']]
        if name == 'frida-policy-fixture':
            sources += [ROOT / 'app/src/main/cpp/frida/policy_command.c']
        binary = output / name.removeprefix('frida-')
        subprocess.run([str(compiler), '-D_GNU_SOURCE', '-Wall', '-Wextra', '-Werror', '-Os',
                        '-ffunction-sections', '-fdata-sections', '-fstack-protector-strong',
                        '-I' + str(output), '-I' + str(output / 'policy-include'), '-I' + str(ROOT / 'app/src/main/cpp/frida'), *map(str, sources),
                        str(output / 'libfrida-core.a'), '-llog', '-ldl', '-lm', '-latomic', '-pthread',
                        '-Wl,--gc-sections', '-Wl,-z,relro,-z,now', '-Wl,-z,noexecstack',
                        '-Wl,-z,max-page-size=16384', '-o', str(binary)], check=True)
        elf, needed = helper.audit(tools / ('llvm-readelf' + suffix), binary)
        binary.with_suffix('.readelf.txt').write_text(elf)
        report['binaries'][binary.name] = {'sha256': digest(binary), 'bytes': binary.stat().st_size, 'needed': needed}
        report['sources'].update({str(p.relative_to(ROOT)).replace('\\', '/'): digest(p) for p in sources})
    for name in ['app/src/main/cpp/frida/policy_types.h', 'app/src/main/cpp/frida/policy_edit.h', 'scripts/build-frida-policy-qualification.py', 'scripts/frida-policy-inputs.py']:
        report['sources'][name] = digest(ROOT / name)
    (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--materials', type=Path, required=True)
    parser.add_argument('--devkit', type=Path, required=True)
    build(parser.parse_args())
