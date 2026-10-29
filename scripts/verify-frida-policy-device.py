#!/usr/bin/env python3
"""Explicit Root device tests of pure policy commands; never loads a policy."""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import uuid


def verify(args):
    def adb(*command):
        return subprocess.run([str(args.adb), '-s', args.serial, *command],
                              capture_output=True, check=True).stdout

    def root(command):
        return adb('shell', 'su --no-pty -c ' + shlex.quote('{ ' + command + '; } 2>&1')).decode().strip()

    def digest():
        return hashlib.sha256(adb('exec-out', 'su --no-pty -c ' +
                                  shlex.quote('cat /sys/fs/selinux/policy'))).hexdigest()

    tag = 'aurora-policy-readonly-' + uuid.uuid4().hex
    staging, private = '/data/local/tmp/' + tag, '/data/adb/' + tag
    records = {}
    before_hash = digest()
    adb('shell', 'mkdir', staging)
    try:
        adb('push', str(args.worker), staging + '/worker')
        adb('push', str(args.baseline), staging + '/before')
        if args.audit:
            adb('push', str(args.audit), staging + '/audit')
        root(f'mkdir -m 700 {private} && cp {staging}/worker {private}/worker && '
             f'cp {staging}/before {private}/before && chmod 700 {private}/worker && '
             f'chmod 600 {private}/before && printf invalid > {private}/malformed && '
             f'ln -s {private}/before {private}/link')
        if args.audit:
            root(f'cp {staging}/audit {private}/audit && chmod 700 {private}/audit')

        def case(name, mode, source, destination, scope='magisk untrusted_app', success=False):
            command = f'{private}/worker {mode} {source} {destination} {scope}'
            output = root(command + '; result=$?; printf "\\nSTATUS=%s\\n" "$result"')
            status = int(output.rsplit('STATUS=', 1)[1])
            records[name] = {'status': status, 'output': output}
            if (status == 0) != success:
                raise AssertionError(f'{name}: unexpected status {status}: {output}')

        candidate = private + '/candidate'
        case('prepare', 'policy-plan', private + '/before', candidate, success=True)
        case('verifyCandidate', 'policy-verify', private + '/before', candidate, success=True)
        case('verifyKernel', 'policy-verify', private + '/before', '/sys/fs/selinux/policy', success=True)
        candidate_hash = root(f'sha256sum {candidate}').split()[0]
        case('existingOutput', 'policy-plan', private + '/before', candidate)
        assert root(f'sha256sum {candidate}').split()[0] == candidate_hash
        case('malformed', 'policy-plan', private + '/malformed', private + '/rejected')
        case('symlink', 'policy-plan', private + '/link', private + '/rejected')
        case('attributeSource', 'policy-plan', private + '/before', private + '/rejected', 'domain untrusted_app')
        case('sameSource', 'policy-plan', private + '/before', private + '/rejected', 'magisk magisk')
        case('alreadyAdapted', 'policy-plan', '/sys/fs/selinux/policy', private + '/rejected')
        case('existingKernelLoad', 'policy-plan', private + '/before', '/sys/fs/selinux/load')
        if args.audit:
            for fault in ['none', 'extra_attribute', 'missing_attribute', 'extra_permission',
                          'broader_source', 'permissive_type', 'bounds']:
                output = root(f'{private}/audit {private}/before /sys/fs/selinux/policy {fault}; '
                              'result=$?; printf "\\nSTATUS=%s\\n" "$result"')
                status = int(output.rsplit('STATUS=', 1)[1])
                records['audit_' + fault] = {'status': status, 'output': output}
                assert status == 0, output
        after_hash = digest()
        assert before_hash == after_hash, 'Read-only commands changed kernel policy'
        report = {'workerSha256': hashlib.sha256(args.worker.read_bytes()).hexdigest(),
                  'kernelSha256': after_hash, 'kernelHashUnchanged': True,
                  'policyQualified': False, 'kernelWritten': False, 'cases': records}
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({'casesPassed': len(records), 'kernelHashUnchanged': True}))
    finally:
        root(f'rm -f {private}/worker {private}/before {private}/candidate '
             f'{private}/malformed {private}/link {private}/rejected {private}/audit; rmdir {private}')
        adb('shell', f'rm -f {staging}/worker {staging}/before {staging}/audit; rmdir {staging}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--worker', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--audit', type=Path)
    parser.add_argument('--report', type=Path, required=True)
    verify(parser.parse_args())
