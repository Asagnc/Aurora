#!/usr/bin/env python3
"""Stage an audited APK with matching source materials; never install, sign or publish."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def json_entry(archive, name):
    entry = archive.getinfo(name)
    if entry.file_size > 2_097_152:
        raise ValueError('Oversized source metadata')
    return json.loads(archive.read(entry))


def source_identity(apk, materials):
    with zipfile.ZipFile(apk) as binary, zipfile.ZipFile(materials) as sources:
        if len(sources.namelist()) != len(set(sources.namelist())):
            raise ValueError('Duplicate source entries')
        reference = json_entry(sources, 'qualification.json')
        packaged = binary.read('assets/managed/arm64-v8a.worker.sha256').decode('ascii')
        with binary.open('lib/arm64-v8a/libaurora_frida.so') as worker:
            actual = hashlib.file_digest(worker, 'sha256').hexdigest()
        if packaged != actual or reference['workerSha256'] != actual:
            raise ValueError('Source materials do not correspond to this APK worker')
        bridge = json_entry(sources, 'tools/frida-bridge/bundle-inputs.json')['script']
        if bridge != json_entry(binary, 'assets/managed/java-bridge.json'):
            raise ValueError('Source bridge differs from APK')
        return {'workerSha256': actual, 'bridge': bridge}


def verify_signature(apk, args):
    if args.unsigned_review:
        if args.apksigner or args.certificate_sha256:
            raise ValueError('Unsigned review cannot claim signature verification')
        return {'verified': False, 'reason': 'unsigned-review-only'}
    if not args.apksigner or not re.fullmatch('[a-f0-9]{64}', args.certificate_sha256 or ''):
        raise ValueError('Release requires apksigner and the expected certificate SHA256')
    result = subprocess.run([str(args.apksigner), 'verify', '--verbose', '--print-certs', str(apk)],
                            capture_output=True, text=True, check=True)
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$', result.stdout, re.M)
    if [item.lower() for item in certificates] != [args.certificate_sha256]:
        raise ValueError('APK signer differs from the selected release certificate')
    return {'verified': True, 'certificateSha256': args.certificate_sha256}


def audit(apk):
    spec = importlib.util.spec_from_file_location('apk_audit', ROOT / 'scripts/verify-linux-native.py')
    verifier = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(verifier)
    verifier.verify(apk)


def stage(args):
    output = args.output.resolve()
    if output.exists():
        raise FileExistsError('Select a new release directory; existing output is preserved')
    if not output.parent.is_dir():
        raise ValueError('Release parent directory must exist')
    # Copy first, then audit the immutable selected copies; external inputs may change.
    with tempfile.TemporaryDirectory(prefix='.aurora-release-', dir=output.parent) as temporary:
        work = Path(temporary)
        selected = [('Aurora.apk', args.apk), ('frida-source-materials.zip', args.frida_sources),
                    ('native-corresponding-source.zip', args.native_sources)]
        for name, source in selected:
            if not source.is_file():
                raise ValueError('Missing selected release input: ' + name)
            shutil.copyfile(source, work / name)
        audit(work / 'Aurora.apk')
        identity = source_identity(work / 'Aurora.apk', work / 'frida-source-materials.zip')
        reference = json.loads(args.frida_reference.read_text(encoding='utf-8'))
        if identity['workerSha256'] != reference['workerSha256']:
            raise ValueError('APK worker differs from trusted build reference')
        signature = verify_signature(work / 'Aurora.apk', args)
        # Rebuild verifiers validate sources against this trusted checkout before executing code.
        subprocess.run([args.python, str(ROOT / 'scripts/verify-frida-source-roundtrip.py'),
            str(work / 'frida-source-materials.zip'), '--reference', str(args.frida_reference.resolve()),
            '--ndk', str(args.ndk.resolve()), '--report', str(work / 'frida-relink.json')], check=True)
        subprocess.run([args.python, str(ROOT / 'scripts/verify-managed-source-roundtrip.py'),
            str(work / 'native-corresponding-source.zip'), '--reference', str(args.guard_reference.resolve()),
            '--ndk', str(args.ndk.resolve()), '--report', str(work / 'native-rebuild.json')], check=True)
        manifest = {'format': 1, 'androidRuntime': 'NOT VERIFIED BY PACKAGING',
            'releaseReady': False, 'signature': signature, **identity,
            'gitCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            'files': [{'name': name, 'bytes': (work / name).stat().st_size, 'sha256': sha(work / name)}
                      for name in sorted(path.name for path in work.iterdir())]}
        (work / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
        (work / 'SHA256SUMS').write_text(''.join(f'{sha(path)}  {path.name}\n'
            for path in sorted(work.iterdir())), encoding='ascii')
        output.mkdir()  # Exclusive creation; never replace an existing review/release directory.
        try:
            for path in work.iterdir():
                shutil.copyfile(path, output / path.name)
        except BaseException:
            # Only this invocation's newly created output is removed.
            shutil.rmtree(output)
            raise
    print(json.dumps({'directory': str(output), 'signatureVerified': signature['verified'],
                      'deviceAcceptance': 'PENDING'}))


if __name__ == '__main__':
    import sys
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['apk', 'frida-sources', 'native-sources', 'frida-reference', 'guard-reference', 'ndk', 'output']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--unsigned-review', action='store_true')
    parser.add_argument('--apksigner', type=Path, help='Java executable wrapper; use apksigner JAR via a local launcher if needed')
    parser.add_argument('--certificate-sha256')
    parser.add_argument('--python', default=sys.executable)
    stage(parser.parse_args())
