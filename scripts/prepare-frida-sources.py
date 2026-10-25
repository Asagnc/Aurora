#!/usr/bin/env python3
"""Acquire fixed upstream source archives, without extracting or executing them.

This produces source materials, not a claim of complete runtime/license closure.
Only complete bounded downloads publish; failed owned .partial files are removed.
"""
import argparse
import base64
import concurrent.futures
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import posixpath
import ssl
import tarfile
import time
import urllib.parse
import urllib.request
import urllib.error

LIMIT = 256 * 1024 * 1024


def location(entry):
    if entry.get('kind') == 'npm':
        url = urllib.parse.urlparse(entry['url'])
        if url.scheme != 'https' or url.hostname != 'registry.npmjs.org' or url.query or url.fragment:
            raise ValueError('Unexpected npm source URL')
        if not entry.get('integrity', '').startswith('sha512-'):
            raise ValueError('npm source requires SHA512 integrity')
        base64.b64decode(entry['integrity'][7:], validate=True)
        return entry['url'], True
    repository = urllib.parse.urlparse(entry['repository'])
    commit = entry['commit']
    if not re.fullmatch('[a-f0-9]{40}', commit) or repository.scheme != 'https' or repository.query or repository.fragment:
        raise ValueError('Expected a fixed HTTPS Git commit')
    if repository.hostname == 'github.com':
        parts = repository.path.strip('/').removesuffix('.git').split('/')
        if len(parts) != 2 or not all(re.fullmatch('[A-Za-z0-9_.-]+', part) for part in parts):
            raise ValueError('Invalid GitHub repository')
        return 'https://codeload.github.com/' + '/'.join(parts) + '/tar.gz/' + commit, True
    if repository.hostname in {'chromium.googlesource.com', 'android.googlesource.com'} and '..' not in repository.path.split('/'):
        subtree = entry.get('subdirectory', '')
        if subtree and (not re.fullmatch('[A-Za-z0-9_./-]+', subtree) or '..' in subtree.split('/') or subtree.startswith('/')):
            raise ValueError('Unsafe source subtree')
        suffix = '/' + subtree if subtree else ''
        return entry['repository'].removesuffix('/') + '/+archive/' + commit + suffix + '.tar.gz', False
    raise ValueError('Unsupported source host')


def inspect(path, entry, prefixed):
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    if entry.get('kind') == 'npm':
        with path.open('rb') as stream:
            integrity = hashlib.file_digest(stream, 'sha512').digest()
        if integrity != base64.b64decode(entry['integrity'][7:], validate=True):
            raise ValueError('npm source integrity mismatch')
    expected = entry.get('archiveSha256')
    if expected is not None and digest != expected:
        raise ValueError('Source archive digest differs from pinned material')
    notices = []
    names = set()
    members = {}
    total = 0
    candidates = {item['path']: item for item in entry.get('noticeCandidates', [])}
    with tarfile.open(path, 'r:gz') as archive:
        for member in archive:
            pure = PurePosixPath(member.name)
            if pure.is_absolute() or '..' in pure.parts or '\\' in member.name:
                raise ValueError('Unsafe source archive member')
            parts = pure.parts[1:] if prefixed else pure.parts
            if not parts:
                continue
            name = '/'.join(parts)
            if name in names:
                raise ValueError('Duplicate source archive member')
            names.add(name)
            members[name] = member
            total += member.size
            if len(names) > 250000 or total > 2 * 1024 * 1024 * 1024:
                raise ValueError('Source expansion exceeds audit budget')
            if not member.isfile():
                if not (member.isdir() or member.issym() or member.islnk()):
                    raise ValueError('Unexpected special source member')
                continue
            candidate = candidates.get(name)
            if candidate is None and not pure.name.lower().startswith(('copying', 'license', 'licence', 'notice', 'copyright')):
                continue
            if member.size > 1048576:
                raise ValueError('Unexpected oversized notice')
            with archive.extractfile(member) as stream:
                content = stream.read(1048577)
            blob = hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest()
            if candidate is not None and blob != candidate['gitBlobSha1']:
                raise ValueError('Notice differs from pinned Git blob: ' + name)
            notices.append({'path': name, 'bytes': len(content), 'gitBlobSha1': blob,
                            'sha256': hashlib.sha256(content).hexdigest()})
        notices.extend(linked_notices(archive, members, candidates))
    missing = sorted(set(candidates) - {notice['path'] for notice in notices})
    return {**entry, 'archive': path.name, 'archiveSha256': digest, 'archiveBytes': path.stat().st_size,
            'entries': len(names), 'expandedBytes': total, 'notices': notices, 'missingNoticeCandidates': missing}


def linked_notices(archive, members, candidates):
    notices = []
    for name, candidate in candidates.items():
        member = members.get(name)
        if member is None or not member.issym():
            continue
        link = member.linkname.encode()
        blob = hashlib.sha1(b'blob ' + str(len(link)).encode() + b'\0' + link).hexdigest()
        if blob != candidate['gitBlobSha1']:
            raise ValueError('Notice link differs from pinned Git blob')
        target = posixpath.normpath(posixpath.join(posixpath.dirname(name), member.linkname))
        if target.startswith('/') or target == '..' or target.startswith('../'):
            raise ValueError('Notice link escapes source archive')
        resolved = members.get(target)
        if resolved is None or not resolved.isfile() or resolved.size > 1048576:
            raise ValueError('Notice link does not resolve to bounded archive text')
        with archive.extractfile(resolved) as stream:
            content = stream.read(1048577)
        notices.append({'path': name, 'resolvedPath': target, 'bytes': len(content),
                        'gitBlobSha1': blob, 'sha256': hashlib.sha256(content).hexdigest()})
    return notices


def acquire(entry, directory):
    name = entry['id']
    if not re.fullmatch('[A-Za-z0-9_.-]+', name):
        raise ValueError('Unsafe source ID')
    url, prefixed = location(entry)
    pin = entry.get('commit', entry.get('version', ''))
    if not re.fullmatch('[A-Za-z0-9.-]+', pin):
        raise ValueError('Unsafe source version')
    target = directory / (name + '-' + pin + '.tar.gz')
    if target.exists():
        if 'archiveSha256' not in entry:
            raise ValueError('Cached source needs a pinned archive digest from the prior materials report')
        if target.is_symlink() or not target.is_file() or not 0 < target.stat().st_size <= LIMIT:
            raise ValueError('Unsafe cached source archive')
        return inspect(target, entry, prefixed)
    partial = target.with_suffix(target.suffix + '.partial')
    owned = False
    try:
        started = time.monotonic()
        with partial.open('xb') as output:
            owned = True
            with urllib.request.urlopen(url, timeout=30) as incoming:
                total = 0
                while block := incoming.read(65536):
                    total += len(block)
                    if total > LIMIT or time.monotonic() - started > 300:
                        raise ValueError('Source download budget exceeded')
                    output.write(block)
        result = inspect(partial, entry, prefixed)
        partial.replace(target)
        return {**result, 'archive': target.name}
    except BaseException:
        if owned:
            partial.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--directory', type=Path, required=True)
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text(encoding='utf-8'))
    entries = catalog['repositories']
    if len(entries) > 128 or len({entry['id'] for entry in entries}) != len(entries):
        raise ValueError('Invalid bounded catalogue')
    args.directory.mkdir(parents=True, exist_ok=True)
    results, failures = [], []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        pending = {pool.submit(acquire_with_retries, entry, args.directory): entry['id'] for entry in entries}
        for task in concurrent.futures.as_completed(pending):
            name = pending[task]
            try:
                result = task.result()
                results.append(result)
                print(name, result['archiveBytes'], 'bytes', flush=True)
            except Exception as failure:
                failures.append({'id': name, 'error': str(failure)})
                print(name, 'FAILED', str(failure), flush=True)
    report = {'scope': catalog['scope'], 'sourceClosureVerified': False,
              'repositories': sorted(results, key=lambda entry: entry['id']), 'failures': failures}
    args.directory.joinpath('materials.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    if failures:
        raise SystemExit('Source acquisition incomplete; see materials.json')


def acquire_with_retries(entry, directory):
    for attempt in range(3):
        try:
            return acquire(entry, directory)
        except (urllib.error.URLError, TimeoutError, ConnectionError, ssl.SSLError):
            if attempt == 2:
                raise


if __name__ == '__main__':
    main()
