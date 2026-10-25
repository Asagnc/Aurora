#!/usr/bin/env python3
"""Actual ARM64 native CMake/Ninja Android JNI qualification, before AGP integration."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
from apk_ndk_targets import acquire, module
from apk_native_host import prepare_host, prepare_ndk

ROOT = Path(__file__).resolve().parents[1]


def write_project(project):
    project.mkdir(parents=True, exist_ok=True)
    (project / 'owned.cpp').write_text('''#include <jni.h>
#include <vector>
#include <string>
extern "C" JNIEXPORT jstring JNICALL Java_io_github_asagnc_aurora_buildfixture_Owned_nativeMarker(JNIEnv* env, jclass) {
  std::vector<int> values{1,2,3};
  std::string text = "owned-cmake-jni-" + std::to_string(values.at(2));
  return env->NewStringUTF(text.c_str());
}
''')
    (project / 'CMakeLists.txt').write_text('''cmake_minimum_required(VERSION 4.1.2)
project(OwnedJni LANGUAGES C CXX)
add_library(owned SHARED owned.cpp)
target_compile_features(owned PRIVATE cxx_std_17)
target_link_options(owned PRIVATE -Wl,--no-undefined,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384,-z,common-page-size=16384)
''')


def fixture(tools, work, directory, target, environment):
    cmake, ndk, toolchain = tools
    abi, machine = target
    project = work / abi; write_project(project)
    build = project / 'build'
    commands = [[str(cmake / 'bin/cmake'), '-S', str(project), '-B', str(build), '-G', 'Ninja',
        '-DCMAKE_MAKE_PROGRAM=' + str(cmake / 'bin/ninja'), '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'),
        '-DANDROID_ABI=' + abi, '-DANDROID_PLATFORM=android-36', '-DANDROID_STL=c++_shared', '-DCMAKE_BUILD_TYPE=Release'],
        [str(cmake / 'bin/cmake'), '--build', str(build), '--verbose']]
    with (directory / (abi + '-cmake.txt')).open('wb') as log:
        for command in commands:
            subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT, timeout=180, check=True)
    source = build / 'libowned.so'; data = source.read_bytes()
    audit = module('cmake_android_elf', 'verify-linux-native.py')
    needed = audit.inspect(data, machine, False)
    if not needed <= {'libc.so', 'libm.so', 'libdl.so', 'libc++_shared.so'} or 'libc++_shared.so' not in needed:
        raise ValueError('Unexpected Android JNI closure')
    symbols = subprocess.check_output([str(toolchain / 'bin/llvm-readobj'), '--dyn-symbols', str(source)], env=environment, text=True, timeout=15)
    if 'Java_io_github_asagnc_aurora_buildfixture_Owned_nativeMarker' not in symbols:
        raise ValueError('JNI dynamic export missing')
    shutil.copyfile(source, directory / (abi + '-libowned.so'))
    return {'abi': abi, 'machine': machine, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(), 'needed': sorted(needed)}


def qualify(directory, compiler):
    if platform.system() != 'Linux' or platform.machine().lower() not in {'aarch64', 'arm64'}:
        raise ValueError('Actual Linux ARM64 required')
    directory = directory.resolve(); directory.mkdir(parents=True, exist_ok=True)
    if (directory / 'report.json').exists():
        raise FileExistsError('Preserve completed qualification')
    candidate, proof = module('cmake_compiler', 'qualify-apk-jni.py').verify_candidate(compiler.resolve())
    archive, pin = acquire(directory)
    with tempfile.TemporaryDirectory(prefix='.owned-cmake-', dir=directory) as temporary:
        work = Path(temporary)
        cmake, host = prepare_host(directory, work)
        (directory / 'native-host.json').write_text(json.dumps(host, indent=2) + '\n')
        ndk, toolchain, files = prepare_ndk(archive, pin, candidate, work)
        (directory / 'ndk-selected.json').write_text(json.dumps(files, indent=2) + '\n')
        environment = os.environ.copy()
        for key in ['LD_PRELOAD', 'LD_LIBRARY_PATH', 'CPATH', 'C_INCLUDE_PATH', 'CPLUS_INCLUDE_PATH', 'LIBRARY_PATH',
                    'COMPILER_PATH', 'GCC_EXEC_PREFIX', 'CCC_OVERRIDE_OPTIONS', 'CMAKE_TOOLCHAIN_FILE', 'CMAKE_GENERATOR', 'CC', 'CXX']:
            environment.pop(key, None)
        environment.update(HOME=str(work / 'home'), PATH=str(cmake / 'bin') + ':' + str(toolchain / 'bin') + ':/usr/bin:/bin')
        versions = {}
        for name, version in [('cmake', '4.1.2'), ('ninja', '1.13.2')]:
            text = subprocess.check_output([str(cmake / 'bin' / name), '--version'], env=environment, text=True, timeout=15)
            if version not in text:
                raise ValueError('Native host version differs')
            versions[name] = text
        results = [fixture((cmake, ndk, toolchain), work, directory, (abi, machine), environment)
                   for abi, machine in [('arm64-v8a', 183), ('x86_64', 62)]]
        report = {'linuxArm64CmakeNinjaAndroidJniQualified': True, 'versions': versions, 'jni': results,
            'ndkArchiveSha256': pin['sha256'], 'compilerSourceArchives': proof['sourceArchives'],
            'targetFiles': len(files), 'targetBytes': sum(item['bytes'] for item in files),
            'ndkEquivalenceVerified': False, 'fullAgpJniBuildQualified': False, 'androidRuntime': 'NOT TESTED', 'phoneInstallReady': False}
        (directory / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--compiler', type=Path, required=True)
    args = parser.parse_args(); qualify(args.directory, args.compiler)
