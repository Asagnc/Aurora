"""Owned Root host sender for the same native channel used by Android; no disk script copy."""
import array
from dataclasses import dataclass
import fcntl
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import uuid


def start_ticks(pid):
    value = Path(f"/proc/{pid}/stat").read_text()
    return int(value[value.rfind(")") + 1:].split()[19])


def receive_frame(peer):
    data = bytearray()
    while len(data) < 72:
        part = peer.recv(72 - len(data))
        if not part:
            break
        data.extend(part)
    return bytes(data)


def snapshot(data):
    descriptor = os.memfd_create("aurora-frida-session", os.MFD_CLOEXEC | os.MFD_ALLOW_SEALING)
    try:
        os.write(descriptor, data)
        fcntl.fcntl(descriptor, fcntl.F_ADD_SEALS,
            fcntl.F_SEAL_WRITE | fcntl.F_SEAL_GROW | fcntl.F_SEAL_SHRINK | fcntl.F_SEAL_SEAL)
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


@dataclass
class LaunchOptions:
    bridge: bytes | None = None
    corrupt: str | None = None
    supervision: socket.socket | None = None
    duration: int = 2000


@dataclass
class ChannelLaunch:
    child: subprocess.Popen
    digest: bytes
    ticks: int


def make_ticket(target, selected, options, worker_sha="a" * 64):
    bridge, corrupt = options.bridge, options.corrupt
    def metadata(data):
        return {"version": "owned-fixture", "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
    script_meta = metadata(selected)
    if corrupt == "sha":
        script_meta["sha256"] = "0" * 64
    if corrupt == "size":
        script_meta["bytes"] += 1
    ticket = {"runId": str(uuid.uuid4()), "owner": str(uuid.uuid4()),
        "identity": {"pid": os.getpid(), "startTicks": start_ticks(os.getpid()),
            "bootId": Path("/proc/sys/kernel/random/boot_id").read_text().strip(), "epoch": str(uuid.uuid4())},
        "appUid": 10000, "appBuild": "host-qualification", "workerSha256": worker_sha,
        "target": {"pid": target[0], "startTicks": target[1]}, "script": script_meta, "observeMs": options.duration}
    if bridge is not None:
        ticket["bridge"] = metadata(bridge)
    return ticket


def send_snapshots(peer, files, frame):
    peer.settimeout(5)
    if receive_frame(peer) != frame(1):
        raise AssertionError("Worker hello differs from immutable task")
    peer.sendmsg([frame(2)[:1]], [(socket.SOL_SOCKET, socket.SCM_RIGHTS, array.array("i", files))])
    peer.sendall(frame(2)[1:]); peer.shutdown(socket.SHUT_WR)
    if receive_frame(peer) != frame(3):
        raise AssertionError("Worker did not acknowledge sealed descriptors")
    if peer.recv(1) != b"":
        raise AssertionError("Worker retained control socket or sent extra bytes")


def launch(worker, target, selected, options=None):
    """target=(pid,startTicks); returns identity only after bounded FD acknowledgement."""
    options = options or LaunchOptions()
    bridge = options.bridge
    ticket = make_ticket(target, selected, options)
    encoded = json.dumps(ticket, separators=(",", ":"))
    count = 2 if bridge is not None else 1
    nonce = os.urandom(32)
    suffix = nonce + hashlib.sha256(encoded.encode()).digest()
    def frame(role):
        return b"AFD1" + bytes([role, count, 0, 0]) + suffix
    name = "aurora-fd-" + uuid.uuid4().hex
    files = []
    environment = os.environ.copy()
    inherited = []
    if options.supervision is not None:
        inherited.append(options.supervision.fileno())
        environment["AURORA_MANAGED_FD"] = str(options.supervision.fileno())
        environment["AURORA_MANAGED_TICKET"] = hashlib.sha256(encoded.encode()).hexdigest()
    child = None
    try:
        files.append(snapshot(selected))
        if bridge is not None:
            files.append(snapshot(bridge))
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as listener:
            listener.bind("\0" + name); listener.listen(1); listener.settimeout(6)
            child = subprocess.Popen([str(worker), "attach-channel", name, nonce.hex(), encoded],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, bufsize=0, env=environment, pass_fds=inherited)
            child_start = start_ticks(child.pid)
            peer, _ = listener.accept()
            with peer:
                send_snapshots(peer, files, frame)
        return ChannelLaunch(child, hashlib.sha256(encoded.encode()).digest(), child_start)
    except BaseException:
        if child is not None:
            child.kill(); child.communicate(timeout=5)
        raise
    finally:
        for descriptor in files:
            os.close(descriptor)
