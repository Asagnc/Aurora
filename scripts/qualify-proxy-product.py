#!/usr/bin/env python3
"""Run immutable production proxy profiles against owned endpoints in a private ARM64 Linux namespace."""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import socket
import subprocess
import tarfile
import uuid

ROOT = Path(__file__).resolve().parents[1]


def profiles():
    source = (ROOT / 'core/src/main/kotlin/io/github/asagnc/aurora/core/NetworkProxyRuntime.kt').read_text()
    rows = re.findall(r'"([A-Za-z0-9_.-]+)" to "([a-f0-9]{64})"', source)
    if len(rows) != 8 or len({name for name, _ in rows}) != 8:
        raise ValueError('Compiled proxy profile inventory differs')
    return dict(rows)


def fixed_config(port):
    def rule(stage, path='/', headers=None, body=None, direction=None):
        return dict(stage=stage, host='127.0.0.1', pathPrefix=path, method=None, headers=headers or {},
            bodyBase64=None if body is None else base64.b64encode(body).decode(), direction=direction)
    return dict(port=port, rules=[rule('request', headers={'X-Aurora-Fixture': 'modified'}),
        rule('response', '/owned-http', {'X-Aurora-Response': 'modified'}, b'owned-modified'),
        rule('websocket', '/owned-ws', body=b'owned-ws-modified', direction='server')],
        durationSeconds=180, maxFlows=8, maxStoredBytes=8 * 1024 ** 2, maxBodyBytes=1024 ** 2, protocol=1)


def prepare(directory):
    spec = importlib.util.spec_from_file_location('proxy_fixed', ROOT / 'scripts/qualify-apk-jvm.py')
    helper = importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)
    pin = json.loads((ROOT / 'tools/network-proxy/catalog.json').read_text())
    archive = helper.acquire(pin, directory)
    stage = directory / 'stage'; stage.mkdir()
    target = stage / 'mitmdump'; target.mkdir()
    with tarfile.open(archive) as source:
        items = source.getmembers()
        if {item.name for item in items} != set(pin['members']) or len(items) != 3 or any(not item.isfile() for item in items):
            raise ValueError('Fixed standalone archive shape differs')
        member = source.getmember('mitmdump')
        if member.size != pin['members']['mitmdump']['bytes']:
            raise ValueError('Selected standalone length differs')
        with source.extractfile(member) as incoming, (target / 'mitmdump').open('xb') as output:
            shutil.copyfileobj(incoming, output, 65536)
    helper.helper().verify(target / 'mitmdump', pin['members']['mitmdump']['sha256'], pin['members']['mitmdump']['bytes'])
    (target / 'mitmdump').chmod(0o755)
    selected = profiles()
    for name, expected in selected.items():
        source = ROOT / 'tools/network-proxy' / ('mitmproxy-LICENSE' if name == 'mitmproxy-LICENSE' else 'runtime/' + name)
        if source.is_symlink() or hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise ValueError('Production proxy source differs from compiled pin')
        shutil.copyfile(source, target / name); (target / name).chmod(0o755 if name.startswith('aurora-') else 0o644)
    return stage, pin, selected


def execute(argv, log):
    child = subprocess.Popen(argv, start_new_session=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    try:
        output, _ = child.communicate(timeout=210)
    except BaseException:
        try:
            os.killpg(child.pid, signal.SIGTERM); child.wait(timeout=10)
        except ProcessLookupError:
            pass
        except subprocess.TimeoutExpired:
            os.killpg(child.pid, signal.SIGKILL); child.wait(timeout=10)
        raise
    log.write_bytes(output[:65536])
    if child.returncode != 0 or len(output) > 65536:
        raise ValueError('Production proxy process did not complete within bounded output')
    return output.decode()


def verify_probe(public, identity, port, encoded, output):
    final = json.loads((public / 'final.json').read_text())
    if output.splitlines().count('aurora-proxy-probe-ok') != 1 or final['reason'] != 'probe_complete' or not final['probe']:
        raise ValueError('Production HTTP/TLS/WebSocket probe did not qualify')
    if final['configSha256'] != hashlib.sha256(base64.b64decode(encoded)).hexdigest() or final['id'] != identity or final['port'] != port:
        raise ValueError('Final proxy record belongs to another request')
    lines = [json.loads(line) for line in (public / 'index.jsonl').read_text().splitlines()]
    messages = [row for row in lines if row['type'] == 'websocket']
    if len(messages) != 1:
        raise ValueError('Missing exact owned stored WebSocket message')
    row = messages[0]; data = (public / row['file']).read_bytes()
    if data != b'owned-ws-modified' or hashlib.sha256(data).hexdigest() != row['sha256']:
        raise ValueError('Stored WebSocket bytes differ from the actual forwarded replacement')
    return final


def inspect_outputs(directory, public, identity):
    read_output = execute(['/opt/mitmdump/aurora-proxy-read', str(uuid.uuid4()), identity, '0', '10'], directory / 'product-read.txt')
    frames = [line.removeprefix('aurora-proxy-read:') for line in read_output.splitlines() if line.startswith('aurora-proxy-read:')]
    if len(frames) != 1:
        raise ValueError('Missing production offline inspection result')
    inspection = json.loads(frames[0])
    if not inspection.get('complete') or inspection.get('parseError') or len(inspection['flows']) < 3:
        raise ValueError('Production offline reader did not accept the complete stored owned flows')
    exported = directory / 'public-artifacts'; exported.mkdir()
    for path in public.iterdir():
        if not path.is_file() or path.is_symlink() or path.name.endswith('.key') or b'PRIVATE KEY' in path.read_bytes()[:16384]:
            raise ValueError('Only regular public owned artifacts may be collected')
        shutil.copyfile(path, exported / path.name)
    return inspection


def qualify(directory):
    if os.geteuid() != 0 or platform.machine() not in {'aarch64', 'arm64'} or platform.system() != 'Linux':
        raise ValueError('Requires a real ARM64 private Root Linux mount/network namespace')
    if not directory.is_dir() or (directory / 'stage').exists():
        raise ValueError('Select a new owned qualification directory')
    stage, pin, selected = prepare(directory)
    workspace = directory / 'workspace'; workspace.mkdir()
    subprocess.run(['ip', 'link', 'set', 'lo', 'up'], check=True)
    subprocess.run(['mount', '--bind', str(stage), '/opt'], check=True)
    subprocess.run(['mount', '-o', 'remount,bind,ro', '/opt'], check=True)
    subprocess.run(['mount', '--bind', str(workspace), '/mnt'], check=True)
    with socket.socket() as reserved:
        reserved.bind(('127.0.0.1', 0)); port = reserved.getsockname()[1]
    identity = str(uuid.uuid4()); config = fixed_config(port)
    encoded = base64.b64encode(json.dumps(config, separators=(',', ':')).encode()).decode()
    output = execute(['/opt/mitmdump/aurora-proxy-probe', identity, str(port), encoded], directory / 'product-probe.txt')
    public = workspace / 'project/.aurora-proxy' / identity
    final = verify_probe(public, identity, port, encoded, output)
    inspection = inspect_outputs(directory, public, identity)
    report = {'productionProfiles': selected, 'archiveSha256': pin['sha256'], 'final': final, 'inspection': inspection,
        'ownedHttpHttpsWebsocketQualified': True, 'untrustedCaRejected': True, 'publicCaOnly': True, 'androidRuntimeQualified': False,
        'scope': 'Actual immutable production profiles and selected pinned standalone binary in a readonly private ARM64 Linux namespace; not Android Binder/PTY/cgroup qualification'}
    (directory / 'product-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'qualified': True, 'androidRuntimeQualified': False}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    qualify(parser.parse_args().directory.resolve())
