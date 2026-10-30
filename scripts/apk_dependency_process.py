"""Linux-only qualification process barrier; never installed on the phone."""
import ctypes
import errno
import os
from pathlib import Path
import signal
import subprocess
import time


def subreaper():
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(36, 1, 0, 0, 0):  # PR_SET_CHILD_SUBREAPER
        raise OSError(ctypes.get_errno(), 'Cannot establish owned child barrier')


def children():
    # Fail closed if this qualification kernel cannot enumerate adopted children.
    data = Path(f'/proc/self/task/{os.getpid()}/children').read_text().strip()
    return [int(value) for value in data.split()]


def reap():
    while True:
        try:
            pid, _ = os.waitpid(-1, os.WNOHANG)
            if not pid:
                return False
        except ChildProcessError:
            return True


def stop_owned():
    for number in [signal.SIGTERM, signal.SIGKILL]:
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            if reap():
                return
            for pid in children():
                try:
                    fd = os.pidfd_open(pid)
                    try:
                        # Membership is rechecked after opening; never signal a
                        # numeric PID or a process group selected by a name.
                        if pid in children():
                            signal.pidfd_send_signal(fd, number)
                    finally:
                        os.close(fd)
                except ProcessLookupError:
                    pass
                except OSError as failure:
                    if failure.errno != errno.ESRCH:
                        raise
            time.sleep(.05)
    if not reap():
        raise RuntimeError('Owned child exit remains unconfirmed; cache cannot be published')


def execute(argv, log, timeout, additions=None):
    environment = {'PATH': '/usr/bin:/bin', 'HOME': '/root', 'LANG': 'C.UTF-8'}
    environment.update(additions or {})
    started = time.monotonic()
    result = None
    try:
        with log.open('xb') as output:
            result = subprocess.run(argv, env=environment, stdin=subprocess.DEVNULL,
                                    stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
    finally:
        stop_owned()
    if result is None:
        raise RuntimeError('No completed process result')
    result.check_returncode()
    return round(time.monotonic() - started, 3)
