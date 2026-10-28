"""Aurora-owned regular loopback proxy. Scripts and rules are immutable for one supervised session."""
import asyncio
import base64
import hashlib
import json
import os
from pathlib import Path
import time
import uuid
from mitmproxy import ctx, exceptions
from mitmproxy.io import FlowWriter
from aurora_proxy_rules import apply_http, decode, matches


class BudgetWriter:
    def __init__(self, owner, stream):
        self.owner, self.stream = owner, stream

    def write(self, data):
        self.owner.spend(len(data))
        if self.stream.write(data) != len(data):
            raise OSError('Short proxy artifact write')


class AuroraProxy:
    def __init__(self):
        self.config = None; self.encoded = None; self.binary = None; self.index = None; self.timer = None
        self.connections = set(); self.active = set(); self.saved = set()
        self.seen = 0; self.completed = 0; self.events = 0; self.stored = 0
        self.reason = 'stopped'; self.started = time.monotonic(); self.probe_task = None

    def load(self, loader):
        for name in ['aurora_config', 'aurora_output', 'aurora_id']:
            loader.add_option(name, str, '', 'Fixed Aurora supervised proxy configuration')
        loader.add_option('aurora_probe', bool, False, 'Run only the fixed owned loopback qualification')

    def configure(self, updates):
        if 'aurora_config' in updates and ctx.options.aurora_config:
            try:
                if self.config is not None:
                    if self.encoded != ctx.options.aurora_config:
                        raise ValueError('Proxy rules are immutable during a session')
                    return
                self.config = decode(ctx.options.aurora_config)
                self.encoded = ctx.options.aurora_config
                ctx.options.update(body_size_limit=str(self.config['maxBodyBytes']))
            except Exception as error:
                raise exceptions.OptionsError('Invalid fixed Aurora proxy configuration') from error

    @staticmethod
    def open_new(path):
        return os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600), 'wb')

    def spend(self, size):
        if self.stored + size + 1024 > self.config['maxStoredBytes']:
            self.stop('byte_budget'); raise ValueError('Proxy artifact budget exhausted')
        self.stored += size

    def write_file(self, name, data):
        self.spend(len(data))
        with self.open_new(self.output / name) as stream:
            if stream.write(data) != len(data):
                raise OSError('Short proxy artifact write')
            stream.flush(); os.fsync(stream.fileno())

    def running(self):
        identity = ctx.options.aurora_id
        self.output = Path(ctx.options.aurora_output)
        if self.config is None or str(uuid.UUID(identity)) != identity or self.output != Path('/mnt/.aurora-proxy') / identity:
            raise ValueError('Proxy output does not belong to the fixed owned session')
        if ctx.options.listen_host != '127.0.0.1' or ctx.options.listen_port != self.config['port'] or ctx.options.ssl_insecure:
            raise ValueError('Proxy must use strict upstream TLS and explicit loopback regular mode')
        certificate = Path(ctx.options.confdir) / 'mitmproxy-ca-cert.pem'
        with certificate.open('rb') as source:
            public = source.read(16385)
        if len(public) > 16384 or b'PRIVATE KEY' in public or b'BEGIN CERTIFICATE' not in public:
            raise ValueError('Only a bounded public CA certificate may be exported')
        self.write_file('ca-cert.pem', public)
        self.binary = self.open_new(self.output / 'flows.mitm')
        self.index = self.open_new(self.output / 'index.jsonl')
        self.writer = FlowWriter(BudgetWriter(self, self.binary))
        self.timer = asyncio.get_running_loop().call_later(self.config['durationSeconds'], self.stop, 'deadline')
        print('\naurora-proxy-ready:' + identity + ':127.0.0.1:' + str(self.config['port']), flush=True)
        if ctx.options.aurora_probe:
            self.probe_task = asyncio.create_task(self.probe())

    def stop(self, reason):
        if self.reason == 'stopped':
            self.reason = reason
        ctx.master.shutdown()

    def client_connected(self, client):
        if len(self.connections) >= 16:
            client.error = 'Aurora proxy connection budget exceeded'
        else:
            self.connections.add(client.id)

    def client_disconnected(self, client):
        self.connections.discard(client.id)

    def requestheaders(self, flow):
        if self.reason != 'stopped' or self.seen >= self.config['maxFlows'] or len(self.active) >= 16:
            self.stop('flow_budget'); flow.kill(); return
        self.seen += 1; self.active.add(flow.id)

    def modify(self, flow, stage):
        message = flow.request if stage == 'request' else flow.response
        if message is None or len(message.raw_content or b'') > self.config['maxBodyBytes']:
            flow.kill(); self.stop('body_budget'); return
        for selected in self.config['rules']:
            if matches(selected, flow, stage):
                if selected['replacement'] is not None and stage == 'response' and (
                    flow.request.method == 'HEAD' or flow.response.status_code < 200 or flow.response.status_code in {204, 304}):
                    flow.kill(); return
                apply_http(selected, message)

    def request(self, flow):
        self.modify(flow, 'request')

    def response(self, flow):
        self.modify(flow, 'response')
        if flow.response.status_code != 101:
            self.save(flow)

    def error(self, flow):
        self.save(flow)

    def record(self, value):
        self.events += 1
        if self.events > 1024:
            self.stop('event_budget'); raise ValueError('Proxy event budget exhausted')
        data = (json.dumps(value, separators=(',', ':')) + '\n').encode()
        self.spend(len(data))
        if self.index.write(data) != len(data):
            raise OSError('Short proxy index write')
        self.index.flush()

    def save(self, flow):
        if self.binary is None or flow.id in self.saved:
            return
        self.active.discard(flow.id); self.saved.add(flow.id)
        try:
            self.writer.add(flow); self.binary.flush()
            self.record({'type': 'flow', 'id': flow.id, 'host': flow.request.host, 'method': flow.request.method,
                'path': flow.request.path.split('?', 1)[0][:512], 'status': flow.response.status_code if flow.response else None,
                'error': bool(flow.error), 'requestBytes': len(flow.request.raw_content or b''),
                'responseBytes': len(flow.response.raw_content or b'') if flow.response else 0})
            self.completed += 1
        except Exception:
            self.stop('artifact_failure')

    def websocket_message(self, flow):
        message = flow.websocket.messages[-1]
        try:
            if len(message.content) > self.config['maxBodyBytes']:
                raise ValueError('Proxy WebSocket message budget exhausted')
            for selected in self.config['rules']:
                if matches(selected, flow, 'websocket') and (selected['direction'] == 'client') == message.from_client:
                    if message.is_text:
                        selected['replacement'].decode('utf-8')
                    message.content = selected['replacement']
            name = 'ws-' + str(self.events + 1).zfill(6) + '.bin'
            self.write_file(name, message.content)
            self.record({'type': 'websocket', 'flow': flow.id, 'fromClient': message.from_client, 'text': message.is_text,
                'file': name, 'bytes': len(message.content), 'sha256': hashlib.sha256(message.content).hexdigest()})
            # Full accepted messages live in bounded separate artifacts, not an unbounded in-memory history.
            flow.websocket.messages[:] = [message]
        except Exception:
            self.stop('websocket_budget_or_artifact_failure'); flow.kill()

    def websocket_end(self, flow):
        self.save(flow)

    async def probe(self):
        try:
            from aurora_proxy_probe import qualify
            await qualify(self.output, self.config['port'])
            print('\naurora-proxy-probe-ok', flush=True)
            self.stop('probe_complete')
        except Exception:
            self.stop('probe_failed')

    def done(self):
        if self.timer:
            self.timer.cancel()
        for stream in [self.binary, self.index]:
            if stream:
                stream.flush(); os.fsync(stream.fileno()); stream.close()
        if hasattr(self, 'output') and self.config is not None:
            data = json.dumps({'protocol': 1, 'id': ctx.options.aurora_id, 'port': self.config['port'],
                'probe': ctx.options.aurora_probe,
                'configSha256': hashlib.sha256(base64.b64decode(ctx.options.aurora_config, validate=True)).hexdigest(),
                'reason': self.reason, 'seenFlows': self.seen, 'completedFlows': self.completed,
                'events': self.events, 'storedBytes': self.stored, 'elapsedSeconds': round(time.monotonic() - self.started, 3),
                'storedBytesExcludesFinalRecord': True,
                'scope': 'Bounded stored HTTP flows and separate accepted WebSocket messages; upstream transient parser buffers are not a hard heap quota'}).encode()
            if len(data) > 1024:
                raise ValueError('Proxy final record exceeds reserved budget')
            with self.open_new(self.output / 'final.json') as stream:
                stream.write(data); stream.flush(); os.fsync(stream.fileno())
            directory = os.open(self.output, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)


addons = [AuroraProxy()]
