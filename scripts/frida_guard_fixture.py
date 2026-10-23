"""Actual native guard + sealed FD sender on Root-owned Linux fixtures."""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import uuid
from frida_channel_fixture import make_ticket, send_snapshots, snapshot, start_ticks


@dataclass
class GuardLaunch:
    child: subprocess.Popen
    digest: bytes
    state: Path
    worker: tuple[int, int]
    base: list[str]

    def recovery(self):
        return [self.base[0], "recover", *self.base[2:]]


def ready_identity(state, ticket, digest):
    data = (state / "managed.ready").read_bytes()
    if len(data) != 164 or data[:4] != b"ARW1" or data[4:40] != ticket["runId"].encode() or data[40:104] != digest.hex().encode():
        raise AssertionError("Native READY differs from immutable task")
    return struct.unpack_from("<I", data, 116)[0], struct.unpack_from("<Q", data, 120)[0]


def launch_guard(worker, guard, target, selected, configuration):
    """configuration=(state, options, verified worker SHA); no script disk copy."""
    state, options, worker_sha = configuration
    ticket = make_ticket(target, selected, options, worker_sha)
    encoded = json.dumps(ticket, separators=(",", ":")); digest = hashlib.sha256(encoded.encode()).digest()
    nonce = os.urandom(32); count = 2 if options.bridge is not None else 1
    def frame(role):
        return b"AFD1" + bytes([role, count, 0, 0]) + nonce + digest
    name = "aurora-fd-" + uuid.uuid4().hex
    base = [str(guard), "run", "--state-dir", str(state), "--run-id", ticket["runId"], "--ticket-sha", digest.hex()]
    command = [*base, "--observe-ms", str(options.duration), "--", str(worker), "attach-channel", name, nonce.hex(), encoded]
    descriptors, child = [], None
    try:
        descriptors.append(snapshot(selected))
        if options.bridge is not None:
            descriptors.append(snapshot(options.bridge))
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as listener:
            listener.bind("\0" + name); listener.listen(1); listener.settimeout(10)
            child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=0)
            peer, _ = listener.accept()
            with peer:
                identity = ready_identity(state, ticket, digest)
                pid, uid, _ = struct.unpack("3i", peer.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, struct.calcsize("3i")))
                if uid != 0 or pid != identity[0] or start_ticks(pid) != identity[1]:
                    raise AssertionError("Script receiver is not the admitted native worker")
                send_snapshots(peer, descriptors, frame)
        return GuardLaunch(child, digest, state, identity, base)
    except BaseException:
        if child is not None:
            child.terminate()
            try:
                child.communicate(timeout=25)
            except subprocess.TimeoutExpired:
                child.kill(); child.communicate(timeout=5)
            subprocess.run([base[0], "recover", *base[2:]], capture_output=True, timeout=25)
        raise
    finally:
        for descriptor in descriptors:
            os.close(descriptor)
