#!/usr/bin/env python3
"""Bounded read-only CArchive inspection; never imports or executes embedded Python code."""
import hashlib
from pathlib import PurePosixPath
import struct
import zlib

MAGIC = b'MEI\x0c\x0b\x0a\x0b\x0e'


def archive(data):
    cookie = data.rfind(MAGIC)
    if cookie < 0 or cookie + 88 > len(data):
        raise ValueError('Missing bounded CArchive cookie')
    _, size, toc_offset, toc_bytes, python, library = struct.unpack_from('!8sIIII64s', data, cookie)
    start = cookie + 88 - size
    if start < 0 or size > 256 * 1024 ** 2 or toc_bytes > 2 * 1024 ** 2 or toc_offset + toc_bytes > size - 88:
        raise ValueError('CArchive metadata exceeds file bounds')
    cursor = start + toc_offset; end = cursor + toc_bytes
    entries = []; names = set(); total = 0
    while cursor < end:
        if cursor + 18 > end:
            raise ValueError('Partial CArchive entry')
        width, position, packed, raw, compressed, kind = struct.unpack_from('!IIIIBc', data, cursor)
        if width < 19 or cursor + width > end or compressed not in {0, 1} or position + packed > toc_offset:
            raise ValueError('Invalid CArchive entry bounds')
        name = data[cursor + 18:cursor + width].rstrip(b'\0').decode('utf-8')
        parts = PurePosixPath(name).parts
        if not name or '\0' in name or '\\' in name or name.startswith('/') or '..' in parts:
            raise ValueError('Unsafe CArchive name')
        if kind != b'o' and name in names:
            raise ValueError('Duplicate CArchive data name')
        names.add(name); total += raw
        if raw > 128 * 1024 ** 2 or total > 512 * 1024 ** 2 or len(entries) >= 10000:
            raise ValueError('CArchive expansion exceeds budget')
        entries.append((name, start + position, packed, raw, compressed, kind.decode('ascii')))
        cursor += width
    if cursor != end or python not in range(300, 400):
        raise ValueError('Invalid CArchive version or table')
    return {'pythonVersion': python, 'pythonLibrary': library.rstrip(b'\0').decode('ascii')}, entries


def content(data, entry):
    _, start, size, raw, compressed, _ = entry
    selected = data[start:start + size]
    if compressed:
        decoder = zlib.decompressobj()
        selected = decoder.decompress(selected, raw + 1)
        if not decoder.eof or decoder.unused_data or decoder.unconsumed_tail:
            raise ValueError('Partial, excessive or trailing compressed payload')
    if len(selected) != raw:
        raise ValueError('CArchive payload length differs from declaration')
    return selected


def elf(data):
    if len(data) < 64 or data[:7] != b'\x7fELF\x02\x01\x01':
        raise ValueError('Expected ELF64 little-endian')
    kind, machine = struct.unpack_from('<HH', data, 16)
    offset = struct.unpack_from('<Q', data, 32)[0]
    width, count = struct.unpack_from('<HH', data, 54)
    if kind not in {2, 3} or machine != 183 or width != 56 or not 1 <= count <= 256 or offset + width * count > len(data):
        raise ValueError('Unexpected ELF type, machine or header bounds')
    headers = [struct.unpack_from('<IIQQQQQQ', data, offset + i * width) for i in range(count)]
    if any(item[2] + item[5] > len(data) for item in headers if item[0] in {1, 2, 3}):
        raise ValueError('ELF segment exceeds file')
    loads = [item for item in headers if item[0] == 1]
    if not loads:
        raise ValueError('ELF lacks load segments')
    interpreters = [data[item[2]:item[2] + item[5]].rstrip(b'\0').decode('ascii') for item in headers if item[0] == 3]
    return {'machine': machine, 'elfType': kind, 'loadAlignments': sorted({item[7] for item in loads}),
        'page16kCompatible': all(item[7] >= 16384 and (item[2] - item[3]) % 16384 == 0 for item in loads),
        'writableExecutable': any(item[1] & 3 == 3 for item in loads), 'interpreters': interpreters,
        'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)}
