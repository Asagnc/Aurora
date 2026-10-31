#!/usr/bin/env python3
"""Collect exact public cache metadata/notices and coordinate-matched upstream sources, without execution."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import io
import json
from pathlib import Path
import re
import tarfile
import tempfile
import urllib.error
import urllib.request
from urllib.parse import urlsplit
import zipfile
import apk_dependency_cache as cache
from apk_dependency_licenses import declarations

ROOT = Path(__file__).resolve().parents[1]


def store(directory, name, data):
    path = directory / name
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('xb') as output:
        output.write(data)
    return {'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def safe_url(url):
    parsed = urlsplit(url)
    if (parsed.scheme != 'https' or parsed.hostname not in cache.HOSTS or parsed.port not in {None, 443}
            or parsed.username or parsed.password or parsed.query or parsed.fragment):
        raise ValueError('Source request is outside fixed public repository hosts')
    return url


class PublicRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        safe_url(newurl)
        return super().redirect_request(request, fp, code, message, headers, newurl)


def notices(source, member, directory):
    result = []
    with tempfile.TemporaryFile() as staged:
        with source.extractfile(member) as incoming:
            while block := incoming.read(65536):
                staged.write(block)
        staged.seek(0)
        with zipfile.ZipFile(staged) as archive:
            if len(archive.infolist()) > 100000:
                raise ValueError('Public dependency ZIP entry budget exceeded')
            pattern = re.compile(r'\.(class|dex|so(?:\.\d+)*|wasm|dll|dylib|jnilib|exe)$')
            code = sum(bool(pattern.search(entry.filename)) for entry in archive.infolist())
            for entry in archive.infolist():
                if entry.filename.endswith('.jar'):
                    if entry.file_size > 128 * 1024 ** 2:
                        raise ValueError('Nested public dependency JAR exceeds budget')
                    with zipfile.ZipFile(io.BytesIO(archive.read(entry))) as nested:
                        code += sum(bool(pattern.search(name)) for name in nested.namelist())
            total = 0
            for entry in archive.infolist():
                if entry.is_dir() or not re.search(r'(?i)(^|/)(license[^/]*|notice[^/]*|copying[^/]*|copyright[^/]*)$', entry.filename):
                    continue
                if not 0 < entry.file_size <= 4 * 1024 ** 2 or len(result) >= 256:
                    raise ValueError('Embedded dependency notice exceeds budget')
                data = archive.read(entry); total += len(data)
                if total > 16 * 1024 ** 2:
                    raise ValueError('Embedded dependency notices exceed budget')
                name = 'notices/' + hashlib.sha256(member.name.encode()).hexdigest()[:16] + '/' + hashlib.sha256(entry.filename.encode()).hexdigest()[:32] + '.txt'
                result.append({**store(directory, name, data), 'archiveEntry': entry.filename})
    return {'notices': result, 'codeEntries': code}


def inspect(archive, directory, pin):
    records, binaries, manifest = [], {}, None
    with tarfile.open(archive, 'r|gz') as source:
        for member in source:
            if not member.isfile() or member.issparse() or not 0 <= member.size <= 512 * 1024 ** 2:
                raise ValueError('Unexpected public cache entry')
            if manifest is None:
                if member.name != 'dependency-cache/manifest.json' or member.size > 32 * 1024 ** 2:
                    raise ValueError('The fixed cache must begin with its manifest')
                data = source.extractfile(member).read()
                if hashlib.sha256(data).hexdigest() != pin['manifestSha256']:
                    raise ValueError('Public cache manifest differs')
                manifest = json.loads(data)
                records.append(store(directory, 'cache-manifest.json', data))
                continue
            name = member.name.removeprefix('dependency-cache/')
            row = manifest['files'].get(name)
            if not row or member.size != row['bytes']:
                raise ValueError('Unexpected public cache inventory entry')
            if name.endswith(('.pom', '.module')):
                data = source.extractfile(member).read()
                if hashlib.sha256(data).hexdigest() != row['sha256']:
                    raise ValueError('Public metadata differs')
                selected = 'metadata/' + hashlib.sha256(name.encode()).hexdigest() + Path(name).suffix
                records.append({**store(directory, selected, data), 'cachePath': name, 'coordinate': row['coordinate'],
                                'requestedUrls': row['requestedUrls']})
            elif name.endswith(('.jar', '.aar')):
                coordinate = row['coordinate']
                binaries.setdefault(coordinate, []).append({'cachePath': name, 'sha256': row['sha256'],
                    'requestedUrls': row['requestedUrls'], **notices(source, member, directory)})
    if manifest is None or not binaries:
        raise ValueError('Public dependency inventory is empty')
    return records, binaries


def source_candidates(coordinate, artifacts):
    group, module, version = coordinate.split(':')
    suffix = '/' + '/'.join([group.replace('.', '/'), module, version]) + '/'
    result = set()
    for artifact in artifacts:
        for url in artifact['requestedUrls']:
            if suffix not in urlsplit(url).path:
                raise ValueError('Artifact origin no longer matches its recorded coordinate')
            result.add(safe_url(url.rsplit('/', 1)[0] + '/' + module + '-' + version + '-sources.jar'))
    return sorted(result)


def publish_source(staged, target):
    created = False
    try:
        with target.open('xb') as output:
            created = True
            staged.seek(0)
            while block := staged.read(65536):
                output.write(block)
    except BaseException:
        if created:
            target.unlink(missing_ok=True)
        raise


def fetch_source(item, directory):
    coordinate, artifacts = item
    errors = []
    empty = all(artifact['codeEntries'] == 0 for artifact in artifacts)
    opener = urllib.request.build_opener(PublicRedirect())
    for url in source_candidates(coordinate, artifacts):
        try:
            with opener.open(url, timeout=45) as response:
                with tempfile.TemporaryFile() as staged:
                    sha = hashlib.sha256(); size = 0
                    while block := response.read(65536):
                        size += len(block)
                        if size > 256 * 1024 ** 2:
                            raise ValueError('Source archive exceeds budget')
                        sha.update(block); staged.write(block)
                    staged.seek(0)
                    with zipfile.ZipFile(staged) as archive:
                        entries = archive.infolist()
                        has_code = any(entry.filename.endswith(('.java', '.kt', '.c', '.cpp', '.groovy', '.h', '.fbs', '.proto')) for entry in entries)
                        if len(entries) > 100000 or not (has_code or empty):
                            raise ValueError('The coordinate source archive lacks expected source files')
                    name = 'sources/' + hashlib.sha256(coordinate.encode()).hexdigest() + '.jar'
                    target = directory / name; target.parent.mkdir(exist_ok=True)
                    publish_source(staged, target)
            return {'coordinate': coordinate, 'url': url, 'path': name, 'bytes': size, 'sha256': sha.hexdigest(), 'emptyBinary': empty}
        except (urllib.error.URLError, TimeoutError, ValueError, zipfile.BadZipFile) as failure:
            errors.append({'url': url, 'error': str(failure)[:500]})
    if empty:
        return {'coordinate': coordinate, 'emptyBinary': True, 'codeEntries': 0, 'attempts': errors,
                'scope': 'Inspected outer and nested JARs contain no class/dex/native/wasm entries; metadata/notices retained'}
    return {'coordinate': coordinate, 'missingSource': True, 'attempts': errors}


def reused_sources(path, pin, directory):
    if path is None:
        return {}
    report = json.loads(path.read_text(encoding='utf-8'))
    if report['cachePin'] != pin:
        raise ValueError('Source reuse belongs to another public baseline')
    result = {}
    for row in report['sources']:
        if row.get('missingSource') or not row.get('path') or row.get('kind'):
            continue
        name = 'sources/' + hashlib.sha256(row['coordinate'].encode()).hexdigest() + '.jar'
        if row.get('path') != name:
            raise ValueError('Unexpected source reuse path')
        source = path.parent / name
        if source.is_symlink() or source.stat().st_size != row['bytes'] or cache.digest(source) != row['sha256']:
            raise ValueError('Existing coordinate source bytes differ')
        with source.open('rb') as staged:
            (directory / 'sources').mkdir(exist_ok=True)
            publish_source(staged, directory / name)
        result[row['coordinate']] = row
    return result


def pinned_source(row, directory, supplied):
    name = row['archive']
    if name != Path(name).name or not re.fullmatch('[a-f0-9]{40}', row['commit']):
        raise ValueError('Invalid fixed official source pin')
    target = directory / 'sources' / name
    target.parent.mkdir(exist_ok=True)
    if supplied:
        source = supplied / name
        if source.is_symlink() or source.stat().st_size != row['bytes'] or cache.digest(source) != row['sha256']:
            raise ValueError('Supplied fixed official source differs')
        with source.open('rb') as stream:
            publish_source(stream, target)
    else:
        # A fixed reviewed codeload URL is separate from Maven redirects; do not
        # widen the public repository redirect allowlist for arbitrary metadata.
        if not row['url'].startswith('https://codeload.github.com/') or not row['url'].endswith('/' + row['commit']):
            raise ValueError('Unexpected fixed official source URL')
        with urllib.request.urlopen(row['url'], timeout=60) as stream:
            with tempfile.TemporaryFile() as staged:
                total = 0
                while block := stream.read(65536):
                    total += len(block)
                    if total > row['bytes']:
                        raise ValueError('Fixed official source exceeds its pin')
                    staged.write(block)
                staged.seek(0)
                if total != row['bytes'] or hashlib.file_digest(staged, 'sha256').hexdigest() != row['sha256']:
                    raise ValueError('Fixed official source digest differs')
                publish_source(staged, target)
    return {**row, 'path': 'sources/' + name, 'kind': 'official_version_tag_archive'}


def prepare(args):
    pin = json.loads((ROOT / 'tools/apk-build/dependency-cache.json').read_text())
    if args.archive.is_symlink() or args.archive.stat().st_size != pin['bytes'] or cache.digest(args.archive) != pin['sha256']:
        raise ValueError('Fixed public dependency archive differs')
    args.directory.mkdir(parents=True, exist_ok=False)
    records, binaries = inspect(args.archive, args.directory, pin)
    sources = []
    if args.sources:
        reused = reused_sources(args.reuse_sources, pin, args.directory)
        overrides = {row['coordinate']: row for row in json.loads((ROOT / 'tools/apk-build/dependency-source-overrides.json').read_text())['sources']}
        def obtain(item):
            coordinate, _ = item
            if coordinate in reused:
                return reused[coordinate]
            if coordinate in overrides:
                return pinned_source(overrides[coordinate], args.directory, args.fallbacks)
            return fetch_source(item, args.directory)
        with ThreadPoolExecutor(max_workers=4) as pool:
            sources = list(pool.map(obtain, sorted(binaries.items())))
    report = {'format': 1, 'cachePin': pin, 'metadata': records, 'binaries': binaries, 'sources': sources,
              'binaryCoordinates': len(binaries), 'sourcesFetched': sum(bool(row.get('path')) for row in sources),
              'emptyBinaryCoordinates': sum(bool(row.get('emptyBinary')) for row in sources),
              'missingSources': [row for row in sources if row.get('missingSource')],
              'sourceLicenseClosureVerified': False,
              'scope': 'Exact public metadata/embedded notices plus coordinate-matched sources; no source execution or bit-rebuild/complete license claim'}
    declarations(args.directory, report)
    (args.directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'binaryCoordinates': len(binaries), 'metadataFiles': len(records),
                      'sourcesFetched': report['sourcesFetched'], 'missingSources': len(report['missingSources'])}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--sources', action='store_true')
    parser.add_argument('--reuse-sources', type=Path)
    parser.add_argument('--fallbacks', type=Path)
    prepare(parser.parse_args())
