"""Bounded snapshot of a stopped, independently seeded public Gradle module cache."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import tarfile
import uuid
from urllib.parse import unquote, urlsplit, urljoin

REPOSITORIES = ('https://dl.google.com/dl/android/maven2/', 'https://repo.maven.apache.org/maven2/',
                'https://plugins.gradle.org/m2/')
HOSTS = {'dl.google.com', 'maven.google.com', 'repo.maven.apache.org', 'plugins.gradle.org',
         'plugins-artifacts.gradle.org', 'repo.gradle.org'}
LIMIT = 2 * 1024 ** 3


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def requested_urls(log):
    result = set()
    for value in re.findall(r'https://[^\s\[\],<>"\)]+', log):
        value = value.rstrip('.')
        parsed = urlsplit(value)
        if parsed.hostname in HOSTS and not parsed.username and not parsed.password and not parsed.query and not parsed.fragment:
            result.add(value)
    return sorted(result)


def stable(a, b):
    return (a.st_dev, a.st_ino, a.st_size, a.st_mode, a.st_nlink, a.st_mtime_ns, a.st_ctime_ns) == (
        b.st_dev, b.st_ino, b.st_size, b.st_mode, b.st_nlink, b.st_mtime_ns, b.st_ctime_ns)


def copy_checked(source, target):
    before = source.lstat()
    if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1 or before.st_size > 512 * 1024 ** 2:
        raise ValueError('Cache entry is not a bounded ordinary single-link file')
    fd = os.open(source, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
    target.parent.mkdir(parents=True, exist_ok=True)
    try:
        if not stable(before, os.fstat(fd)):
            raise ValueError('Cache entry changed before copy')
        sha256, sha1, total = hashlib.sha256(), hashlib.sha1(), 0
        with os.fdopen(fd, 'rb', closefd=False) as incoming, target.open('xb') as outgoing:
            while block := incoming.read(65536):
                total += len(block)
                if total > before.st_size:
                    raise ValueError('Cache entry grew during copy')
                sha256.update(block); sha1.update(block); outgoing.write(block)
        if total != before.st_size or not stable(before, os.fstat(fd)) or not stable(before, source.lstat()):
            raise ValueError('Cache entry changed during copy')
        return {'bytes': total, 'sha256': sha256.hexdigest()}, sha1.hexdigest()
    finally:
        os.close(fd)


def artifact_origins(name, record, inventory, target, urls):
    group, module, version = record['coordinate'].split(':')
    prefix = '/' + '/'.join([group.replace('.', '/'), module, version]) + '/'
    direct = [url for url in urls if unquote(urlsplit(url).path).endswith(prefix + Path(name).name)]
    if direct:
        return direct
    # Gradle module metadata can deliberately rename a downloaded artifact in
    # files-2.1 (e.g. animation-core.aar). Require the exact public .module and
    # its declared URL, size and digest rather than guessing a Maven filename.
    for metadata_name, metadata_record in inventory.items():
        if not metadata_name.endswith('.module') or metadata_record.get('coordinate') != record['coordinate']:
            continue
        origins = [url for url in urls if unquote(urlsplit(url).path).endswith(prefix + Path(metadata_name).name)]
        if not origins or metadata_record['bytes'] > 16 * 1024 ** 2:
            continue
        metadata = json.loads((target / metadata_name).read_text(encoding='utf-8'))
        component = metadata.get('component', {})
        if component.get('group') != group or component.get('version') != version:
            raise ValueError('Public Gradle module identity differs')
        if component.get('module') != module:
            # Multiplatform publications identify their original component and
            # link back to its public metadata from the platform publication.
            original = component.get('url', '')
            parsed = urlsplit(original)
            original_module = component.get('module', '')
            suffix = '/' + '/'.join([group.replace('.', '/'), original_module, version,
                                     original_module + '-' + version + '.module'])
            if (not original or parsed.scheme or parsed.netloc or parsed.query or parsed.fragment
                    or not any(urljoin(origin, original) in urls and
                               unquote(urlsplit(urljoin(origin, original)).path).endswith(suffix) for origin in origins)):
                raise ValueError('Original multiplatform component lacks public request evidence')
        for variant in metadata.get('variants', []):
            for file in variant.get('files', []):
                if file.get('name') != Path(name).name:
                    continue
                declared = file.get('url', '')
                parsed = urlsplit(declared)
                if not declared or parsed.scheme or parsed.netloc or parsed.query or parsed.fragment:
                    raise ValueError('Unexpected artifact relocation in the public module')
                matched = [urljoin(origin, declared) for origin in origins if urljoin(origin, declared) in urls]
                if not matched:
                    continue
                if file.get('size') != record['bytes'] or file.get('sha256') != record['sha256']:
                    raise ValueError('Renamed public artifact metadata digest differs')
                record['moduleMetadata'] = {'path': metadata_name, 'sha256': metadata_record['sha256']}
                return matched
    raise ValueError('Artifact lacks public seed request evidence: ' + name)


def snapshot(source, target, context, log):
    if source.is_symlink() or not source.is_dir() or target.exists():
        raise ValueError('Expected an independent cache and a fresh snapshot directory')
    source = source.resolve()
    target.mkdir()
    inventory, total, artifacts = {}, 0, 0
    urls = requested_urls(log)
    entries = 0
    for path in source.rglob('*'):
        entries += 1
        if entries > 128000:
            raise ValueError('Cache tree exceeds its entry budget')
        relative = path.relative_to(source)
        if any(not part or part in {'.', '..'} or '\\' in part or ':' in part for part in relative.parts):
            raise ValueError('Unsafe cache path')
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode):
            raise ValueError('Cache links are forbidden')
        if stat.S_ISDIR(info.st_mode):
            continue
        if path.name.endswith('.lock') or path.name == 'gc.properties':
            continue
        if total + info.st_size > LIMIT or len(inventory) >= 64000:
            raise ValueError('Public cache exceeds its resource budget')
        record, sha1 = copy_checked(path, target / 'modules-2' / relative)
        if relative.parts[0] == 'files-2.1':
            # Gradle 9.8.2 GroupedAndNamedUniqueFileStore.stripLeadingZeros
            # retains the historical non-padded SHA1 path representation.
            if (len(relative.parts) != 6 or not re.fullmatch('[1-9a-f][0-9a-f]{0,39}', relative.parts[4])
                    or sha1.lstrip('0') != relative.parts[4]):
                raise ValueError('Artifact does not match its Gradle content address: ' + relative.as_posix())
            group, module, version, _, filename = relative.parts[1:]
            record['coordinate'] = f'{group}:{module}:{version}'
            artifacts += 1
        else:
            record['kind'] = 'Gradle internal metadata from the isolated public seed'
        inventory['modules-2/' + relative.as_posix()] = record
        total += record['bytes']
        if total > LIMIT or len(inventory) > 64000:
            raise ValueError('Public cache exceeds its resource budget')
    if not artifacts:
        raise ValueError('An empty public artifact cache is not a baseline')
    for name, record in inventory.items():
        if 'coordinate' in record:
            record['requestedUrls'] = artifact_origins(name, record, inventory, target, urls)
    manifest = {'format': 1, 'scope': 'public Android probe dependencies only', 'gradleVersion': '9.8.2',
                'repositories': list(REPOSITORIES), 'seed': context, 'files': inventory,
                'fileCount': len(inventory), 'artifactFiles': artifacts, 'bytes': total,
                'sourceLicenseClosureVerified': False, 'phoneRuntimeQualified': False}
    (target / 'manifest.json').write_text(json.dumps(manifest, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    verify(target)
    return manifest


def verify(root):
    header = root / 'manifest.json'
    info = header.lstat()
    if root.is_symlink() or not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > 32 * 1024 ** 2:
        raise ValueError('Invalid cache manifest inode')
    manifest = json.loads(header.read_text(encoding='utf-8'))
    if {path.name for path in root.iterdir()} != {'manifest.json', 'modules-2'}:
        raise ValueError('Unexpected cache root entry')
    if (root / 'modules-2').is_symlink() or not (root / 'modules-2').is_dir():
        raise ValueError('Invalid module cache root')
    if manifest['format'] != 1 or manifest['gradleVersion'] != '9.8.2' or manifest['repositories'] != list(REPOSITORIES):
        raise ValueError('Incompatible public cache manifest')
    if not 1 <= manifest['fileCount'] <= 64000 or not 0 < manifest['bytes'] <= LIMIT:
        raise ValueError('Invalid cache budget')
    for name, record in manifest['files'].items():
        if not name.startswith('modules-2/') or any(p in {'', '.', '..'} or '\\' in p or ':' in p for p in name.split('/')):
            raise ValueError('Unsafe cache inventory path')
        if not re.fullmatch('[0-9a-f]{64}', record['sha256']) or not 0 <= record['bytes'] <= 512 * 1024 ** 2:
            raise ValueError('Invalid cache file record')
    actual = set()
    entries = 0
    for path in (root / 'modules-2').rglob('*'):
        entries += 1
        if entries > 128000:
            raise ValueError('Cache tree exceeds its entry budget')
        info = path.lstat()
        if stat.S_ISLNK(info.st_mode) or not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode)):
            raise ValueError('Unexpected cache inode')
        if not stat.S_ISREG(info.st_mode):
            continue
        name = path.relative_to(root).as_posix(); actual.add(name)
        record = manifest['files'].get(name)
        if info.st_nlink != 1 or not record or info.st_size != record['bytes'] or digest(path) != record['sha256']:
            raise ValueError('Cache inventory mismatch')
    if actual != set(manifest['files']) or len(actual) != manifest['fileCount'] or sum(r['bytes'] for r in manifest['files'].values()) != manifest['bytes']:
        raise ValueError('Incomplete cache inventory')
    return manifest


def export(root, output):
    manifest = verify(root)
    if output.exists():
        raise FileExistsError(output)
    temporary = output.with_name('.' + output.name + '-' + uuid.uuid4().hex)
    try:
        with temporary.open('xb') as stream:
            with gzip.GzipFile(fileobj=stream, mode='wb', filename='', mtime=0) as compressed:
                with tarfile.open(fileobj=compressed, mode='w', format=tarfile.PAX_FORMAT) as archive:
                    for name in ['manifest.json', *sorted(manifest['files'])]:
                        path = root / name
                        info = archive.gettarinfo(str(path), 'dependency-cache/' + path.relative_to(root).as_posix())
                        info.uid = info.gid = info.mtime = 0; info.uname = info.gname = ''; info.mode = 0o444; info.pax_headers = {}
                        with path.open('rb') as incoming:
                            archive.addfile(info, incoming)
            stream.flush(); os.fsync(stream.fileno())
        verify(root)
        os.link(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    if os.name != 'nt':
        fd = os.open(output.parent, os.O_RDONLY | os.O_DIRECTORY)
        try: os.fsync(fd)
        finally: os.close(fd)
    return {'archive': output.name, 'sha256': digest(output), 'bytes': output.stat().st_size,
            'manifestSha256': digest(root / 'manifest.json'), 'artifactFiles': manifest['artifactFiles']}
