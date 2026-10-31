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
    full_test = 'io.github.asagnc.aurora.root.' + test
    if args.kind == 'sdk-root':
        full_test = 'io.github.asagnc.aurora.reverse.AndroidBuildFaultDeviceTest#rootServiceDeathRetiresSdkLeaseWithoutReplayingTheGuest'
        flag = 'auroraAndroidBuildRootDeathTest'
    if args.kind == 'linux-pty-root':
        full_test = 'io.github.asagnc.aurora.linux.RootLinuxTerminalDeviceTest#rootServiceDeathRetainsLeaseUntilFreshServiceProofWithoutReplay'
        flag = 'auroraLinuxRootDeathTest'
    if args.kind == 'metadata-root':
        full_test = 'io.github.asagnc.aurora.linux.RootMetadataDeviceTest'
        flag = 'auroraRootMetadataDeathTest'
    command = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', full_test,
               '-e', 'notAnnotation', 'none', '-e', 'auroraRootTest', 'true', '-e', flag, 'true',
               '-e', 'auroraFridaPolicyTest', 'true',
               'io.github.asagnc.aurora.test/androidx.test.runner.AndroidJUnitRunner']
    if args.kind in {'linux-pty-root', 'metadata-root'}:
        command[-1:-1] = ['-e', 'auroraLinuxImageTest', 'true']
    process = subprocess.Popen(prefix + command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    incoming = queue.Queue()

    def read():
        for line in process.stdout:
            incoming.put(line)
        incoming.put(None)

    reader = threading.Thread(target=read, daemon=True); reader.start()
    marker = 'ROOT' if args.kind == 'root' else 'WORKER'
    prefix_marker = 'AURORA_SDK_ROOT' if args.kind == 'sdk-root' else 'AURORA_FRIDA_' + marker
    if args.kind == 'linux-pty-root':
        prefix_marker = 'AURORA_LINUX_PTY_ROOT'
    if args.kind == 'metadata-root':
        prefix_marker = 'AURORA_METADATA_ROOT'
    pattern = re.compile(prefix_marker + r'_DEATH_READY=(\d+):(\d+)')
    proofs, signalled = [], set()
    expected_signals = 3 if args.kind == 'metadata-root' else 1
    expected_tests = 4 if args.kind == 'metadata-root' else 1
    transcript = bytearray()
    deadline = time.monotonic() + (360 if args.kind == 'sdk-root' else 180)
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
                pid, ticks = map(int, found.groups())
                assert len(proofs) < expected_signals and (pid, ticks) not in signalled, 'Duplicate or excessive signal request'

                def signal(expected):
                    output = root(shlex.join([remote, '--owned-root', str(pid), str(expected)]) +
                                  '; result=$?; printf "\\nSTATUS=%s\\n" "$result"')
                    return int(output.rsplit('STATUS=', 1)[1]), output

                wrong, _ = signal(ticks + 1)
                assert wrong == 66, 'Wrong start ticks were not refused'
                status, output = signal(ticks)
                assert status == 0 and '"exactRootPidfdSignalled":true' in output, output
                proofs.append({'exactPidfdSignalled': True, 'wrongTicksRefused': True})
                signalled.add((pid, ticks))
        process.wait(timeout=10)
        success = f'OK ({expected_tests} test' + ('s)' if expected_tests != 1 else ')')
        statuses = [line.strip() for line in transcript.decode(errors='replace').splitlines()
                    if line.startswith('INSTRUMENTATION_STATUS_CODE:')]
        assert (process.returncode == 0 and len(proofs) == expected_signals
                and success.encode() in transcript and b'FAILURES!!!' not in transcript
                and statuses.count('INSTRUMENTATION_STATUS_CODE: 0') == expected_tests
                and all(line in {'INSTRUMENTATION_STATUS_CODE: 0', 'INSTRUMENTATION_STATUS_CODE: 1',
                                 'INSTRUMENTATION_STATUS_CODE: 2'}
                        for line in statuses)), 'Device fault test did not pass without skips'
        proof = {**proofs[0], 'signals': len(proofs)}
        (args.output / 'host.json').write_text(json.dumps({'kind': args.kind, **proof,
            'policyQualifiedBeforeFault': args.kind in {'root', 'worker'},
            'sdkQualifiedBeforeFault': args.kind == 'sdk-root',
            'linuxPtyAdmittedBeforeFault': args.kind == 'linux-pty-root', 'deviceTestsPassed': expected_tests}, indent=2) + '\n')
        print(json.dumps({'kind': args.kind, 'passed': expected_tests, **proof}))
    finally:
        if process.poll() is None:
            process.terminate(); process.wait(timeout=10)
        process.stdout.close()
        adb('shell', 'rm', '-f', remote)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--kind', choices=['root', 'worker', 'sdk-root', 'linux-pty-root', 'metadata-root'], required=True)
    parser.add_argument('--signal-helper', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    verify(parser.parse_args())
