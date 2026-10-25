#!/usr/bin/env python3
"""Inspect exact resource descriptors in a linked ARM64 worker; never execute embedded code."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct


class Elf:
    def __init__(self, data):
        self.data = data
        if len(data) < 64 or data[:4] != b'\x7fELF' or data[4] not in (1, 2) or data[5:7] != b'\x01\x01':
            raise ValueError('Expected bounded little-endian ELF')
        self.bits = 64 if data[4] == 2 else 32
        self.kind, self.machine = struct.unpack_from('<HH', data, 16)
        if self.bits == 64:
            offset = struct.unpack_from('<Q', data, 32)[0]
            width, count = struct.unpack_from('<HH', data, 54)
            expected = 56
        else:
            offset = struct.unpack_from('<I', data, 28)[0]
            width, count = struct.unpack_from('<HH', data, 42)
            expected = 32
        self.headers = []
        if self.kind == 1 and offset == 0 and count == 0 and width == 0:
            return  # Relocatable BPF objects are not system-loader executables.
        if width != expected or not 1 <= count <= 128:
            raise ValueError('Invalid ELF program header table')
        for index in range(count):
            raw = self.read(offset + index * width, width)
            if self.bits == 64:
                kind, flags, file, address, _, size, memory, align = struct.unpack('<IIQQQQQQ', raw)
            else:
                kind, file, address, _, size, memory, flags, align = struct.unpack('<IIIIIIII', raw)
            if kind == 1 and (size > memory or not align or align & (align - 1)):
                raise ValueError('Invalid ELF load segment')
            self.read(file, size)
            self.headers.append((kind, file, address, size, memory, flags, align))

    def read(self, offset, size):
        if offset < 0 or size < 0 or offset + size > len(self.data):
            raise ValueError('ELF range outside resource')
        return self.data[offset:offset + size]

    def offset(self, address, size):
        matches = [h for h in self.headers if h[0] == 1 and h[2] <= address and address + size <= h[2] + h[3]]
        if len(matches) != 1:
            raise ValueError('Address not in one file-backed load segment')
        h = matches[0]
        return h[1] + address - h[2]

    def cstring(self, address):
        offset = self.offset(address, 1)
        end = self.data.find(b'\0', offset, min(offset + 4096, len(self.data)))
        if end < 0:
            raise ValueError('Unterminated bounded string')
        self.offset(address, end - offset + 1)
        return self.data[offset:end].decode('utf-8')

    def describe(self):
        loads = [h for h in self.headers if h[0] == 1]
        dynamic = [h for h in self.headers if h[0] == 2]
        needed = []
        if dynamic:
            if len(dynamic) != 1:
                raise ValueError('Duplicate dynamic table')
            h = dynamic[0]
            fmt, width = ('<QQ', 16) if self.bits == 64 else ('<II', 8)
            if h[3] % width:
                raise ValueError('Partial dynamic table')
            tags = [struct.unpack(fmt, self.read(pos, width)) for pos in range(h[1], h[1] + h[3], width)]
            tables = [value for tag, value in tags if tag == 5]
            if any(tag == 1 for tag, _ in tags):
                if len(tables) != 1:
                    raise ValueError('Missing dynamic string table')
                needed = sorted(self.cstring(tables[0] + value) for tag, value in tags if tag == 1)
        return {'bits': self.bits, 'machine': self.machine, 'type': self.kind,
                'loadAlignments': [h[6] for h in loads],
                'load16KiB': bool(loads) and all(h[6] >= 16384 and (h[1] - h[2]) % 16384 == 0 for h in loads),
                'writableExecutable': any(h[5] & 3 == 3 for h in loads),
                'needed': needed, 'androidToolchainNotes': self.android_notes()}

    def android_notes(self):
        notes = []
        for header in self.headers:
            if header[0] != 4:
                continue
            position, end = header[1], header[1] + header[3]
            while position < end:
                if end - position < 12:
                    raise ValueError('Truncated ELF note header')
                names, size, kind = struct.unpack('<III', self.read(position, 12))
                if names > 256 or size > 1048576:
                    raise ValueError('Oversized ELF note')
                name_start = position + 12
                desc_start = name_start + (names + 3) // 4 * 4
                following = desc_start + (size + 3) // 4 * 4
                if following > end:
                    raise ValueError('ELF note exceeds segment')
                name = self.read(name_start, names)
                desc = self.read(desc_start, size)
                if name == b'Android\0' and kind == 1 and size == 132:
                    notes.append({'api': struct.unpack_from('<I', desc)[0],
                                  'ndk': desc[4:68].split(b'\0', 1)[0].decode('ascii'),
                                  'build': desc[68:132].split(b'\0', 1)[0].decode('ascii')})
                position = following
        return notes


def audit(binary, map_path):
    data = binary.read_bytes()
    outer = Elf(data)
    if outer.bits != 64 or outer.machine != 183:
        raise ValueError('This descriptor layout is qualified for ARM64 only')
    resources = []
    seen = set()
    pattern = re.compile(r'^\s*([a-f0-9]+)\s+[a-f0-9]+\s+([a-f0-9]+)\s+\d+\s+.*libfrida-core\.a\(([^)]+)\):\(\.data\.rel\.ro\.root_blobs\)$')
    for line in map_path.read_text(encoding='utf-8').splitlines():
        match = pattern.match(line)
        if not match:
            continue
        address, size = (int(match[i], 16) for i in (1, 2))
        if size == 0 or size % 24 or size > 24 * 1024:
            raise ValueError('Resource descriptor array size mismatch')
        offset = outer.offset(address, size)
        for index in range(size // 24):
            name_ptr, data_ptr, length, unpacked = struct.unpack_from('<QQII', data, offset + index * 24)
            name = outer.cstring(name_ptr)
            key = (match[3], name)
            if key in seen:
                raise ValueError('Duplicate resource identity')
            seen.add(key)
            payload = outer.read(outer.offset(data_ptr, length), length)
            entry = {'object': match[3], 'name': name, 'bytes': length, 'uncompressedBytes': unpacked,
                     'sha256': hashlib.sha256(payload).hexdigest()}
            if length != unpacked:
                entry['compressed'] = True
            elif payload.startswith(b'\x7fELF'):
                entry['elf'] = Elf(payload).describe()
            resources.append(entry)
    expected = {'frida-agent-64.so', 'frida-agent-32.so', 'frida-helper-64', 'frida-helper-32'}
    if not expected <= {entry['name'] for entry in resources}:
        raise ValueError('Missing required agent/helper descriptors')
    return {'workerSha256': hashlib.sha256(data).hexdigest(), 'resources': resources,
            'scope': 'Exact linked root_blobs descriptors; internal loaders and nested compressed content are not system-loader qualification',
            'androidRuntime': 'NOT TESTED'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('binary', type=Path)
    parser.add_argument('map', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(json.dumps(audit(args.binary, args.map), indent=2) + '\n', encoding='utf-8')
