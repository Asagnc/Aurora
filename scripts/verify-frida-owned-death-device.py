#!/usr/bin/env python3
"""Signal only the exact Root/worker identity supplied by its explicit device test."""
import argparse
import json
from pathlib import Path
import queue
import re
import shlex
import subprocess
import threading
import time
import uuid


def verify(args):
    prefix = [str(args.adb), '-s', args.serial]

    def adb(*command):
        return subprocess.run(prefix + list(command), capture_output=True, check=True, timeout=20).stdout

    def root(command):
        return adb('shell', 'su --no-pty -c ' + shlex.quote(command)).decode()

    remote = '/data/local/tmp/aurora-owned-signal-' + uuid.uuid4().hex
    args.output.mkdir(parents=True, exist_ok=False)
    adb('push', str(args.signal_helper), remote)
    adb('shell', 'chmod', '700', remote)
    test = ('FridaCancellationDeviceTest#rootServiceDeathRetiresTheLoadedWorkerAndResetsQualification'
            if args.kind == 'root' else 'FridaWorkerDeathDeviceTest')
    flag = 'auroraFridaRootDeathTest' if args.kind == 'root' else 'auroraFridaWorkerDeathTest'
    command = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'io.github.asagnc.aurora.root.' + test,
               '-e', 'notAnnotation', 'none', '-e', 'auroraRootTest', 'true', '-e', flag, 'true',
               '-e', 'auroraFridaPolicyTest', 'true',
               'io.github.asagnc.aurora.test/androidx.test.runner.AndroidJUnitRunner']
    process = subprocess.Popen(prefix + command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    incoming = queue.Queue()

    def read():
        for line in process.stdout:
            incoming.put(line)
        incoming.put(None)

    reader = threading.Thread(target=read, daemon=True); reader.start()
    marker = 'ROOT' if args.kind == 'root' else 'WORKER'
    pattern = re.compile(r'AURORA_FRIDA_' + marker + r'_DEATH_READY=(\d+):(\d+)')
    proof = None
    transcript = bytearray()
    deadline = time.monotonic() + 180
    try:
        with (args.output / 'instrumentation.txt').open('wb') as log:
            while True:
                if time.monotonic() >= deadline:
                    raise TimeoutError('Device test timed out; inspect retained state before recovery')
                try:
                    line = incoming.get(timeout=1)
                except queue.Empty:
                    continue
                if line is None:
                    break
                transcript.extend(line); log.write(line); log.flush()
                found = pattern.search(line.decode(errors='replace'))
                if not found:
                    continue
                assert proof is None, 'Duplicate signal request'
                pid, ticks = map(int, found.groups())

                def signal(expected):
                    output = root(shlex.join([remote, '--owned-root', str(pid), str(expected)]) +
                                  '; result=$?; printf "\\nSTATUS=%s\\n" "$result"')
                    return int(output.rsplit('STATUS=', 1)[1]), output

                wrong, _ = signal(ticks + 1)
                assert wrong == 66, 'Wrong start ticks were not refused'
                status, output = signal(ticks)
                assert status == 0 and '"exactRootPidfdSignalled":true' in output, output
                proof = {'exactPidfdSignalled': True, 'wrongTicksRefused': True}
        process.wait(timeout=10)
        assert proof and b'OK (1 test)' in transcript and b'FAILURES!!!' not in transcript, 'Device fault test did not pass'
        (args.output / 'host.json').write_text(json.dumps({'kind': args.kind, **proof,
            'policyQualifiedBeforeFault': True, 'deviceTestsPassed': 1}, indent=2) + '\n')
        print(json.dumps({'kind': args.kind, 'passed': 1, **proof}))
    finally:
        if process.poll() is None:
            process.terminate(); process.wait(timeout=10)
        process.stdout.close()
        adb('shell', 'rm', '-f', remote)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--kind', choices=['root', 'worker'], required=True)
    parser.add_argument('--signal-helper', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    verify(parser.parse_args())
