#!/usr/bin/env python3
"""Prepare a candidate SDK archive only from an explicitly pinned successful cache qualification.

This creates review material; it never edits the production catalog or installs a phone version.
The caller must separately verify the qualification run's GitHub commit/workflow provenance.
"""
import argparse
import copy
import gzip
import hashlib
import json
import os
from pathlib import Path
import stat
import tarfile
import tempfile
import uuid
import apk_dependency_cache as cache

NATIVE_SHA = 'f899eda7aee47fe3edc11546f26d7853aa6f8962f08bb2225f59a327b2327042'


def ordinary(path, maximum):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or not 0 < info.st_size <= maximum:
        raise ValueError('Expected a bounded ordinary single-link input')
    return info


def canonical(name):
    if not name or any(part in {'', '.', '..'} or '\\' in part or ':' in part for part in name.split('/')):
        raise ValueError('Unsafe candidate archive path')
    return name


def append_native(archive, native, seen):
    expanded = 0
    with tarfile.open(native, 'r:gz') as source:
        for member in source:
            name = canonical(member.name.rstrip('/'))
            if not (name == 'android-host' or name.startswith('android-host/')) or name in seen:
                raise ValueError('Unexpected fixed native archive layout')
            if not (member.isfile() or member.isdir() or member.issym() or member.islnk()) or member.issparse():
                raise ValueError('Unexpected native archive entry kind')
            if not 0 <= member.size <= 512 * 1024 ** 2:
                raise ValueError('Native entry exceeds budget')
            expanded += member.size; seen.add(name)
            if len(seen) > 100000 or expanded > 3 * 1024 ** 3:
                raise ValueError('Candidate archive exceeds budget')
            archive.addfile(copy.copy(member), source.extractfile(member) if member.isfile() else None)
    return expanded


def copy_cache_entry(source, archive, member, record):
    digest = hashlib.sha256()
    with tempfile.TemporaryFile() as staged:
        with source.extractfile(member) as incoming:
            while block := incoming.read(65536):
                digest.update(block); staged.write(block)
        if digest.hexdigest() != record['sha256']:
            raise ValueError('Cache entry differs from manifest')
        output = copy.copy(member)
        output.name = 'android-host/' + member.name; output.uid = output.gid = output.mtime = 0
        output.uname = output.gname = ''; output.mode = 0o444; output.pax_headers = {}
        staged.seek(0)
        archive.addfile(output, staged)


def append_cache(archive, source_path, report, seen):
    with tarfile.open(source_path, 'r|gz') as source:
        header = source.next()
        if header is None or header.name != 'dependency-cache/manifest.json':
            raise ValueError('The qualified cache must begin with its manifest')
        if not header.isfile() or not 0 < header.size <= 32 * 1024 ** 2:
            raise ValueError('Invalid cache manifest entry')
        data = source.extractfile(header).read()
        if hashlib.sha256(data).hexdigest() != report['archive']['manifestSha256']:
            raise ValueError('Cache manifest differs from pinned qualification')
        manifest = json.loads(data)
        expected = {'dependency-cache/' + name: value for name, value in manifest['files'].items()}
        expected['dependency-cache/manifest.json'] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        if manifest['format'] != 1 or manifest['gradleVersion'] != '9.8.2' or len(expected) > 64001:
            raise ValueError('Unexpected cache manifest identity or budget')
        emitted = set(); total = 0
        member = header
        # The manifest was already read; emit its exact bytes before streaming
        # each remaining entry once. Never seek backward through a large gzip.
        import io
        manifest_output = copy.copy(header)
        manifest_output.name = 'android-host/' + header.name
        archive.addfile(manifest_output, io.BytesIO(data))
        emitted.add(header.name); seen.add(manifest_output.name); total += len(data)
        while (member := source.next()) is not None:
            name = canonical(member.name)
            record = expected.get(name)
            if name in emitted or not record or not member.isfile() or member.issparse() or member.size != record['bytes']:
                raise ValueError('Unexpected cache archive entry')
            if not 0 <= member.size <= 512 * 1024 ** 2:
                raise ValueError('Cache entry exceeds budget')
            target = 'android-host/' + name
            if target in seen:
                raise ValueError('Native and cache paths overlap')
            emitted.add(name); seen.add(target); total += member.size
            if total > cache.LIMIT + 32 * 1024 ** 2:
                raise ValueError('Cache archive exceeds budget')
            copy_cache_entry(source, archive, member, record)
        if emitted != set(expected):
            raise ValueError('Cache archive is incomplete')
    return total


def prepare(args):
    ordinary(args.native, 512 * 1024 ** 2); ordinary(args.qualification, 1024 ** 2)
    if cache.digest(args.native) != NATIVE_SHA or cache.digest(args.qualification) != args.qualification_sha256:
        raise ValueError('Fixed native or explicitly pinned qualification report differs')
    report = json.loads(args.qualification.read_text())
    if (not report['qualified'] or len(report['freshProbes']) != 2 or not report['readonlyWriteRejected']
            or not report['missingArtifactFallback']['onlinePrivateWritePassed']):
        raise ValueError('The complete cache mechanism qualification has not passed')
    source = args.qualification.parent / report['archive']['archive']
    ordinary(source, cache.LIMIT)
    if source.parent != args.qualification.parent or cache.digest(source) != report['archive']['sha256']:
        raise ValueError('Qualified cache archive differs')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name('.' + args.output.name + '-' + uuid.uuid4().hex)
    seen = set()
    try:
        with temporary.open('xb') as stream:
            with gzip.GzipFile(fileobj=stream, mode='wb', filename='', mtime=0) as compressed:
                with tarfile.open(fileobj=compressed, mode='w', format=tarfile.PAX_FORMAT) as archive:
                    native_bytes = append_native(archive, args.native, seen)
                    cache_bytes = append_cache(archive, source, report, seen)
            stream.flush(); os.fsync(stream.fileno())
        if cache.digest(args.native) != NATIVE_SHA or cache.digest(source) != report['archive']['sha256']:
            raise ValueError('An input changed while building the candidate')
        os.link(temporary, args.output)
    finally:
        temporary.unlink(missing_ok=True)
    print(json.dumps({'archive': args.output.name, 'sha256': cache.digest(args.output), 'bytes': args.output.stat().st_size,
        'expandedBytes': native_bytes + cache_bytes, 'files': len(seen), 'nativeSdkSha256': NATIVE_SHA,
        'dependencyArchiveSha256': report['archive']['sha256'], 'dependencyManifestSha256': report['archive']['manifestSha256'],
        'qualificationSha256': args.qualification_sha256, 'installed': False, 'productionCatalogChanged': False,
        'sourceLicenseClosureVerified': False}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native', type=Path, required=True)
    parser.add_argument('--qualification', type=Path, required=True)
    parser.add_argument('--qualification-sha256', required=True)
    parser.add_argument('--output', type=Path, required=True)
    prepare(parser.parse_args())
