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


def guard_source_identity(apk, materials, reference):
    """Bind the APK digest to its exact owned sources without equating two compiler profiles."""
    configuration = ['app/src/main/cpp/CMakeLists.txt', 'app/src/main/cpp/managed/Guard.cmake',
                     'app/src/main/cpp/managed/sources.txt']
    if not 1 <= len(reference['sources']) <= 256:
        raise ValueError('Guard qualification source count exceeds budget')
    with zipfile.ZipFile(materials) as sources:
        names = sources.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate guard source entries')
        for name in configuration:
            if sources.getinfo(name).file_size > 2 * 1024 * 1024:
                raise ValueError('Guard build configuration exceeds budget')
            if sources.read(name) != (ROOT / name).read_bytes():
                raise ValueError('Packaged guard build configuration differs: ' + name)
        for name, digest in reference['sources'].items():
            if not re.fullmatch(r'app/src/main/cpp/(managed|linux|frida)/[a-z_-]+\.(c|h)', name):
                raise ValueError('Unexpected guard qualification source path')
            if sources.getinfo(name).file_size > 2 * 1024 * 1024:
                raise ValueError('Guard source exceeds budget')
            if hashlib.sha256(sources.read(name)).hexdigest() != digest or sha(ROOT / name) != digest:
                raise ValueError('Packaged guard source differs from qualification: ' + name)
    with zipfile.ZipFile(apk) as binary:
        entry = binary.getinfo('lib/arm64-v8a/libaurora_managed_guard.so')
        if not 0 < entry.file_size <= 2 * 1024 * 1024:
            raise ValueError('APK guard exceeds budget')
        digest = hashlib.sha256(binary.read(entry)).hexdigest()
        if binary.read('assets/managed/arm64-v8a.guard.sha256') != digest.encode('ascii'):
            raise ValueError('APK guard digest differs from its packaged manifest')
    return {'apkGuardSha256': digest, 'standaloneQualificationSha256': reference['sha256'],
            'byteIdentical': digest == reference['sha256'], 'ownedSourceFiles': len(reference['sources']),
            'scope': 'Exact current owned sources/configuration and APK digest audit; standalone qualification uses its own compiler profile, not an APK CMake bit-rebuild claim'}


def verify_signature(apk, args):
    if args.unsigned_review:
        if args.apksigner or args.certificate_sha256:
            raise ValueError('Unsigned review cannot claim signature verification')
        return {'verified': False, 'reason': 'unsigned-review-only'}
    if not args.apksigner or not re.fullmatch('[a-f0-9]{64}', args.certificate_sha256 or ''):
        raise ValueError('Release requires apksigner and the expected certificate SHA256')
    signer = [str(args.apksigner)]
    if args.apksigner.suffix == '.jar':
        signer = [str(args.java), '-jar', str(args.apksigner)]
    result = subprocess.run(signer + ['verify', '--verbose', '--print-certs', str(apk)],
                            capture_output=True, text=True, check=True)
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$', result.stdout, re.M)
    if [item.lower() for item in certificates] != [args.certificate_sha256]:
        raise ValueError('APK signer differs from the selected release certificate')
    return {'verified': True, 'certificateSha256': args.certificate_sha256}


def manifest_identity(badging):
    source = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    code = re.search(r'versionCode = (\d+)', source).group(1)
    version = re.search(r'versionName = "([^"]+)"', source).group(1)
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ('io.github.asagnc.aurora', code, version):
        raise ValueError('APK identity differs from this checkout')
    if "minSdkVersion:'36'" not in badging or "targetSdkVersion:'37'" not in badging or 'application-debuggable' in badging:
        raise ValueError('Release requires Android36+, target37 and a non-debuggable APK')
    return {'applicationId': package[1], 'versionCode': int(code), 'versionName': version}


def android_identity(apk, args):
    suffix = '.exe' if __import__('os').name == 'nt' else ''
    tools = args.build_tools.resolve()
    result = subprocess.run([str(tools / ('aapt2' + suffix)), 'dump', 'badging', str(apk)],
                            capture_output=True, text=True, check=True)
    identity = manifest_identity(result.stdout)
    from launcher_assets import verify as verify_launcher
    identity['launcherArtwork'] = verify_launcher(apk, tools / ('aapt2' + suffix), result.stdout)
    subprocess.run([str(tools / ('zipalign' + suffix)), '-c', '-P', '16', '4', str(apk)],
                   capture_output=True, check=True)
    return identity


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
        sdk_bundle = getattr(args, 'android_build_bundle', None)
        sdk_sources = getattr(args, 'android_build_sources', None)
        if bool(sdk_bundle) != bool(sdk_sources):
            raise ValueError('SDK native bundle and source materials must be supplied together')
        if sdk_bundle:
            selected += [('android-host-arm64.tar.gz', sdk_bundle), ('android-build-source-materials.zip', sdk_sources)]
        proxy_bundle = getattr(args, 'proxy_distribution', None)
        proxy_sources = getattr(args, 'proxy_sources', None)
        if bool(proxy_bundle) != bool(proxy_sources):
            raise ValueError('Proxy distribution and source materials must be supplied together')
        if proxy_bundle:
            selected += [('mitmproxy-linux-aarch64.tar.gz', proxy_bundle), ('network-proxy-source-materials.zip', proxy_sources)]
        for name, source in selected:
            if not source.is_file():
                raise ValueError('Missing selected release input: ' + name)
            shutil.copyfile(source, work / name)
        audit(work / 'Aurora.apk')
        application = android_identity(work / 'Aurora.apk', args)
        identity = source_identity(work / 'Aurora.apk', work / 'frida-source-materials.zip')
        guard = guard_source_identity(work / 'Aurora.apk', work / 'native-corresponding-source.zip',
                                      json.loads(args.guard_reference.read_text(encoding='utf-8')))
        reference = json.loads(args.frida_reference.read_text(encoding='utf-8'))
        if identity['workerSha256'] != reference['workerSha256']:
            raise ValueError('APK worker differs from trusted build reference')
        signature = verify_signature(work / 'Aurora.apk', args)
        sdk = None
        if sdk_bundle:
            from apk_release_materials import verify_release
            sdk = verify_release(work / 'Aurora.apk', work / 'android-host-arm64.tar.gz', work / 'android-build-source-materials.zip')
        proxy = None
        if proxy_bundle:
            from proxy_release_materials import verify_release as verify_proxy
            proxy = verify_proxy(work / 'Aurora.apk', work / 'mitmproxy-linux-aarch64.tar.gz', work / 'network-proxy-source-materials.zip')
        # Rebuild verifiers validate sources against this trusted checkout before executing code.
        subprocess.run([args.python, str(ROOT / 'scripts/verify-frida-source-roundtrip.py'),
            str(work / 'frida-source-materials.zip'), '--reference', str(args.frida_reference.resolve()),
            '--ndk', str(args.ndk.resolve()), '--report', str(work / 'frida-relink.json')], check=True)
        subprocess.run([args.python, str(ROOT / 'scripts/verify-managed-source-roundtrip.py'),
            str(work / 'native-corresponding-source.zip'), '--reference', str(args.guard_reference.resolve()),
            '--ndk', str(args.ndk.resolve()), '--report', str(work / 'native-rebuild.json')], check=True)
        manifest = {'format': 1, 'androidRuntime': 'NOT VERIFIED BY PACKAGING',
            'releaseReady': False, 'signature': signature, **identity, **application,
            'androidBuildTools': sdk,
            'networkProxyTools': proxy, 'guardSourceCorrespondence': guard,
            'gitCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
            'workTreeDirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
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
    for name in ['apk', 'frida-sources', 'native-sources', 'frida-reference', 'guard-reference', 'ndk', 'build-tools', 'output']:
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--unsigned-review', action='store_true')
    parser.add_argument('--apksigner', type=Path, help='SDK lib/apksigner.jar or native launcher')
    parser.add_argument('--java', type=Path, default=Path(shutil.which('java') or 'java'))
    parser.add_argument('--certificate-sha256')
    parser.add_argument('--python', default=sys.executable)
    parser.add_argument('--android-build-bundle', type=Path)
    parser.add_argument('--android-build-sources', type=Path)
    parser.add_argument('--proxy-distribution', type=Path)
    parser.add_argument('--proxy-sources', type=Path)
    stage(parser.parse_args())
