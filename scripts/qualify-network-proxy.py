#!/usr/bin/env python3
"""Real ARM64 standalone HTTP/TLS/WebSocket fixture, using only owned loopback endpoints."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import platform
import selectors
import socket
import subprocess
import tarfile
import tempfile
import time
from network_proxy_fixture import ADDON, origins, http_fixtures, websocket_fixture

ROOT = Path(__file__).resolve().parents[1]


def stop(child, log):
    if child.poll() is None:
        child.terminate()
    forced = False
    try:
        output, _ = child.communicate(timeout=15)
    except subprocess.TimeoutExpired:
        forced = True; child.kill(); output, _ = child.communicate(timeout=5)
    with log.open('ab') as stream:
        stream.write(output[:65536])
    if forced or len(output) > 65536:
        raise ValueError('Proxy did not stop cooperatively within bounded output')


def ready(child, log, marker=b'AURORA_PROXY_READY\n'):
    deadline = time.monotonic() + 30; output = bytearray()
    with selectors.DefaultSelector() as selector:
        selector.register(child.stdout, selectors.EVENT_READ)
        while time.monotonic() < deadline:
            if not selector.select(max(0, deadline - time.monotonic())):
                break
            data = os.read(child.stdout.fileno(), 4096)
            if not data:
                break
            output.extend(data)
            if len(output) > 65536:
                break
            if marker in output:
                with log.open('ab') as stream:
                    stream.write(output)
                return
    with log.open('ab') as stream:
        stream.write(output)
    raise ValueError('Owned proxy startup was not confirmed')


def offline(binary, environment, work, dump):
    script = work / 'read-owned.py'
    script.write_text('''
from mitmproxy import ctx
from mitmproxy.io import FlowReader
import json
def running():
    http = 0; websocket = 0
    with open(''' + repr(str(dump)) + ''', 'rb') as stream:
        for flow in FlowReader(stream).stream():
            request = getattr(flow, 'request', None); response = getattr(flow, 'response', None)
            if request and response and request.path == '/fixture' and response.status_code == 200:
                assert request.headers['X-Aurora-Fixture'] == 'modified'
                assert response.content == b'owned-modified'
                http += 1
            ws = getattr(flow, 'websocket', None)
            if ws and any(message.content == b'owned-ws-modified' for message in ws.messages):
                websocket += 1
    print('AURORA_FLOWS ' + json.dumps({'http':http,'websocket':websocket}), flush=True)
    ctx.master.shutdown()
''')
    result = subprocess.run([str(binary), '-q', '-n', '--set', 'confdir=' + str(work / 'config'), '-s', str(script)],
                            env=environment, capture_output=True, text=True, timeout=30, check=True)
    lines = [line.removeprefix('AURORA_FLOWS ') for line in result.stdout.splitlines() if line.startswith('AURORA_FLOWS ')]
    if len(lines) != 1 or json.loads(lines[0]) != {'http': 2, 'websocket': 1}:
        raise ValueError('Completed flow archive failed offline correspondence checks')


def qualify(directory):
    if platform.system() != 'Linux' or platform.machine().lower() not in {'arm64', 'aarch64'}:
        raise ValueError('Requires a real Linux ARM64 host')
    directory = directory.resolve(); directory.mkdir(parents=True, exist_ok=True)
    if (directory / 'runtime-report.json').exists():
        raise FileExistsError('Preserve completed qualification; choose a new output directory')
    spec = importlib.util.spec_from_file_location('preparation', ROOT / 'scripts/prepare-network-proxy.py')
    preparation = importlib.util.module_from_spec(spec); spec.loader.exec_module(preparation)
    preparation.prepare(directory)
    pin = json.loads((ROOT / 'tools/network-proxy/catalog.json').read_text())
    with tempfile.TemporaryDirectory(prefix='.owned-proxy-', dir=directory) as temporary:
        work = Path(temporary); binary = work / 'mitmdump'
        with tarfile.open(directory / pin['archive']) as archive:
            data = archive.extractfile('mitmdump').read()
        if hashlib.sha256(data).hexdigest() != pin['members']['mitmdump']['sha256']:
            raise ValueError('Selected executable changed before launch')
        binary.write_bytes(data); binary.chmod(0o755)
        environment = os.environ.copy()
        for name in ['PYTHONPATH', 'PYTHONHOME', 'LD_PRELOAD']:
            environment.pop(name, None)
        for name in ['HOME', 'TMPDIR']:
            path = work / name.lower(); path.mkdir(); environment[name] = str(path)
        version = subprocess.check_output([str(binary), '--version'], env=environment, text=True, timeout=30)
        (directory / 'version.txt').write_text(version)
        if '12.3.0' not in version:
            raise ValueError('Standalone launch differs from fixed version')
        script = work / 'owned-rules.py'; script.write_text(ADDON)
        dump = work / 'owned.flows'
        with socket.socket() as reserved:
            reserved.bind(('127.0.0.1', 0)); port = reserved.getsockname()[1]
        with origins(work) as selected:
            command = [str(binary), '-q', '--listen-host', '127.0.0.1', '--listen-port', str(port),
                '--set', 'confdir=' + str(work / 'config'), '--set', 'ssl_verify_upstream_trusted_ca=' + str(selected['ca']),
                '-s', str(script), '-w', str(dump)]
            child = subprocess.Popen(command, env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            try:
                ready(child, directory / 'proxy.txt')
                http_fixtures(port, selected, work / 'config/mitmproxy-ca-cert.pem')
                websocket_fixture(port, selected['http'])
                ready(child, directory / 'proxy.txt', b'AURORA_WS_DONE\n')
                if selected['seen'] != ['modified'] * 3:
                    raise ValueError('Origin did not observe exactly three selected modified requests')
            finally:
                stop(child, directory / 'proxy.txt')
        if not dump.is_file() or not 0 < dump.stat().st_size <= 5 * 1024 ** 2:
            raise ValueError('Missing or oversized owned flow archive')
        offline(binary, environment, work, dump)
        report = {'linuxArm64RuntimeQualified': True, 'httpModification': True, 'tlsStrictTrust': True,
                  'untrustedCaRejected': True, 'websocketModification': True, 'flowOfflineRead': True,
                  'archiveSha256': pin['sha256'], 'androidRuntime': 'NOT TESTED', 'licenseClosureVerified': False}
        (directory / 'runtime-report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    qualify(parser.parse_args().directory)
