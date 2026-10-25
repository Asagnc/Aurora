#!/usr/bin/env python3
"""Bounded read-only CArchive inspection; never imports or executes embedded Python code."""
import hashlib
import io
import marshal
from pathlib import PurePosixPath
import struct
import sys
import zlib

MAGIC = b'MEI\x0c\x0b\x0a\x0b\x0e'


def python_modules(data):
    """Read the primitive module table only; code objects are explicitly disallowed."""
    if sys.version_info < (3, 13):
        raise ValueError('Code-disabled module inventory requires Python 3.13 or newer')
    if len(data) < 12 or data[:4] != b'PYZ\0':
        raise ValueError('Missing Python module archive')
    offset = struct.unpack_from('!I', data, 8)[0]
    if not 12 <= offset < len(data) or len(data) - offset > 2 * 1024 ** 2:
        raise ValueError('Python module table exceeds budget')
    stream = io.BytesIO(data[offset:])
    table = marshal.load(stream, allow_code=False)
    if stream.tell() != len(data) - offset or not isinstance(table, list) or len(table) > 10000:
        raise ValueError('Unexpected Python module table')
    names = set()
    for item in table:
        if not isinstance(item, tuple) or len(item) != 2:
            raise ValueError('Invalid Python module entry')
        name, location = item
        if not isinstance(name, str) or len(name) > 512 or not all(part.replace('-', '_').isidentifier() for part in name.split('.')):
            raise ValueError('Invalid Python module name')
        if name in names or not isinstance(location, tuple) or len(location) != 3:
            raise ValueError('Duplicate or invalid Python module location')
        kind, position, size = location
        if any(type(value) is not int for value in location) or kind not in {0, 1, 3} or position < 12 or size < 0 or position + size > offset:
            raise ValueError('Python module data exceeds archive bounds')
        names.add(name)
    return sorted(names)


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
        'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data), **dynamic_libraries(data, headers)}


def dynamic_libraries(data, headers):
    entries = []
    for header in headers:
        if header[0] != 2:
            continue
        if header[5] % 16 or header[5] > 1024 * 1024:
            raise ValueError('Invalid ELF dynamic table')
        terminated = False
        for offset in range(header[2], header[2] + header[5], 16):
            tag, value = struct.unpack_from('<qQ', data, offset)
            if tag == 0:
                terminated = True; break
            entries.append((tag, value))
        if not terminated:
            raise ValueError('Unterminated ELF dynamic table')
    if not entries:
        return {'needed': [], 'rpath': [], 'runpath': [], 'soname': []}
    addresses = [value for tag, value in entries if tag == 5]
    sizes = [value for tag, value in entries if tag == 10]
    if len(addresses) != 1 or len(sizes) != 1 or not 0 < sizes[0] <= 2 * 1024 ** 2:
        raise ValueError('Invalid ELF dynamic string table')
    positions = [h[2] + addresses[0] - h[3] for h in headers if h[0] == 1 and
                 h[3] <= addresses[0] and addresses[0] + sizes[0] <= h[3] + h[5]]
    if len(positions) != 1:
        raise ValueError('ELF dynamic strings lack one file-backed load segment')
    strings = data[positions[0]:positions[0] + sizes[0]]
    def selected(tag):
        result = []
        for kind, offset in entries:
            if kind != tag:
                continue
            end = strings.find(b'\0', offset) if offset < len(strings) else -1
            if end < 0 or end - offset > 4096:
                raise ValueError('Invalid ELF dependency string')
            result.append(strings[offset:end].decode('utf-8'))
        return result
    return {name: selected(tag) for name, tag in [('needed', 1), ('rpath', 15), ('runpath', 29), ('soname', 14)]}
