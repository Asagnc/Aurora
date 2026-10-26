"""Check the actual adaptive icon chain and decoded pixels of the fixed approved RGB PNG. No image edits."""
import hashlib
from pathlib import Path
import re
import struct
import subprocess
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
APPROVED = '274da3ca39c5ea28837a7c44534c6869252c787aa4fca800ab0c60fad1f54226'


def paeth(a, b, c):
    p = a + b - c; distances = [abs(p - a), abs(p - b), abs(p - c)]
    return (a, b, c)[distances.index(min(distances))]


def pixels(data):
    if len(data) > 16 * 1024 ** 2 or data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('Expected bounded PNG')
    offset = 8; chunks = []; compressed = bytearray()
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError('Truncated PNG chunk')
        size = struct.unpack('>I', data[offset:offset + 4])[0]; kind = data[offset + 4:offset + 8]
        end = offset + 8 + size
        if end + 4 > len(data) or zlib.crc32(data[offset + 4:end]) != struct.unpack('>I', data[end:end + 4])[0]:
            raise ValueError('PNG chunk length or CRC differs')
        payload = data[offset + 8:end]; chunks.append(kind)
        if kind == b'IHDR':
            width, height, depth, color, compression, filtering, interlace = struct.unpack('>IIBBBBB', payload)
            if not 0 < width <= 4096 or not 0 < height <= 4096 or (depth, color, compression, filtering, interlace) != (8, 2, 0, 0, 0):
                raise ValueError('Approved artwork requires bounded noninterlaced RGB8')
        elif kind == b'IDAT':
            compressed.extend(payload)
        elif kind == b'IEND':
            if size or end + 4 != len(data):
                raise ValueError('Data after PNG end')
        elif kind[0] & 32 == 0:
            raise ValueError('Unsupported critical PNG chunk')
        offset = end + 4
    if chunks[:1] != [b'IHDR'] or chunks[-1:] != [b'IEND'] or chunks.count(b'IHDR') != 1:
        raise ValueError('PNG header/end differs')
    stride = width * 3; budget = (stride + 1) * height
    if budget > 32 * 1024 ** 2:
        raise ValueError('PNG decoded budget exceeded')
    decoder = zlib.decompressobj(); raw = decoder.decompress(compressed, budget + 1)
    if len(raw) != budget or decoder.unconsumed_tail or decoder.unused_data or not decoder.eof:
        raise ValueError('PNG decoded size differs')
    previous = bytearray(stride); digest = hashlib.sha256()
    for start in range(0, budget, stride + 1):
        kind = raw[start]; row = bytearray(raw[start + 1:start + stride + 1])
        if kind not in range(5):
            raise ValueError('Unknown PNG filter')
        for index in range(stride):
            left = row[index - 3] if index >= 3 else 0; up = previous[index]; corner = previous[index - 3] if index >= 3 else 0
            predictor = (0, left, up, (left + up) // 2, paeth(left, up, corner))[kind]
            row[index] = (row[index] + predictor) & 255
        digest.update(row); previous = row
    return dict(width=width, height=height, rgbSha256=digest.hexdigest())


def verify(apk, aapt2, badging=None):
    def dump(*arguments):
        return subprocess.check_output([str(aapt2), 'dump', *arguments, str(apk)], text=True, encoding='utf-8')
    approved = (ROOT / 'docs/assets/approved-icon.png').read_bytes()
    if hashlib.sha256(approved).hexdigest() != APPROVED or (ROOT / 'app/src/main/res/drawable-nodpi/aurora_artwork.png').read_bytes() != approved:
        raise ValueError('Launcher source differs from approved artwork')
    icon = re.search(r"^application:.* icon='([^']+)'", badging or dump('badging'), re.M).group(1)
    resources = {}; selected = None
    for line in dump('resources').splitlines():
        match = re.search(r'^\s*resource (0x[0-9a-f]+) ', line)
        if match:
            selected = match[1]; resources[selected] = []
        match = re.search(r'\(file\) (\S+) type=', line)
        if selected and match:
            resources[selected].append(match[1])
    def file(identifier):
        found = resources[identifier]
        if len(found) != 1:
            raise ValueError('Ambiguous launcher resource')
        return found[0]
    adaptive = dump('xmltree', '--file', icon)
    if 'E: adaptive-icon ' not in adaptive or not re.search(r'E: background[^\n]*\n\s*A:[^\n]*=@0x0106000c', adaptive):
        raise ValueError('Adaptive icon background differs')
    foreground = file(re.search(r'E: foreground[^\n]*\n\s*A:[^\n]*=@(0x[0-9a-f]+)', adaptive)[1])
    bitmap = dump('xmltree', '--file', foreground)
    if 'E: inset ' not in bitmap or not re.search(r':inset\([^\n]*=8\.000000dp', bitmap) or ':gravity(0x010100af)=0x00000077' not in bitmap:
        raise ValueError('Launcher inset or bitmap gravity differs')
    artwork = file(re.search(r':src\([^\n]*=@(0x[0-9a-f]+)', bitmap)[1])
    with zipfile.ZipFile(apk) as archive:
        entry = archive.getinfo(artwork)
        if entry.file_size > 16 * 1024 ** 2:
            raise ValueError('APK artwork exceeds budget')
        actual = pixels(archive.read(entry))
    if actual != pixels(approved):
        raise ValueError('APK launcher pixels differ from approved artwork')
    return dict(approvedPngSha256=APPROVED, **actual, adaptiveIcon=icon, foreground=foreground, artwork=artwork,
        launcherDeviceVisualAcceptance=False)
