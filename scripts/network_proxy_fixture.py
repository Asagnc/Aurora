"""Owned loopback origins and static addons for native proxy qualification, never user traffic."""
import base64
from contextlib import contextmanager
import hashlib
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
import socket
import ssl
import subprocess
import threading

ADDON = '''
def running():
    print('AURORA_PROXY_READY', flush=True)
def request(flow):
    flow.request.headers['X-Aurora-Fixture'] = 'modified'
def response(flow):
    if flow.request.path == '/fixture' and flow.response.status_code == 200:
        flow.response.content = b'owned-modified'
        flow.response.headers['X-Aurora-Response'] = 'modified'
def websocket_message(flow):
    message = flow.websocket.messages[-1]
    if not message.from_client:
        message.content = b'owned-ws-modified'
def websocket_end(flow):
    print('AURORA_WS_DONE', flush=True)
'''


@contextmanager
def origins(work):
    certificate = work / 'origin.pem'; key = work / 'origin.key'
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
        '-keyout', str(key), '-out', str(certificate), '-subj', '/CN=127.0.0.1',
        '-addext', 'subjectAltName=IP:127.0.0.1'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=30)
    seen = []
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'
        def log_message(self, *_):
            pass
        def do_GET(self):
            seen.append(self.headers.get('X-Aurora-Fixture'))
            if self.path == '/fixture-ws':
                accept = base64.b64encode(hashlib.sha1((self.headers['Sec-WebSocket-Key'] +
                    '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
                self.send_response(101); self.send_header('Upgrade', 'websocket')
                self.send_header('Connection', 'Upgrade'); self.send_header('Sec-WebSocket-Accept', accept); self.end_headers()
                self.wfile.write(b'\x81\x08owned-ws\x88\x02\x03\xe8'); self.wfile.flush()
                self.close_connection = True
                return
            body = b'owned-origin'
            self.send_response(200 if seen[-1] == 'modified' else 400)
            self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    http = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    https = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); context.load_cert_chain(certificate, key)
    https.socket = context.wrap_socket(https.socket, server_side=True)
    threads = [threading.Thread(target=server.serve_forever, daemon=True) for server in [http, https]]
    for thread in threads:
        thread.start()
    try:
        yield {'http': http.server_port, 'https': https.server_port, 'ca': certificate, 'seen': seen}
    finally:
        for server in [http, https]:
            server.shutdown(); server.server_close()
        for thread in threads:
            thread.join(timeout=5)


def http_fixtures(port, selected, proxy_ca):
    plain = http.client.HTTPConnection('127.0.0.1', port, timeout=10)
    try:
        plain.request('GET', f'http://127.0.0.1:{selected["http"]}/fixture')
        response = plain.getresponse()
        if response.status != 200 or response.read() != b'owned-modified' or response.getheader('X-Aurora-Response') != 'modified':
            raise ValueError('Plain HTTP request/response rules did not execute')
    finally:
        plain.close()
    encrypted = http.client.HTTPSConnection('127.0.0.1', port, timeout=10, context=ssl.create_default_context(cafile=proxy_ca))
    try:
        encrypted.set_tunnel('127.0.0.1', selected['https']); encrypted.request('GET', '/fixture')
        response = encrypted.getresponse()
        if response.status != 200 or response.read() != b'owned-modified':
            raise ValueError('Strictly verified TLS proxy did not modify the response')
    finally:
        encrypted.close()
    untrusted = http.client.HTTPSConnection('127.0.0.1', port, timeout=10, context=ssl.create_default_context())
    try:
        untrusted.set_tunnel('127.0.0.1', selected['https'])
        try:
            untrusted.request('GET', '/fixture')
        except ssl.SSLCertVerificationError:
            pass
        else:
            raise ValueError('Untrusted proxy CA was silently accepted')
    finally:
        untrusted.close()


def websocket_fixture(port, origin):
    with socket.create_connection(('127.0.0.1', port), timeout=10) as client:
        nonce = base64.b64encode(os.urandom(16)).decode()
        request = (f'GET http://127.0.0.1:{origin}/fixture-ws HTTP/1.1\r\nHost: 127.0.0.1:{origin}\r\n'
            f'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {nonce}\r\nSec-WebSocket-Version: 13\r\n\r\n')
        client.sendall(request.encode()); data = b''
        while b'\r\n\r\n' not in data:
            part = client.recv(4096)
            if not part or len(data) > 16384:
                raise ValueError('Incomplete WebSocket handshake')
            data += part
        headers, data = data.split(b'\r\n\r\n', 1)
        if b' 101 ' not in headers.split(b'\r\n', 1)[0]:
            raise ValueError('WebSocket upgrade failed')
        expected = base64.b64encode(hashlib.sha1((nonce + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
        fields = dict(line.split(b':', 1) for line in headers.split(b'\r\n')[1:] if b':' in line)
        if not any(key.lower() == b'sec-websocket-accept' and value.strip() == expected for key, value in fields.items()):
            raise ValueError('WebSocket server accept differs from owned challenge')
        while len(data) < 2:
            part = client.recv(4096)
            if not part:
                raise ValueError('Missing WebSocket frame')
            data += part
        length = data[1] & 127
        if data[0] != 0x81 or data[1] & 128 or length >= 126:
            raise ValueError('Unexpected owned WebSocket frame format')
        while len(data) < 2 + length:
            part = client.recv(4096)
            if not part:
                raise ValueError('Truncated WebSocket frame')
            data += part
        if data[2:2 + length] != b'owned-ws-modified':
            raise ValueError('WebSocket response rule did not execute')
        mask = os.urandom(4); status = b'\x03\xe8'
        client.sendall(b'\x88\x82' + mask + bytes(value ^ mask[i % 4] for i, value in enumerate(status)))
