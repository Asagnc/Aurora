"""Owned externalNativeBuild fixture: real ARM64 CMake/Ninja/LLVM and APK JNI closure."""
import hashlib
import json
import subprocess
import zipfile
from apk_ndk_targets import ROOT, acquire, module
from apk_native_host import prepare_host, prepare_ndk


class NativeAgpFixture:
    def __init__(self, directory, compiler, work, environment, zipalign):
        candidate, _ = module('agp_compiler', 'qualify-apk-jni.py').verify_candidate(compiler.resolve())
        archive, pin = acquire(directory)
        self.cmake, host = prepare_host(directory, work)
        self.ndk, self.toolchain, files = prepare_ndk(archive, pin, candidate, work)
        self.work = work; self.environment = environment; self.zipalign = zipalign
        self.sdk_managed = False
        self.framework = json.loads((ROOT / 'tools/apk-build/compose-native.json').read_text())
        module('compose_native_fetch', 'qualify-apk-jvm.py').acquire(self.framework, directory)
        (directory / 'native-host.json').write_text(json.dumps(host, indent=2) + '\n')
        (directory / 'ndk-selected.json').write_text(json.dumps(files, indent=2) + '\n')
        for key in ['LD_PRELOAD', 'LD_LIBRARY_PATH', 'CPATH', 'C_INCLUDE_PATH', 'CPLUS_INCLUDE_PATH', 'LIBRARY_PATH',
                    'COMPILER_PATH', 'GCC_EXEC_PREFIX', 'CCC_OVERRIDE_OPTIONS', 'CMAKE_TOOLCHAIN_FILE', 'CMAKE_GENERATOR', 'CC', 'CXX']:
            environment.pop(key, None)
        environment['PATH'] = str(self.cmake / 'bin') + ':' + str(self.toolchain / 'bin') + ':' + environment['PATH']

    def configure(self, project):
        module('agp_cmake_fixture', 'qualify-apk-cmake.py').write_project(project)
        # These absolute paths only exist in the private qualification fixture, never in a published project.
        if not self.sdk_managed:
            with (project / 'local.properties').open('a') as output:
                output.write('cmake.dir=' + str(self.cmake) + '\n')
        with (project / 'build.gradle').open('a') as output:
            output.write('''
android {
 ndkVersion '30.0.16248370'
 ndkPath 'REPLACE_NDK'
 defaultConfig {
   ndk { abiFilters 'arm64-v8a', 'x86_64' }
   externalNativeBuild { cmake { arguments '-DANDROID_STL=c++_shared'; targets 'owned' } }
 }
 externalNativeBuild { cmake { path file('CMakeLists.txt'); version '4.1.2' } }
}
'''.replace(" ndkPath 'REPLACE_NDK'", '' if self.sdk_managed else " ndkPath 'REPLACE_NDK'").replace('REPLACE_NDK', str(self.ndk)))
        source = project / 'src/main/java/io/github/asagnc/aurora/buildfixture/Owned.java'
        source.write_text(source.read_text().replace('public class Owned {',
            'public class Owned { static { System.loadLibrary("owned"); } public static native String nativeMarker();'))

    def framework_native(self, archive, abi, machine):
        reference = self.framework['members'][abi]
        data = archive.read('lib/' + abi + '/libandroidx.graphics.path.so')
        if len(data) != reference['bytes'] or hashlib.sha256(data).hexdigest() != reference['sha256']:
            raise ValueError('Compose native payload differs from fixed Google Maven AAR')
        needed = module('compose_elf', 'verify-linux-native.py').inspect(data, machine, False)
        if not needed <= {'libc.so', 'libm.so', 'libdl.so'}:
            raise ValueError('Unexpected Compose native dependency')
        return {'coordinate': self.framework['coordinate'], 'sha256': reference['sha256'], 'needed': sorted(needed)}

    def inspect(self, apk, compose):
        audit = module('agp_jni_elf', 'verify-linux-native.py'); results = []
        expected = {f'lib/{abi}/{name}' for abi in ['arm64-v8a', 'x86_64'] for name in ['libowned.so', 'libc++_shared.so']}
        if compose:
            expected.update('lib/' + abi + '/libandroidx.graphics.path.so' for abi in ['arm64-v8a', 'x86_64'])
        with zipfile.ZipFile(apk) as archive:
            if {name for name in archive.namelist() if name.startswith('lib/')} != expected:
                raise ValueError('Actual APK JNI layout differs')
            for abi, triple, machine in [('arm64-v8a', 'aarch64-linux-android', 183), ('x86_64', 'x86_64-linux-android', 62)]:
                owned = archive.read('lib/' + abi + '/libowned.so'); runtime = archive.read('lib/' + abi + '/libc++_shared.so')
                needed = audit.inspect(owned, machine, False); audit.inspect(runtime, machine, False)
                if not needed <= {'libc.so', 'libm.so', 'libdl.so', 'libc++_shared.so'} or 'libc++_shared.so' not in needed:
                    raise ValueError('APK JNI dependency closure differs')
                original = self.toolchain / 'sysroot/usr/lib' / triple / 'libc++_shared.so'
                # AGP strips packaged runtime. Compare the deterministic output of this same owned strip path.
                stripped = self.work / (abi + '-libcxx.so'); stripped.write_bytes(original.read_bytes())
                subprocess.run([str(self.toolchain / 'bin/llvm-strip'), '--strip-unneeded', str(stripped)], env=self.environment, check=True, timeout=30)
                if runtime != stripped.read_bytes():
                    raise ValueError('Packaged C++ runtime does not match pinned target data and stripping')
                selected = self.work / (abi + '-owned.so'); selected.write_bytes(owned)
                symbols = subprocess.check_output([str(self.toolchain / 'bin/llvm-readobj'), '--dyn-symbols', str(selected)], env=self.environment, text=True, timeout=15)
                if 'Java_io_github_asagnc_aurora_buildfixture_Owned_nativeMarker' not in symbols:
                    raise ValueError('Packaged JNI export missing')
                results.append({'abi': abi, 'ownedSha256': hashlib.sha256(owned).hexdigest(), 'runtimeSha256': hashlib.sha256(runtime).hexdigest(),
                    'needed': sorted(needed), 'frameworkNative': self.framework_native(archive, abi, machine) if compose else None})
        subprocess.run([str(self.zipalign), '-c', '-P', '16', '-v', '4', str(apk)], env=self.environment, check=True, timeout=30,
            stdout=subprocess.DEVNULL)
        return results
