"""Bounded offline flow inspection. Caller selects only a session UUID, never a script or arbitrary input path."""
import base64
import json
import os
from pathlib import Path
import stat
import uuid
from mitmproxy import ctx
from mitmproxy.io import FlowReader

SENSITIVE = {'authorization', 'proxy-authorization', 'cookie', 'set-cookie'}


def headers(message):
    if message is None:
        return []
    return [[name[:128], '<redacted>' if name.lower() in SENSITIVE else value[:160]]
        for name, value in list(message.headers.items(multi=True))[:16]]


def describe(flow):
    request = flow.request; response = flow.response
    return {'id': flow.id, 'method': request.method[:32], 'scheme': request.scheme, 'host': request.host[:253],
        'port': request.port, 'path': request.path.split('?', 1)[0][:512], 'requestHeaders': headers(request),
        'responseHeaders': headers(response), 'status': response.status_code if response else None,
        'requestRawBytes': len(request.raw_content or b''), 'responseRawBytes': len(response.raw_content or b'') if response else 0,
        'requestRawPreviewBase64': base64.b64encode((request.raw_content or b'')[:1024]).decode(),
        'responseRawPreviewBase64': base64.b64encode((response.raw_content or b'')[:1024]).decode() if response else None,
        'error': bool(flow.error), 'websocket': flow.websocket is not None}


def inspect(identity, offset, limit):
    if str(uuid.UUID(identity)) != identity or not 0 <= offset <= 512 or not 1 <= limit <= 10:
        raise ValueError('Invalid owned proxy inspection selection')
    path = Path('/mnt/project/.aurora-proxy') / identity / 'flows.mitm'
    current = Path('/')
    for part in path.parts[1:]:
        current /= part
        if current.is_symlink():
            raise ValueError('Proxy flow path contains a link')
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(descriptor, 'rb') as stream:
        before = os.fstat(stream.fileno())
        if not stat.S_ISREG(before.st_mode) or not 0 <= before.st_size <= 32 * 1024 ** 2:
            raise ValueError('Proxy flow archive exceeds the fixed file budget')
        rows = []; reached_end = True; failed = False
        try:
            for index, flow in enumerate(FlowReader(stream).stream()):
                if index >= 512:
                    raise ValueError('Proxy flow count exceeds the fixed budget')
                if index < offset:
                    continue
                row = describe(flow)
                if len(rows) >= limit or len(json.dumps(rows + [row]).encode()) > 30000:
                    reached_end = False; break
                rows.append(row)
        except Exception:
            failed = True
        after = os.fstat(stream.fileno())
        if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
            after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns):
            raise ValueError('Proxy archive changed during inspection')
    return {'session': identity, 'flows': rows, 'next': None if reached_end and not failed else offset + len(rows),
        'complete': reached_end and not failed, 'parseError': failed,
        'scope': 'Stored HTTP flows; full accepted WebSocket messages are separate indexed artifacts. Sensitive headers and query strings are omitted; body previews may still contain application data.'}


def load(loader):
    loader.add_option('aurora_read_id', str, '', 'Owned Aurora proxy session UUID')
    loader.add_option('aurora_read_offset', int, 0, 'Bounded stored flow offset')
    loader.add_option('aurora_read_limit', int, 5, 'Bounded stored flow count')


def running():
    try:
        value = inspect(ctx.options.aurora_read_id, ctx.options.aurora_read_offset, ctx.options.aurora_read_limit)
        print('\naurora-proxy-read:' + json.dumps(value, separators=(',', ':')), flush=True)
    except Exception as error:
        print('\naurora-proxy-read:' + json.dumps({'error': type(error).__name__}), flush=True)
    finally:
        ctx.master.shutdown()
