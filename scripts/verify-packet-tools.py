#!/usr/bin/env python3
"""Real pinned tcpdump/libpcap offline tests on Linux; not Android Root capture."""
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def build(directory):
    caches = sorted((ROOT / 'app/.cxx/Debug').glob('*/x86_64/_deps'))
    if len(caches) != 1:
        raise RuntimeError('Expected one verified current Android source generation')
    cache = caches[0]
    prefix = directory / 'prefix'
    common = ['-G', 'Ninja', '-DCMAKE_BUILD_TYPE=Release', '-DBUILD_SHARED_LIBS=OFF',
              '-DCMAKE_INSTALL_PREFIX=' + str(prefix), '-DCMAKE_INSTALL_LIBDIR=lib']
    pcap_build = directory / 'pcap-build'
    subprocess.run(['cmake', '-S', str(cache / 'aurora_pcap_source-src'), '-B', str(pcap_build)] + common +
                   ['-DPCAP_TYPE=linux', '-DBUILD_WITH_LIBNL=OFF', '-DENABLE_REMOTE=OFF',
                    '-DDISABLE_BLUETOOTH=ON', '-DDISABLE_DBUS=ON', '-DDISABLE_RDMA=ON',
                    '-DDISABLE_DAG=ON', '-DDISABLE_SNF=ON', '-DDISABLE_NETMAP=ON',
                    '-DDISABLE_LINUX_USBMON=ON'], check=True)
    subprocess.run(['cmake', '--build', str(pcap_build), '--parallel', '2', '--target', 'pcap_static'], check=True)
    subprocess.run(['cmake', '--install', str(pcap_build)], check=True)
    source = directory / 'tcpdump-source'
    shutil.copytree(cache / 'aurora_tcpdump_source-src', source)
    shutil.copyfile(ROOT / 'app/src/main/cpp/packet/FindPCAP.cmake', source / 'cmake/Modules/FindPCAP.cmake')
    program = directory / 'tcpdump-build'
    subprocess.run(['cmake', '-S', str(source), '-B', str(program)] + common +
                   ['-DWITH_CRYPTO=OFF', '-DWITH_SMI=OFF', '-DWITH_CAP_NG=OFF', '-DWITH_CAPSICUM=OFF',
                    '-DPCAP_INCLUDE_DIR=' + str(prefix / 'include'),
                    '-DPCAP_LIBRARY=' + str(prefix / 'lib/libpcap.a')], check=True)
    subprocess.run(['cmake', '--build', str(program), '--parallel', '2', '--target', 'tcpdump'], check=True)
    baseline = subprocess.run([str(program / 'tcpdump'), '-nn', '-r', str(directory / 'input.pcap'),
                               '-w', '/dev/full'], capture_output=True, timeout=10)
    print(f'Unmodified upstream /dev/full exit status: {baseline.returncode}', flush=True)
    subprocess.run(['cmake', '-DSOURCE_DIR=' + str(source), '-P',
                    str(ROOT / 'app/src/main/cpp/packet/PatchTcpdump.cmake')], check=True)
    subprocess.run(['cmake', '--build', str(program), '--parallel', '2', '--target', 'tcpdump'], check=True)
    return program / 'tcpdump'


class PacketToolsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.name != 'posix':
            raise RuntimeError('Explicit Linux host suite only')
        cls.directory = tempfile.TemporaryDirectory(prefix='aurora-packet-tools-')
        cls.addClassCleanup(cls.directory.cleanup)
        cls.root = Path(cls.directory.name)
        cls.packet = bytes.fromhex('00000000000000000000000008004500001c0000000040110000'
                                   '7f0000017f00000104d210e100080000')
        cls.capture = struct.pack('<IHHIIII', 0xa1b2c3d4, 2, 4, 0, 0, 65535, 1) + \
            struct.pack('<IIII', 1, 0, len(cls.packet), len(cls.packet)) + cls.packet
        (cls.root / 'input.pcap').write_bytes(cls.capture)
        cls.binary = build(cls.root)

    def run_tool(self, *args):
        return subprocess.run([str(self.binary), *args], cwd=self.root, capture_output=True,
                              timeout=10, env={**os.environ, 'LC_ALL': 'C'})

    def test_worker_complete_packet_boundaries_and_write_error(self):
        prefix = self.root / 'prefix'
        module = ROOT / 'app/src/main/cpp/packet'
        binary = self.root / 'worker-test'
        subprocess.run(['cc', '-Wall', '-Wextra', '-Werror', '-I' + str(prefix / 'include'),
                        '-I' + str(module), str(ROOT / 'scripts/packet-worker-test.c'),
                        str(module / 'WorkerLoop.c'), str(prefix / 'lib/libpcap.a'),
                        '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True, timeout=10)

    def test_exact_stable_versions(self):
        result = self.run_tool('--version')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b'tcpdump version 4.99.7', result.stdout)
        self.assertIn(b'libpcap version 1.10.7', result.stdout)

    def test_matching_filter_preserves_packet(self):
        result = self.run_tool('-nn', '-r', 'input.pcap', '-w', 'matching.pcap', 'udp', 'dst', 'port', '4321')
        self.assertEqual(result.returncode, 0, result.stderr)
        output = (self.root / 'matching.pcap').read_bytes()
        self.assertEqual(len(output), len(self.capture))
        self.assertEqual(output[40:], self.packet)

    def test_nonmatching_filter_writes_only_file_header(self):
        result = self.run_tool('-nn', '-r', 'input.pcap', '-w', 'empty.pcap', 'udp', 'dst', 'port', '123')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.root / 'empty.pcap').stat().st_size, 24)

    def test_invalid_filter_fails(self):
        result = self.run_tool('-nn', '-r', 'input.pcap', 'udp', 'and', '(')
        self.assertNotEqual(result.returncode, 0)

    def test_truncated_input_fails(self):
        (self.root / 'truncated.pcap').write_bytes(self.capture[:-1])
        result = self.run_tool('-nn', '-r', 'truncated.pcap')
        self.assertNotEqual(result.returncode, 0)

    def test_agent_style_literal_filter_and_packet_limit(self):
        result = self.run_tool('-nn', '-t', '-c', '1', '-r', 'input.pcap', '--', 'udp dst port 4321')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(b'.4321', result.stdout)

    def test_filter_cannot_be_reinterpreted_as_version_flag(self):
        result = self.run_tool('-nn', '-t', '-c', '1', '-r', 'input.pcap', '--', '--version')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(b'tcpdump version', result.stdout)

    def test_full_output_device_cannot_report_success(self):
        result = self.run_tool('-nn', '-r', 'input.pcap', '-w', '/dev/full')
        self.assertNotEqual(result.returncode, 0, 'A buffered write failure was reported as success')

    def test_nonregular_output_does_not_require_fsync(self):
        result = self.run_tool('-nn', '-r', 'input.pcap', '-w', '/dev/null')
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rotation_closes_and_preserves_both_segments(self):
        record = self.capture[24:]
        (self.root / 'large.pcap').write_bytes(self.capture[:24] + record * 20000)
        result = self.run_tool('-nn', '-r', 'large.pcap', '-C', '1', '-W', '2', '-w', 'rotated.pcap')
        self.assertEqual(result.returncode, 0, result.stderr)
        files = sorted(self.root.glob('rotated.pcap*'))
        self.assertEqual(len(files), 2)
        count = 0
        for path in files:
            data = path.read_bytes()
            offset = 24
            while offset < len(data):
                self.assertGreaterEqual(len(data) - offset, 16)
                length = struct.unpack_from('<I', data, offset + 8)[0]
                self.assertEqual(length, len(self.packet))
                self.assertEqual(data[offset + 16:offset + 16 + length], self.packet)
                offset += 16 + length
                count += 1
            self.assertEqual(offset, len(data))
        self.assertEqual(count, 20000)


if __name__ == '__main__':
    unittest.main(verbosity=2)
