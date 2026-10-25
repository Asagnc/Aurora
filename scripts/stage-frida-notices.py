#!/usr/bin/env python3
"""Stage exact source notice texts for the APK, with a reproducible source-material index."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def stage(args):
    catalog = json.loads((ROOT / 'tools/frida-sources/catalog.json').read_text(encoding='utf-8'))
    spec = importlib.util.spec_from_file_location('source_materials', ROOT / 'scripts/prepare-frida-sources.py')
    validator = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(validator)
    expected = {entry['id']: validator.material_identity(entry) for entry in catalog['repositories']}
    pins = {entry['id']: entry for entry in catalog['repositories']}
    records = []
    texts = {}
    seen = {}
    for directory in args.materials:
        report = json.loads((directory / 'materials.json').read_text(encoding='utf-8'))
        if report['failures']:
            raise ValueError('Incomplete material acquisition')
        for entry in report['repositories']:
            if entry['id'] in seen or expected.get(entry['id']) != validator.material_identity(entry) or entry['missingNoticeCandidates']:
                raise ValueError('Source notice index differs from fixed catalogue')
            seen[entry['id']] = validator.material_identity(entry)
            path = directory / entry['archive']
            if path.is_symlink() or not path.is_file():
                raise ValueError('Expected ordinary source archive')
            with path.open('rb') as incoming:
                if hashlib.file_digest(incoming, 'sha256').hexdigest() != entry['archiveSha256']:
                    raise ValueError('Source archive changed')
            _, prefixed = validator.location(pins[entry['id']])
            checked = validator.inspect(path, pins[entry['id']], prefixed)
            if checked['notices'] != entry['notices']:
                raise ValueError('Source notice metadata changed')
            with tarfile.open(path) as archive:
                # Source acquisition already validated paths/types; resolve only an indexed text.
                members = {}
                for member in archive.getmembers():
                    parts = PurePosixPath(member.name).parts
                    relative = '/'.join(parts[1:] if prefixed else parts)
                    members[relative] = member
                for notice in entry['notices']:
                    relative = notice.get('resolvedPath', notice['path'])
                    selected = members.get(relative)
                    if selected is None or not selected.isfile() or selected.size > 1048576:
                        raise ValueError('Ambiguous source notice')
                    with archive.extractfile(selected) as incoming:
                        content = incoming.read()
                    digest = hashlib.sha256(content).hexdigest()
                    if digest != notice['sha256']:
                        raise ValueError('Source notice changed')
                    texts[digest] = content
                    records.append({'source': entry['id'], **notice})
    if seen != expected:
        raise ValueError('Notice sources are incomplete')
    for entry in json.loads((ROOT / 'tools/frida-licenses/sources.json').read_text())['files']:
        content = (ROOT / 'tools/frida-licenses' / entry['file']).read_bytes()
        if hashlib.sha256(content).hexdigest() != entry['sha256']:
            raise ValueError('Companion license text changed')
        texts[entry['sha256']] = content
        records.append({'source': 'companion-license', 'path': entry['file'], 'sha256': entry['sha256']})
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    blocks = ['Aurora Frida source-material notices\n\n'
              'Frida 17.24.3 / Java bridge 7.0.13. This corpus also preserves notices from build tools and optional source files.\n'
              'Source versions, archive digests and paths are recorded in index.json and tools/frida-sources/catalog.json.\n'
              'The Java bridge declares LGPL-2.0 WITH WxWindows-exception-3.1 in package.json; its publication has no standalone license file.\n'
              'Companion LGPL and wxWindows texts below retain their own documented sources.\n'
              'Corresponding source/relink materials accompany development/release artifacts.\n']
    for digest, content in sorted(texts.items()):
        labels = sorted(record['source'] + '/' + record['path'] for record in records if record['sha256'] == digest)
        try:
            text = content.decode('utf-8')
        except UnicodeDecodeError:
            text = content.decode('latin-1')
        blocks.append('\n' + '=' * 72 + '\n' + '\n'.join(labels) + '\nSHA256 ' + digest + '\n\n' + text)
    notice_bytes = '\n'.join(blocks).encode('utf-8')
    (output / 'NOTICE.txt').write_bytes(notice_bytes)
    index = {'noticeSha256': hashlib.sha256(notice_bytes).hexdigest(), 'uniqueTexts': len(texts),
             'sourceArchives': len(seen), 'records': records}
    (output / 'index.json').write_text(json.dumps(index, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in index.items() if key != 'records'}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--materials', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    stage(parser.parse_args())
