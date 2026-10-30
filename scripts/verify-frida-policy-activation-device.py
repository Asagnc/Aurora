#!/usr/bin/env python3
"""Explicit same-boot owned-policy qualification. Reload requires its own flag."""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import uuid


def verify(args):
    assert args.baseline_sha == hashlib.sha256(args.baseline.read_bytes()).hexdigest()
    uuid.UUID(args.boot)

    def adb(*command):
        return subprocess.run([str(args.adb), '-s', args.serial, *command],
                              check=True, capture_output=True).stdout

    def root(command):
        return adb('shell', 'su --no-pty -c ' + shlex.quote('{ ' + command + '; } 2>&1')).decode().strip()

    def kernel_hash():
        return hashlib.sha256(adb('exec-out', 'su --no-pty -c ' +
                                  shlex.quote('cat /sys/fs/selinux/policy'))).hexdigest()

    assert root('cat /proc/sys/kernel/random/boot_id') == args.boot, 'Original baseline belongs to another boot'
    tag = 'aurora-policy-activation-' + uuid.uuid4().hex
    stage, private = '/data/local/tmp/' + tag, '/data/adb/' + tag
    initial_hash, records = kernel_hash(), {}
    adb('shell', 'mkdir', stage)
    try:
        adb('push', str(args.worker), stage + '/worker')
        adb('push', str(args.baseline), stage + '/before')
        root(f'mkdir -m 700 {private} && cp {stage}/worker {private}/worker && '
             f'cp {stage}/before {private}/before && chmod 700 {private}/worker && chmod 600 {private}/before')

        def call(name, arguments, success):
            output = root(f'{private}/worker ' + shlex.join(arguments) +
                          '; result=$?; printf "\\nSTATUS=%s\\n" "$result"')
            status = int(output.rsplit('STATUS=', 1)[1])
            records[name] = {'status': status, 'output': output}
            assert (status == 0) == success, f'{name}: {output}'
            if success:
                return json.loads(output[:output.rfind('STATUS=')].strip())

        before, candidate = private + '/before', private + '/candidate'
        call('plan', ['policy-plan', before, candidate, 'magisk', 'untrusted_app'], True)
        base = [before, candidate, args.boot, 'magisk', 'untrusted_app']
        call('attest', ['policy-attest', *base], True)
        result = call('activateExisting', ['policy-activate', *base], True)
        assert not result['kernelWriteAttempted'], 'Existing owned delta must not be reloaded by normal activation'
        wrong_boot = base.copy(); wrong_boot[2] = str(uuid.uuid4())
        call('wrongBoot', ['policy-activate', *wrong_boot], False)
        wrong_root = base.copy(); wrong_root[3] = 'untrusted_app'
        call('wrongRoot', ['policy-activate', *wrong_root], False)
        wrong_app = base.copy(); wrong_app[4] = 'domain'
        call('attributeApp', ['policy-activate', *wrong_app], False)
        call('baselineAsCandidate', ['policy-activate', before, before, *base[2:]], False)
        root(f'chmod 666 {before}')
        try:
            call('writableBaseline', ['policy-activate', *base], False)
        finally:
            root(f'chmod 600 {before}')
        assert kernel_hash() == initial_hash, 'Non-writing cases changed the policy'
        if args.reload_owned:
            result = call('explicitOwnedReload', ['policy-qualify-reload', *base], True)
            assert result['kernelWriteAttempted'] and result['policyQualified'] and result['inverseVerified'] and result['enforcing']
            call('attestAfterReload', ['policy-attest', *base], True)
        if args.bootstrap_owned_baseline:
            product = '/data/user/0/io.github.asagnc.aurora/files/spaces/resources/root-frida-policy'
            for directory in [product, product + '/' + args.boot]:
                root(f'if [ ! -e {directory} ]; then mkdir -m 700 {directory}; fi')
                assert root(f'test ! -L {directory} && stat -c %u:%a {directory}') == '0:700'
            destination = product + '/' + args.boot + '/before.policy'
            root(f'if [ ! -e {destination} ]; then (set -C; umask 077; cat {before} > {destination}); fi')
            assert root(f'test ! -L {destination} && stat -c %u:%a:%h {destination}') == '0:600:1'
            assert root(f'sha256sum {destination}').split()[0] == args.baseline_sha
            call('attestProductOwnedBaseline', ['policy-attest', destination, candidate, *base[2:]], True)
        report = {'workerSha256': hashlib.sha256(args.worker.read_bytes()).hexdigest(),
                  'initialKernelSha256': initial_hash, 'finalKernelSha256': kernel_hash(),
                  'ownedReloadRequested': args.reload_owned, 'ownedBootstrapRequested': args.bootstrap_owned_baseline,
                  'cases': records, 'newBootQualified': False, 'rootRevocationQualified': False}
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({'casesPassed': len(records), 'ownedReload': args.reload_owned,
                          'kernelHashUnchanged': report['initialKernelSha256'] == report['finalKernelSha256']}))
    finally:
        root(f'rm -f {private}/worker {private}/before {private}/candidate; rmdir {private}')
        adb('shell', f'rm -f {stage}/worker {stage}/before; rmdir {stage}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--worker', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--baseline-sha', required=True)
    parser.add_argument('--boot', required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--reload-owned', action='store_true')
    parser.add_argument('--bootstrap-owned-baseline', action='store_true')
    verify(parser.parse_args())
