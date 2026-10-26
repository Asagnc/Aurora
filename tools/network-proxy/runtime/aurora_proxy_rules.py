"""Bounded literal Aurora rules. This module never evaluates caller-provided code or patterns."""
import base64
import json
import re

PROTECTED = {'host', 'connection', 'proxy-authorization', 'proxy-authenticate', 'transfer-encoding',
    'content-length', 'content-encoding', 'upgrade', 'trailer', 'te', 'keep-alive'}


def replacement(text):
    if not isinstance(text, str) or len(text) > 21848:
        raise ValueError('Invalid replacement size')
    data = base64.b64decode(text, validate=True)
    if len(data) > 16384 or base64.b64encode(data).decode() != text:
        raise ValueError('Replacement must be bounded canonical Base64')
    return data


def rule(value):
    keys = {'stage', 'host', 'pathPrefix', 'method', 'headers', 'bodyBase64', 'direction'}
    if not isinstance(value, dict) or set(value) != keys:
        raise ValueError('Invalid rule fields')
    stage, host, path = value['stage'], value['host'], value['pathPrefix']
    if not isinstance(stage, str) or stage not in {'request', 'response', 'websocket'} or not isinstance(host, str) or not 1 <= len(host) <= 253:
        raise ValueError('Invalid rule stage or host')
    if any(not re.fullmatch(r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?', label) for label in host.split('.')):
        raise ValueError('Host must be an exact lowercase ASCII hostname or IPv4 address')
    if not isinstance(path, str) or not path.startswith('/') or len(path.encode()) > 1024 or any(ord(c) < 32 or ord(c) == 127 for c in path):
        raise ValueError('Invalid literal path prefix')
    method, headers, direction = value['method'], value['headers'], value['direction']
    if method is not None and (not isinstance(method, str) or not re.fullmatch(r'[A-Z][A-Z0-9_-]{0,31}', method)):
        raise ValueError('Invalid literal method')
    if not isinstance(headers, dict) or len(headers) > 32 or len({name.lower() for name in headers}) != len(headers):
        raise ValueError('Invalid header count or duplicate header')
    total = 0
    for name, content in headers.items():
        if not re.fullmatch(r"[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}", name) or name.lower() in PROTECTED:
            raise ValueError('Invalid or protected framing/routing header')
        if content is not None and (not isinstance(content, str) or len(content.encode()) > 2048 or any(ord(c) < 32 or ord(c) == 127 for c in content)):
            raise ValueError('Invalid header value')
        total += len(name) + (len(content.encode()) if content is not None else 0)
    if total > 16384:
        raise ValueError('Header edit budget exceeded')
    body = value['bodyBase64']
    if stage == 'websocket':
        if not isinstance(direction, str) or direction not in {'client', 'server'} or headers or body is None:
            raise ValueError('WebSocket rules require an explicit direction and replacement')
    elif direction is not None or not headers and body is None:
        raise ValueError('HTTP rule has no modification or has WebSocket fields')
    return dict(value, replacement=None if body is None else replacement(body))


def decode(encoded):
    if not isinstance(encoded, str) or len(encoded) > 43692:
        raise ValueError('Proxy configuration budget exceeded')
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) > 32768 or base64.b64encode(raw).decode() != encoded:
        raise ValueError('Invalid canonical proxy configuration')
    value = json.loads(raw)
    limits = {'port': (1024, 65535), 'durationSeconds': (1, 240), 'maxFlows': (1, 512),
        'maxStoredBytes': (4096, 32 * 1024 ** 2), 'maxBodyBytes': (1, 1024 ** 2), 'protocol': (1, 1)}
    if not isinstance(value, dict) or set(value) != set(limits) | {'rules'}:
        raise ValueError('Unknown or missing proxy configuration field')
    for name, (low, high) in limits.items():
        if type(value[name]) is not int or not low <= value[name] <= high:
            raise ValueError('Invalid proxy budget: ' + name)
    if not isinstance(value['rules'], list) or len(value['rules']) > 32:
        raise ValueError('Invalid proxy rule count')
    value['rules'] = [rule(item) for item in value['rules']]
    if any(item['replacement'] is not None and len(item['replacement']) > value['maxBodyBytes'] for item in value['rules']):
        raise ValueError('Replacement exceeds selected body budget')
    return value


def matches(selected, flow, stage):
    return selected['stage'] == stage and selected['host'] == flow.request.host.lower() and \
        flow.request.path.startswith(selected['pathPrefix']) and (selected['method'] is None or selected['method'] == flow.request.method)


def apply_http(selected, message):
    if selected['replacement'] is not None:
        for name in ['content-encoding', 'content-md5', 'digest', 'etag', 'trailer', 'transfer-encoding']:
            message.headers.pop(name, None)
        message.trailers = None
        message.content = selected['replacement']
    for name, content in selected['headers'].items():
        if content is None:
            message.headers.pop(name, None)
        else:
            message.headers[name] = content
