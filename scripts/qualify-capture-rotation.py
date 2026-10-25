#!/usr/bin/env python3
"""Build the existing fixed libpcap and execute rotation qualification, without any Android access."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def qualify():
    directory = ROOT / 'app/build/outputs/capture-rotation-qualification'; directory.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location('capture_fixed_fetch', ROOT / 'scripts/qualify-apk-jvm.py')
    fetch = importlib.util.module_from_spec(spec); spec.loader.exec_module(fetch)
    archive = fetch.acquire({'url': 'https://www.tcpdump.org/release/libpcap-1.10.7.tar.xz', 'archive': 'libpcap-1.10.7.tar.xz',
        'sha256': '68fa62cffb974f4275641ce14c2e2d75739251f30e00e6a0900903b247d76a03', 'bytes': 686992}, directory)
    with tarfile.open(archive) as incoming:
        members = incoming.getmembers()
        if len(members) > 10000 or sum(item.size for item in members) > 64 * 1024 ** 2:
            raise ValueError('Packet source expansion exceeds budget')
        for item in members:
            fetch.member_name(item.name, 'libpcap-1.10.7')
        incoming.extractall(directory, filter='data')
    build = directory / 'pcap-build'; prefix = directory / 'prefix'
    subprocess.run(['cmake', '-G', 'Ninja', '-S', str(directory / 'libpcap-1.10.7'), '-B', str(build),
        '-DCMAKE_BUILD_TYPE=Release', '-DBUILD_SHARED_LIBS=OFF', '-DCMAKE_INSTALL_LIBDIR=lib', '-DCMAKE_INSTALL_PREFIX=' + str(prefix),
        '-DPCAP_TYPE=linux', '-DBUILD_WITH_LIBNL=OFF', '-DENABLE_REMOTE=OFF', '-DDISABLE_BLUETOOTH=ON',
        '-DDISABLE_DBUS=ON', '-DDISABLE_RDMA=ON', '-DDISABLE_DAG=ON', '-DDISABLE_SNF=ON', '-DDISABLE_NETMAP=ON', '-DDISABLE_LINUX_USBMON=ON'], check=True)
    subprocess.run(['cmake', '--build', str(build), '--parallel', '2'], check=True)
    subprocess.run(['cmake', '--install', str(build)], check=True)
    packet = ROOT / 'app/src/main/cpp/packet'
    common = ['cc', '-Wall', '-Wextra', '-Werror', '-I' + str(prefix / 'include'), '-I' + str(packet)]
    store_sources = [packet / (name + '.c') for name in ['RotateStore', 'RotateSeal', 'Sha256']]
    store = directory / 'store'
    subprocess.run(common + [str(ROOT / 'scripts/capture-store-driver.c'), *map(str, store_sources),
        str(prefix / 'lib/libpcap.a'), '-Wl,--wrap=fsync', '-o', str(store)], check=True)
    subprocess.run(['sudo', '-n', sys.executable, str(ROOT / 'scripts/verify-capture-store.py'), str(store)], check=True, timeout=60)
    worker_sources = store_sources + [packet / (name + '.c') for name in ['RotateMain', 'RotateSource', 'RotateLoop', 'RotateChannel', 'RotateConfig']]
    worker_sources += [packet.parent / 'frida' / (name + '.c') for name in ['managed_control', 'input', 'fd_channel', 'fd_io']]
    worker = directory / 'worker'
    subprocess.run(common + list(map(str, worker_sources)) + [str(prefix / 'lib/libpcap.a'), '-o', str(worker)], check=True)
    subprocess.run(['sudo', '-n', 'unshare', '--net', '--', sys.executable, str(ROOT / 'scripts/verify-capture-rotation.py'), str(worker)], check=True, timeout=45)


if __name__ == '__main__':
    qualify()
