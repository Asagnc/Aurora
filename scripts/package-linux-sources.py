#!/usr/bin/env python3
"""Bundle exact corresponding native sources and Aurora's complete build adapter."""
from pathlib import Path
import hashlib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app/build/outputs/linux-sources/aurora-linux-corresponding-source.zip'
COMPONENTS = ['aurora_proot_source-src', 'aurora_talloc_source-src', 'aurora_shmem_source-src',
              'aurora_pcap_source-src', 'aurora_tcpdump_source-src']


def tree_hash(source):
    digest = hashlib.sha256()
    for path in sorted(source.rglob('*')):
        if path.is_file():
            digest.update(path.relative_to(source).as_posix().encode() + b'\0')
            digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def package():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUTPUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for component in COMPONENTS:
            sources = sorted((ROOT / 'app/.cxx').glob(f'*/*/*/_deps/{component}'))
            if not sources:
                raise FileNotFoundError(f'Build native tools first: {component}')
            if len({tree_hash(source) for source in sources}) != 1:
                raise ValueError(f'Mixed cached source generations; select a clean build: {component}')
            source = sources[0]
            for path in sorted(source.rglob('*')):
                if path.is_file():
                    archive.write(path, Path('upstream') / component / path.relative_to(source))
        for folder in ['app/src/main/cpp/linux', 'app/src/main/assets/licenses/linux',
                       'app/src/main/cpp/packet', 'app/src/main/assets/licenses/packet']:
            for path in sorted((ROOT / folder).rglob('*')):
                if path.is_file():
                    archive.write(path, Path(folder) / path.relative_to(ROOT / folder))
        archive.write(ROOT / 'docs/LINUX_NATIVE_BUILD.md', 'README.md')
        archive.write(ROOT / 'docs/PACKET_NATIVE_BUILD.md', 'PACKET_NATIVE_BUILD.md')
    print(OUTPUT, OUTPUT.stat().st_size)


if __name__ == '__main__':
    package()
