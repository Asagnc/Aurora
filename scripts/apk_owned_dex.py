"""Bounded read-only DEX class definition verification for owned build fixtures, including multidex."""
import hashlib
import re
import struct
import zlib


def defined_classes(data):
    if len(data) < 112 or data[:4] != b'dex\n' or data[4:8] not in {b'035\0', b'037\0', b'038\0', b'039\0', b'040\0'}:
        raise ValueError('Unsupported owned DEX header')
    def word(offset):
        if not 0 <= offset <= len(data) - 4:
            raise ValueError('DEX word exceeds file')
        return struct.unpack_from('<I', data, offset)[0]
    if word(32) != len(data) or word(36) != 112 or word(40) != 0x12345678 or hashlib.sha1(data[32:]).digest() != data[12:32] or \
            zlib.adler32(data[12:]) & 0xffffffff != word(8):
        raise ValueError('Owned DEX length/checksum differs')
    strings, string_offset = word(56), word(60)
    types, type_offset = word(64), word(68)
    classes, class_offset = word(96), word(100)
    for size, offset, width in [(strings, string_offset, 4), (types, type_offset, 4), (classes, class_offset, 32)]:
        if size > 2000000 or (offset < 112 and (size != 0 or offset != 0)) or offset + size * width > len(data):
            raise ValueError('DEX table exceeds owned file')
    result = set()
    for index in range(classes):
        type_index = word(class_offset + index * 32)
        if type_index >= types:
            raise ValueError('DEX class type index exceeds table')
        string_index = word(type_offset + type_index * 4)
        if string_index >= strings:
            raise ValueError('DEX type string index exceeds table')
        offset = word(string_offset + string_index * 4)
        for count in range(5):
            if offset >= len(data):
                raise ValueError('Truncated DEX string length')
            byte = data[offset]; offset += 1
            if count == 4 and byte > 15:
                raise ValueError('DEX string length overflows u32')
            if not byte & 0x80:
                break
        else:
            raise ValueError('Oversized DEX string length')
        end = data.find(b'\0', offset, min(len(data), offset + 65536))
        if end < 0:
            raise ValueError('Unterminated DEX class descriptor')
        descriptor = data[offset:end]
        if descriptor in result:
            raise ValueError('Duplicate owned DEX class definition')
        result.add(descriptor)
    return result


def verify(archive, compose, required=None):
    names = [name for name in archive.namelist() if re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', name)]
    if not names or len(names) > 32 or sum(archive.getinfo(name).file_size for name in names) > 128 * 1024 ** 2:
        raise ValueError('Owned APK DEX budget differs')
    if required is None:
        required = {b'Lio/github/asagnc/aurora/buildfixture/Owned;'}
        if compose:
            required.add(b'Lio/github/asagnc/aurora/buildfixture/OwnedScreenKt;')
    if not required or not isinstance(required, set) or not all(isinstance(item, bytes) for item in required):
        raise ValueError('Explicit owned class descriptors must be a nonempty byte set')
    locations = {}; observed = set()
    for name in names:
        classes = defined_classes(archive.read(name))
        if classes & observed:
            raise ValueError('Duplicate class definitions across owned DEX files')
        observed.update(classes)
        for descriptor in required & classes:
            locations[descriptor.decode('ascii')] = name
    if set(key.encode('ascii') for key in locations) != required:
        raise ValueError('Actual generated DEX lacks defined owned Java/Compose fixture classes')
    return locations
