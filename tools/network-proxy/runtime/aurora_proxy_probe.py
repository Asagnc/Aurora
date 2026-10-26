"""Fixed owned HTTP/TLS/WebSocket fixtures for the actual installed standalone proxy. No external traffic."""
import asyncio
import base64
from datetime import datetime, timedelta, timezone
import hashlib
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import os
from pathlib import Path
import socket
import ssl
import threading
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from mitmproxy import ctx


def certificate(directory):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Aurora owned loopback probe')])
    now = datetime.now(timezone.utc)
    cert = x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()) \
        .serial_number(x509.random_serial_number()).not_valid_before(now - timedelta(minutes=1)) \
        .not_valid_after(now + timedelta(days=1)).add_extension(x509.BasicConstraints(ca=True, path_length=0), True) \
        .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]), False).sign(key, hashes.SHA256())
    certificate_path = directory / 'owned-origin.pem'; key_path = directory / 'owned-origin.key'
    for path, data in [(certificate_path, cert.public_bytes(serialization.Encoding.PEM)),
        (key_path, key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))]:
        with path.open('xb') as stream:
            stream.write(data)
        path.chmod(0o600)
    return certificate_path, key_path


def origin_handler(seen):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'

        def log_message(self, *_):
            pass

        def do_GET(self):
            seen.append(self.headers.get('X-Aurora-Fixture'))
            if self.path == '/owned-ws':
                challenge = self.headers['Sec-WebSocket-Key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11'
                self.send_response(101); self.send_header('Upgrade', 'websocket'); self.send_header('Connection', 'Upgrade')
                self.send_header('Sec-WebSocket-Accept', base64.b64encode(hashlib.sha1(challenge.encode()).digest()).decode()); self.end_headers()
                self.wfile.write(b'\x81\x08owned-ws\x88\x02\x03\xe8'); self.wfile.flush(); self.close_connection = True
                return
            body = b'owned-origin'
            self.send_response(200 if seen[-1] == 'modified' else 400)
            self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    return Handler


def http_checks(port, origins, proxy_ca):
    plain = http.client.HTTPConnection('127.0.0.1', port, timeout=10)
    encrypted = http.client.HTTPSConnection('127.0.0.1', port, timeout=10, context=ssl.create_default_context(cafile=proxy_ca))
    untrusted = http.client.HTTPSConnection('127.0.0.1', port, timeout=10, context=ssl.create_default_context())
    try:
        plain.request('GET', f'http://127.0.0.1:{origins[0]}/owned-http')
        response = plain.getresponse()
        if response.status != 200 or response.read() != b'owned-modified' or response.getheader('X-Aurora-Response') != 'modified':
            raise ValueError('Owned plain HTTP rules did not execute')
        encrypted.set_tunnel('127.0.0.1', origins[1]); encrypted.request('GET', '/owned-http')
        response = encrypted.getresponse()
        if response.status != 200 or response.read() != b'owned-modified':
            raise ValueError('Owned strict HTTPS rules did not execute')
        untrusted.set_tunnel('127.0.0.1', origins[1])
        try:
            untrusted.request('GET', '/owned-http')
        except ssl.SSLCertVerificationError:
            pass
        else:
            raise ValueError('Untrusted proxy CA was silently accepted')
    finally:
        for client in [plain, encrypted, untrusted]:
            client.close()


def websocket_check(port, origin):
    with socket.create_connection(('127.0.0.1', port), timeout=10) as client:
        nonce = base64.b64encode(os.urandom(16)).decode()
        request = (f'GET http://127.0.0.1:{origin}/owned-ws HTTP/1.1\r\nHost: 127.0.0.1:{origin}\r\n'
            f'Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {nonce}\r\nSec-WebSocket-Version: 13\r\n\r\n')
        client.sendall(request.encode()); data = b''
        while b'\r\n\r\n' not in data:
            part = client.recv(4096)
            if not part or len(data) > 16384:
                raise ValueError('Incomplete owned WebSocket handshake')
            data += part
        headers, data = data.split(b'\r\n\r\n', 1)
        expected = base64.b64encode(hashlib.sha1((nonce + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
        if b' 101 ' not in headers.split(b'\r\n', 1)[0] or not any(line.lower().startswith(b'sec-websocket-accept:') and line.split(b':', 1)[1].strip() == expected for line in headers.split(b'\r\n')):
            raise ValueError('Owned WebSocket upgrade/challenge failed')
        while len(data) < 2:
            part = client.recv(4096)
            if not part:
                raise ValueError('Missing owned WebSocket frame')
            data += part
        length = data[1] & 127
        if data[0] != 0x81 or data[1] & 128 or length >= 126:
            raise ValueError('Unexpected owned WebSocket frame format')
        while len(data) < 2 + length:
            part = client.recv(4096)
            if not part:
                raise ValueError('Truncated owned WebSocket frame')
            data += part
        if data[2:2 + length] != b'owned-ws-modified':
            raise ValueError('Owned WebSocket replacement failed')
        mask = os.urandom(4); status = b'\x03\xe8'
        client.sendall(b'\x88\x82' + mask + bytes(value ^ mask[i % 4] for i, value in enumerate(status)))


async def qualify(output, port):
    private = Path(ctx.options.confdir).parent
    cert, key = await asyncio.to_thread(certificate, private)
    ctx.options.update(ssl_verify_upstream_trusted_ca=str(cert))
    seen = []; handler = origin_handler(seen)
    plain = ThreadingHTTPServer(('127.0.0.1', 0), handler)
    encrypted = ThreadingHTTPServer(('127.0.0.1', 0), handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); context.load_cert_chain(cert, key)
    encrypted.socket = context.wrap_socket(encrypted.socket, server_side=True)
    threads = [threading.Thread(target=server.serve_forever, daemon=True) for server in [plain, encrypted]]
    for thread in threads:
        thread.start()
    try:
        await asyncio.to_thread(http_checks, port, [plain.server_port, encrypted.server_port], output / 'ca-cert.pem')
        await asyncio.to_thread(websocket_check, port, plain.server_port)
        await asyncio.sleep(0.2)
        if seen != ['modified'] * 3:
            raise ValueError('Origin did not observe exactly the three owned modified requests')
    finally:
        for server in [plain, encrypted]:
            await asyncio.to_thread(server.shutdown); server.server_close()
        for thread in threads:
            await asyncio.to_thread(thread.join, 5)
            if thread.is_alive():
                raise ValueError('Owned loopback server did not stop')
