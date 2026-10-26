"""Fixed proxy distribution/source correspondence. Reads archives without executing their contents."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
from urllib.parse import urlsplit
import zipfile
from apk_release_materials import ROOT, sha, snapshot

CATALOG = ROOT / 'tools/network-proxy'


def load(name):
    return json.loads((CATALOG / name).read_text())


def profiles():
    source = (ROOT / 'core/src/main/kotlin/io/github/asagnc/aurora/core/NetworkProxyPreparation.kt').read_text()
    pin = load('catalog.json')
    if any('"' + value + '"' not in source for value in [pin['url'], pin['sha256'], pin['members']['mitmdump']['sha256']]) or \
            '"' + pin['sha256'] + '", ' + str(pin['bytes']) + ')' not in source:
        raise ValueError('Proxy distribution differs from compiled installation pins')
    spec = importlib.util.spec_from_file_location('proxy_profiles', ROOT / 'scripts/qualify-proxy-product.py')
    helper = importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)
    return helper.profiles()


def entries():
    rows = []
    def add(group, item):
        name = item.get('archive') or Path(urlsplit(item['url']).path).name
        rows.append(dict(group=group, archive=name, bytes=item.get('bytes', item.get('size')),
            sha256=item.get('sha256') or item['hash'].removeprefix('sha256:'), entry='inputs/' + group + '/' + name))
    add('upstream', load('upstream.json')['source'])
    for package in load('source-materials.json')['packages']:
        for item in [package['sdist'], *package['wheels']]:
            add('packages', item)
    for item in load('rust-materials.json')['crates']:
        add('rust', dict(item, archive=item['name'] + '-' + item['version'] + '.crate'))
    python = load('python-materials.json')
    for item in [python['install'], python['full'], *python['sources'].values()]:
        add('python', item)
    for item in load('gcc-materials.json')['inputs'].values():
        add('gcc', item)
    crypto = load('crypto-materials.json')
    for item in [*crypto['sources'], crypto['vendor']]:
        add('crypto', item)
    if len({row['entry'] for row in rows}) != len(rows):
        raise ValueError('Conflicting fixed proxy source names')
    return rows


def verify_file(path, row):
    limit = row['bytes'] or 64 * 1024 ** 2  # Cargo locks specify hashes, not archive sizes.
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= limit or \
            row['bytes'] is not None and path.stat().st_size != row['bytes'] or sha(path) != row['sha256']:
        raise ValueError('Fixed proxy material differs: ' + path.name)


def verify_sources(path):
    expected = entries()
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != {'manifest.json', 'aurora-source.zip', *[row['entry'] for row in expected]}:
            raise ValueError('Proxy sources have missing, duplicate or unexpected entries')
        if archive.getinfo('manifest.json').file_size > 1024 ** 2:
            raise ValueError('Proxy source manifest exceeds budget')
        manifest = json.loads(archive.read('manifest.json'))
        if manifest['format'] != 1 or manifest['distribution'] != load('catalog.json') or \
                manifest['profiles'] != profiles() or manifest['inputs'] != expected:
            raise ValueError('Proxy source inventory differs from the trusted checkout')
        for row in expected + [dict(manifest['auroraSource'], entry='aurora-source.zip')]:
            size = archive.getinfo(row['entry']).file_size
            if not 0 < size <= (row['bytes'] or 64 * 1024 ** 2) or row['bytes'] is not None and size != row['bytes']:
                raise ValueError('Proxy source entry size differs')
            with archive.open(row['entry']) as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != row['sha256']:
                    raise ValueError('Proxy source entry digest differs')
        with tempfile.TemporaryDirectory(prefix='aurora-proxy-source-check-') as temporary:
            selected = Path(temporary) / 'source.zip'; snapshot(selected)
            if sha(selected) != manifest['auroraSource']['sha256']:
                raise ValueError('Proxy own source snapshot differs from the trusted checkout')
    return manifest


def verify_release(apk, distribution, sources):
    pin = load('catalog.json'); verify_file(distribution, pin)
    expected = profiles()
    with zipfile.ZipFile(apk) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Duplicate APK entries')
        for name, digest in expected.items():
            entry = archive.getinfo('assets/network-proxy/' + name)
            if not 0 < entry.file_size <= 131072 or hashlib.sha256(archive.read(entry)).hexdigest() != digest:
                raise ValueError('APK selects another proxy profile')
    manifest = verify_sources(sources)
    return dict(distribution=pin, profiles=expected, sourceInputs=len(manifest['inputs']),
        phoneRuntimeQualified=False, licenseClosureVerified=False,
        scope='Fixed upstream and dependency sources, candidate wheels, and own profiles; not full rebuild or static-link reproducibility proof')
