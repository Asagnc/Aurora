"""Relocatable ARM64 native build bundle; official SDK/JVM distributions remain separately pinned inputs."""
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import tarfile

BASE_RUNTIME = {'libc.so.6', 'libm.so.6', 'libdl.so.2', 'libpthread.so.0', 'librt.so.1', 'ld-linux-aarch64.so.1'}


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def sdk_cmake_metadata(directory):
    (directory / 'source.properties').write_text('Pkg.Revision = 4.1.2\nPkg.Path = cmake;4.1.2\nPkg.Desc = Aurora qualified ARM64 CMake 4.1.2\n')
    (directory / 'package.xml').write_text('''<?xml version="1.0" encoding="UTF-8"?>
<repo:repository xmlns:repo="http://schemas.android.com/repository/android/common/02"
 xmlns:generic="http://schemas.android.com/repository/android/generic/02" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
 <localPackage path="cmake;4.1.2" obsolete="false"><type-details xsi:type="generic:genericDetailsType"/>
 <revision><major>4</major><minor>1</minor><micro>2</micro></revision>
 <display-name>Aurora qualified ARM64 CMake 4.1.2</display-name></localPackage>
</repo:repository>
''')


def sdk_ndk_metadata(directory):
    (directory / 'package.xml').write_text('''<?xml version="1.0" encoding="UTF-8"?>
<repo:repository xmlns:repo="http://schemas.android.com/repository/android/common/02"
 xmlns:generic="http://schemas.android.com/repository/android/generic/02" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
 <localPackage path="ndk;30.0.16248370" obsolete="false"><type-details xsi:type="generic:genericDetailsType"/>
 <revision><major>30</major><minor>0</minor><micro>16248370</micro></revision>
 <display-name>Aurora ARM64 compiler with NDK30 target data; not full patched NDK equivalence</display-name></localPackage>
</repo:repository>
''')


def package_owner(path):
    variants = [path, path.resolve()]
    for selected in list(variants):
        text = str(selected)
        if text.startswith('/usr/lib/'):
            variants.append(Path(text.removeprefix('/usr')))
        elif text.startswith('/lib/'):
            variants.append(Path('/usr' + text))
    for candidate in variants:
        result = subprocess.run(['dpkg-query', '-S', str(candidate)], text=True, capture_output=True, timeout=15)
        if not result.returncode:
            owner = result.stdout.splitlines()[0].split(': /', 1)[0].strip()
            return subprocess.check_output(['dpkg-query', '-W', '-f=${Package}\t${Version}\t${Architecture}\t${source:Package}\t${source:Version}', owner], text=True, timeout=15).split('\t')
    raise ValueError('No installed-package provenance for host dependency: ' + path.name)


def host_executables(sdk):
    roots = [sdk / 'build-tools/36.0.0', sdk / 'cmake/4.1.2/bin', sdk / 'ndk/30.0.16248370/toolchains/llvm/prebuilt/linux-x86_64/bin']
    for root in roots:
        for path in sorted(root.rglob('*')):
            if not path.is_file():
                continue
            with path.open('rb') as stream:
                header = stream.read(20)
            if not header.startswith(b'\x7fELF'):
                continue
            if header[:6] != b'\x7fELF\x02\x01' or int.from_bytes(header[18:20], 'little') != 183:
                raise ValueError('Foreign host ELF in portable build bundle')
            yield path


def runtime_closure(sdk, directory):
    directory.mkdir(); libraries = {}; observed = []
    for path in host_executables(sdk):
        output = subprocess.check_output(['ldd', str(path)], text=True, timeout=15)
        if 'not found' in output:
            raise ValueError('Unresolved host dependency before bundling')
        observed.append({'path': path.relative_to(sdk).as_posix(), 'sha256': digest(path), 'ldd': output})
        for name, resolved in re.findall(r'^\s*(\S+) => (/\S+)', output, re.M):
            if name in BASE_RUNTIME or Path(resolved).is_relative_to(sdk):
                continue
            if not re.fullmatch(r'[A-Za-z0-9_.+-]{1,128}', name):
                raise ValueError('Unsafe host dependency soname')
            source = Path(resolved).resolve(); identity = digest(source)
            if name in libraries and libraries[name]['sha256'] != identity:
                raise ValueError('Conflicting host dependency soname')
            if name in libraries:
                continue
            shutil.copyfile(source, directory / name); (directory / name).chmod(0o644)
            fields = package_owner(Path(resolved))
            notice = Path('/usr/share/doc') / fields[0] / 'copyright'
            if not notice.is_file():
                raise ValueError('Host dependency package notice missing')
            notices = directory / 'notices'; notices.mkdir(exist_ok=True)
            target = notices / (fields[0] + '-copyright.txt'); shutil.copyfile(notice, target)
            libraries[name] = {'sha256': identity, 'bytes': source.stat().st_size,
                'binaryPackage': fields[:3], 'sourcePackage': fields[3:], 'copyrightSha256': digest(target)}
    return {'libraries': libraries, 'hostExecutables': observed, 'systemProvided': sorted(BASE_RUNTIME),
        'scope': 'Actual host CLI transitive ELF closure. GNU libc is supplied by the qualified Root image; JDK GUI paths are not qualified.'}


def verify_private_resolution(sdk, runtime, environment):
    observed = []
    for path in host_executables(sdk):
        text = subprocess.check_output(['ldd', str(path)], env=environment, text=True, timeout=15)
        if 'not found' in text:
            raise ValueError('Portable host dependency is unresolved')
        for name, location in re.findall(r'^\s*(\S+) => (/\S+)', text, re.M):
            selected = Path(location).resolve()
            if name not in BASE_RUNTIME and not selected.is_relative_to(sdk) and not selected.is_relative_to(runtime):
                raise ValueError('Portable host still depends on an unbundled system library: ' + name)
        observed.append({'path': path.relative_to(sdk).as_posix(), 'ldd': text})
    return observed


def relocate(sdk, fixture, work):
    relocated = work / 'portable-check'; relocated.mkdir()
    selected = relocated / 'sdk'; shutil.copytree(sdk, selected, symlinks=True)
    ndk = selected / 'ndk/30.0.16248370'; ndk.parent.mkdir(parents=True)
    shutil.copytree(fixture.ndk, ndk, symlinks=True)
    sdk_ndk_metadata(ndk)
    cmake = selected / 'cmake/4.1.2'; cmake.parent.mkdir(parents=True)
    shutil.copytree(fixture.cmake, cmake, symlinks=True); sdk_cmake_metadata(cmake)
    closure = runtime_closure(selected, relocated / 'runtime-lib')
    return relocated, selected, closure


def source_notices(directories, root):
    records = []; destination = root / 'source-notices'; destination.mkdir()
    for directory in directories:
        for entry in json.loads((directory / 'materials.json').read_text()):
            path = directory / 'sources' / entry['archive']
            if path.stat().st_size != entry['archiveBytes'] or digest(path) != entry['archiveSha256']:
                raise ValueError('Source material changed before notice packaging')
            expected = {item['path']: item for item in entry['notices']}; found = []
            prefix = entry['repository'].rsplit('/', 1)[-1] + '-' + entry['commit'] + '/' if entry['repository'].startswith('https://github.com/') else ''
            with tarfile.open(path, 'r|gz') as archive:
                for item in archive:
                    name = item.name.removeprefix(prefix)
                    if name not in expected:
                        continue
                    notice = expected[name]
                    if name in found or not item.isfile() or item.size != notice['bytes'] or item.size > 4 * 1024 ** 2:
                        raise ValueError('Source notice shape differs')
                    data = archive.extractfile(item).read()
                    if hashlib.sha256(data).hexdigest() != notice['sha256']:
                        raise ValueError('Source notice digest differs')
                    target = destination / (notice['sha256'] + '.txt')
                    if not target.exists():
                        target.write_bytes(data)
                    found.append(name)
            if set(found) != expected.keys():
                raise ValueError('Fixed source notice missing from archive')
            records.append({key: entry[key] for key in ['id', 'repository', 'commit', 'archive', 'archiveBytes', 'archiveSha256', 'notices']})
    (root / 'source-materials.json').write_text(json.dumps(records, indent=2) + '\n')


def export_native(sdk, runtime, output, report, materials):
    """Exclude official SDK JAR/platform data: installer obtains those directly from the pinned upstream archives."""
    if output.exists():
        raise FileExistsError('Preserve existing native build bundle')
    stage = output.parent / '.android-host-stage'; stage.mkdir()
    try:
        root = stage / 'android-host'; root.mkdir()
        resources = root / 'resources'; resources.mkdir()
        for name in ['aapt2', 'aidl', 'zipalign']:
            shutil.copyfile(sdk / 'build-tools/36.0.0' / name, resources / name); (resources / name).chmod(0o755)
        shutil.copytree(sdk / 'ndk/30.0.16248370', root / 'ndk', symlinks=True)
        shutil.copytree(sdk / 'cmake/4.1.2', root / 'cmake', symlinks=True)
        shutil.copytree(runtime, root / 'runtime-lib', symlinks=True)
        source_notices(materials, root)
        (root / 'qualification.json').write_text(json.dumps(report, indent=2) + '\n')
        files = []; total = 0
        for path in sorted(root.rglob('*')):
            if path.is_symlink():
                target = path.resolve()
                if not target.is_relative_to(root.resolve()):
                    raise ValueError('Portable bundle link escapes its root')
            elif path.is_file():
                total += path.stat().st_size
                files.append({'path': path.relative_to(root).as_posix(), 'bytes': path.stat().st_size, 'sha256': digest(path)})
        if total > 1536 * 1024 ** 2 or len(files) > 20000:
            raise ValueError('Portable native bundle exceeds install budget')
        (root / 'files.json').write_text(json.dumps(files, indent=2) + '\n')
        temporary = output.with_suffix('.partial')
        try:
            with temporary.open('xb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as compressed, tarfile.open(fileobj=compressed, mode='w|') as archive:
                for path in [root] + sorted(root.rglob('*')):
                    item = archive.gettarinfo(str(path), arcname=path.relative_to(stage).as_posix())
                    item.uid = item.gid = item.mtime = 0; item.uname = item.gname = ''
                    if item.isfile():
                        with path.open('rb') as stream:
                            archive.addfile(item, stream)
                    else:
                        archive.addfile(item)
            temporary.replace(output)
        finally:
            temporary.unlink(missing_ok=True)
        return {'archive': output.name, 'bytes': output.stat().st_size, 'sha256': digest(output),
            'expandedBytes': total, 'regularFiles': len(files), 'containsOfficialSdkDistributions': False}
    finally:
        if stage.resolve().parent != output.parent.resolve():
            raise ValueError('Refusing cleanup outside the selected bundle output parent')
        shutil.rmtree(stage)
