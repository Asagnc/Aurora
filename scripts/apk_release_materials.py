"""Read-only exact SDK material inventory, shared by packaging and release validation."""
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def entries():
    rows = []
    for filename, group in [('sources.json', 'resources'), ('llvm-sources.json', 'compiler')]:
        for row in json.loads((ROOT / 'tools/apk-build' / filename).read_text())['repositories']:
            name = row['archive'] if 'archive' in row else row['id'] + '-' + row['commit'] + '.tar.gz'
            rows.append((group, name, row['archiveSha256'], row['archiveBytes']))
    for row in json.loads((ROOT / 'tools/apk-build/runtime-materials.json').read_text())['packages']:
        for item in [row['descriptor'], *row['sources']]:
            rows.append(('runtime', item['archive'], item['sha256'], item['bytes']))
    for row in json.loads((ROOT / 'tools/apk-build/host-sources.json').read_text())['sources']:
        rows.append(('host', row['archive'], row['sha256'], row['bytes']))
    return [{'group': group, 'archive': name, 'sha256': digest, 'bytes': size, 'entry': 'inputs/' + group + '/' + name}
        for group, name, digest, size in rows]


def verify_file(path, row):
    if path.is_symlink() or not path.is_file() or path.stat().st_size != row['bytes'] or sha(path) != row['sha256']:
        raise ValueError('Fixed SDK material differs: ' + path.name)


def snapshot(path):
    subprocess.run(['git', 'archive', '--format=zip', '--output', str(path), 'HEAD'], cwd=ROOT, check=True)


def verify_sources(path):
    import tempfile
    expected = entries()
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or set(names) != {'manifest.json', 'aurora-source.zip', *[row['entry'] for row in expected]}:
            raise ValueError('SDK sources contain missing, duplicate or unexpected entries')
        if archive.getinfo('manifest.json').file_size > 1024 ** 2:
            raise ValueError('SDK source manifest exceeds budget')
        manifest = json.loads(archive.read('manifest.json'))
        pin = json.loads((ROOT / 'tools/apk-build/native-bundle.json').read_text())
        if manifest['format'] != 1 or manifest['bundle'] != pin or manifest['inputs'] != expected:
            raise ValueError('SDK source inventory differs from compiled trusted inputs')
        for row in expected + [dict(manifest['auroraSource'], entry='aurora-source.zip')]:
            if archive.getinfo(row['entry']).file_size != row['bytes']:
                raise ValueError('SDK source entry size differs')
            with archive.open(row['entry']) as stream:
                if hashlib.file_digest(stream, 'sha256').hexdigest() != row['sha256']:
                    raise ValueError('SDK source entry digest differs')
        with tempfile.TemporaryDirectory(prefix='aurora-sdk-source-check-') as temporary:
            selected = Path(temporary) / 'source.zip'; snapshot(selected)
            if sha(selected) != manifest['auroraSource']['sha256']:
                raise ValueError('SDK own source snapshot differs from the trusted checkout')
    return manifest


def verify_release(apk, bundle, sources):
    pin = json.loads((ROOT / 'tools/apk-build/native-bundle.json').read_text())
    verify_file(bundle, pin)
    with zipfile.ZipFile(apk) as archive:
        entry = archive.getinfo('assets/apk-build/native-bundle.json')
        if entry.file_size > 16384 or json.loads(archive.read(entry)) != pin:
            raise ValueError('APK selects another native SDK bundle')
    manifest = verify_sources(sources)
    return {'bundle': pin, 'sourceInputs': len(manifest['inputs']), 'phoneRuntimeQualified': False,
        'scope': 'Fixed native SDK bytes and corresponding source materials; not Android runtime or full NDK equivalence proof'}
