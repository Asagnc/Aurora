#!/usr/bin/env python3
"""Inspect actual APK ELF payloads; this is packaging evidence, not execution."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

MACHINES = {'arm64-v8a': 183, 'x86_64': 62}
FILES = ['libaurora_proot.so', 'libaurora_proot_loader.so', 'libaurora_talloc.so', 'libaurora_linux_namespace.so', 'libaurora_linux_scope.so', 'libaurora_tcpdump.so', 'libaurora_packet_worker.so', 'libaurora_packet_inject.so', 'libaurora_socket_diag.so', 'libaurora_packet_rotate.so', 'libaurora_managed_guard.so']


def inspect(data, machine, loader, executable=False):
    if data[:7] != b'\x7fELF\x02\x01\x01':
        raise ValueError('Expected ELF64 little-endian')
    kind, actual_machine = struct.unpack_from('<HH', data, 16)
    if actual_machine != machine or kind != (2 if loader else 3):
        raise ValueError('Wrong architecture or ELF type')
    offset = struct.unpack_from('<Q', data, 32)[0]
    width, count = struct.unpack_from('<HH', data, 54)
    if width != 56 or not 1 <= count <= 1024:
        raise ValueError('Invalid program header table')
    headers = [struct.unpack_from('<IIQQQQQQ', data, offset + i * width) for i in range(count)]
    loads = [h for h in headers if h[0] == 1]
    if not loads or any(h[7] < 16384 or (h[2] - h[3]) % 16384 for h in loads):
        raise ValueError('ELF load segments are not 16 KiB compatible')
    if any(h[0] == 0x6474e551 and h[1] & 1 for h in headers):
        raise ValueError('Executable stack')
    if any(h[1] & 3 == 3 for h in loads):
        raise ValueError('Writable executable load segment')
    if executable:
        interpreters = [h for h in headers if h[0] == 3]
        if len(interpreters) != 1 or data[interpreters[0][2]:interpreters[0][2] + interpreters[0][5]] != b'/system/bin/linker64\0':
            raise ValueError('Managed executable must use the Android system interpreter')
        if not any(h[0] == 0x6474e552 for h in headers):
            raise ValueError('Managed executable lacks RELRO')
    if loader:
        if any(h[0] in (2, 3) for h in headers):
            raise ValueError('Freestanding loader unexpectedly needs dynamic linking')
        return set()
    dynamic = next(h for h in headers if h[0] == 2)
    tags = [struct.unpack_from('<QQ', data, i)
            for i in range(dynamic[2], dynamic[2] + dynamic[5], 16)]
    if executable and not any(tag == 24 or (tag == 30 and value & 8) or (tag == 0x6ffffffb and value & 1)
                              for tag, value in tags):
        raise ValueError('Managed executable lacks immediate symbol binding')
    table_address = next(value for tag, value in tags if tag == 5)
    segment = next(h for h in loads if h[3] <= table_address < h[3] + h[5])
    table = segment[2] + table_address - segment[3]
    needed = set()
    for tag, value in tags:
        if tag == 1:
            start = table + value
            needed.add(data[start:data.index(b'\0', start)].decode('ascii'))
    return needed


def verify(apk):
    with zipfile.ZipFile(apk) as archive:
        for abi, machine in MACHINES.items():
            for name in FILES + (['libaurora_frida.so'] if abi == 'arm64-v8a' else []):
                data = archive.read(f'lib/{abi}/{name}')
                needed = inspect(data, machine, name == 'libaurora_proot_loader.so', name in {'libaurora_managed_guard.so', 'libaurora_packet_rotate.so', 'libaurora_frida.so'})
                allowed = {'libc.so', 'libdl.so', 'libm.so', 'liblog.so', 'libandroid.so', 'libaurora_talloc.so'}
                if name == 'libaurora_managed_guard.so':
                    allowed = {'libc.so', 'libdl.so'}
                    manifest = archive.read(f'assets/managed/{abi}.guard.sha256')
                    if manifest != hashlib.sha256(data).hexdigest().encode('ascii'):
                        raise ValueError('Guard digest differs from packaged trusted manifest')
                if name == 'libaurora_packet_rotate.so':
                    manifest = archive.read(f'assets/managed/{abi}.capture.sha256')
                    if manifest != hashlib.sha256(data).hexdigest().encode('ascii'):
                        raise ValueError('Capture digest differs from packaged trusted manifest')
                if name == 'libaurora_frida.so':
                    allowed = {'libc.so', 'libdl.so', 'liblog.so', 'libm.so'}
                    if archive.read(f'assets/managed/{abi}.worker.sha256') != hashlib.sha256(data).hexdigest().encode('ascii'):
                        raise ValueError('Frida payload differs from trusted manifest')
                if not needed <= allowed:
                    raise ValueError(f'Unpackaged dependency: {needed - allowed}')
                if name == 'libaurora_proot.so' and 'libaurora_talloc.so' not in needed:
                    raise ValueError('PRoot must dynamically link packaged talloc')
                print(abi, name, len(data), hashlib.sha256(data).hexdigest())
        bridge = archive.read('assets/frida/java-bridge.js')
        metadata = json.loads(archive.read('assets/managed/java-bridge.json'))
        if metadata['sha256'] != hashlib.sha256(bridge).hexdigest() or metadata['bytes'] != len(bridge):
            raise ValueError('Java bridge differs from packaged descriptor')
        notices = archive.read('assets/licenses/frida/NOTICE.txt')
        index = json.loads(archive.read('assets/licenses/frida/index.json'))
        if hashlib.sha256(notices).hexdigest() != index['noticeSha256'] or index['sourceArchives'] != 65:
            raise ValueError('Frida source notice corpus changed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    verify(parser.parse_args().apk)
