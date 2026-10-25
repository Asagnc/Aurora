#!/usr/bin/env python3
"""Read-only source/notice coverage for observed and fixed-recipe static OpenSSL variants."""
import argparse
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


def recipes(pin):
    data = {}
    for entry in pin['recipes']:
        path = ROOT / 'tools/network-proxy/crypto-recipes' / entry['file']
        raw = path.read_bytes()
        if len(raw) != entry['bytes'] or hashlib.sha256(raw).hexdigest() != entry['sha256'] or \
                hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() != entry['gitBlobSha1']:
            raise ValueError('Static crypto source recipe differs from fixed Git blob')
        data[entry['file']] = raw
    vendor = json.loads(data['aioquic-vendor.json'])['urls']
    expected = 'https://github.com/aiortc/aioquic-openssl/releases/download/3.3.0-1/openssl-{platform}.tar.gz'
    if vendor != [expected] or pin['vendor']['url'] != expected.replace('{platform}', 'manylinux_aarch64'):
        raise ValueError('Aioquic vendor is not the fixed upstream wheel recipe')
    tree = ast.parse(data['aioquic-openssl-build.py'])
    urls = {item.value for item in ast.walk(tree) if isinstance(item, ast.Constant) and isinstance(item.value, str) and item.value.startswith('https://')}
    if urls != {'https://www.openssl.org/source/openssl-3.3.0.tar.gz'} or b'CIBW_BEFORE_BUILD: python scripts/fetch-vendor.py' not in data['aioquic-workflow.yml']:
        raise ValueError('Aioquic wheel-to-vendor-to-source recipe chain differs')


def vendor_identity(path):
    with tarfile.open(path) as archive:
        members = archive.getmembers()
        if len(members) > 5000 or sum(item.size for item in members) > 96 * 1024 ** 2:
            raise ValueError('Crypto vendor archive exceeds budget')
        headers = [item for item in members if item.name == 'include/openssl/opensslv.h']
        if len(headers) != 1 or not headers[0].isfile() or not 0 < headers[0].size < 65536:
            raise ValueError('Vendor OpenSSL version header is not bounded regular data')
        data = archive.extractfile(headers[0]).read()
        if b'# define OPENSSL_VERSION_STR "3.3.0"' not in data:
            raise ValueError('Vendor OpenSSL version differs')
        return {'version': '3.3.0', 'headerSha256': hashlib.sha256(data).hexdigest(),
            'scope': 'Pinned upstream vendor headers; does not prove every statically linked object was rebuilt'}


def prepare(directory, inputs, identity):
    pin = json.loads((ROOT / 'tools/network-proxy/crypto-materials.json').read_text())
    recipes(pin)
    observed = json.loads(identity.read_text())
    if not observed['cryptographyOpenSsl'].startswith('OpenSSL 3.5.5 '):
        raise ValueError('Observed cryptography runtime differs from source material plan')
    directory.mkdir(parents=True, exist_ok=True); inputs.mkdir(parents=True, exist_ok=True)
    fetch = module('crypto_fixed_fetch', 'prepare-proxy-python-materials.py')
    paths = fetch.acquire_inputs(pin['sources'] + [pin['vendor']], inputs)
    store = directory / 'notices'; store.mkdir(exist_ok=True)
    source = module('crypto_source_notices', 'prepare-network-proxy-materials.py')
    materials = []
    for entry in pin['sources']:
        notices = source.notices(paths[entry['archive']], store)
        if not any(item['entry'].endswith('/LICENSE.txt') for item in notices):
            raise ValueError('OpenSSL source notice missing')
        materials.append({'pin': entry, 'notices': notices})
    report = {'inputs': pin, 'sources': materials, 'vendorIdentity': vendor_identity(paths[pin['vendor']['archive']]),
        'observedCryptographyOpenSsl': observed['cryptographyOpenSsl'], 'licenseClosureVerified': False,
        'scope': 'Fixed source and notice material coverage. Aioquic static version follows its exact wheel build recipe; full static-link reproducibility not established.'}
    (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'sourceCount': len(materials), 'vendorIdentity': report['vendorIdentity'], 'observedCryptographyOpenSsl': report['observedCryptographyOpenSsl']}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['directory', 'inputs', 'identity']:
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args(); prepare(args.directory, args.inputs, args.identity)
