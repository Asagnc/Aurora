#!/usr/bin/env python3
"""Actual Linux Root namespace tests; not Android/SELinux acceptance."""
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import unittest


def children(pid):
    try:
        return [int(value) for value in Path(f'/proc/{pid}/task/{pid}/children').read_text().split()]
    except FileNotFoundError:
        return []


def descendants(pid):
    result = children(pid)
    for child in list(result):
        result.extend(descendants(child))
    return result


class NamespaceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.geteuid() != 0:
            raise RuntimeError('Run this explicit host Root suite with sudo')
        cls.directory = tempfile.TemporaryDirectory(prefix='aurora-namespace-')
        Path(cls.directory.name).chmod(0o755)
        cls.binary = str(Path(cls.directory.name) / 'namespace')
        source = Path(__file__).resolve().parents[1] / 'app/src/main/cpp/linux/namespace_probe.c'
        subprocess.run(['cc', '-std=c11', '-O2', '-DNDEBUG', '-Wall', '-Wextra', '-Werror',
                        str(source), '-o', cls.binary], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def start(self, hold=5000):
        process = subprocess.Popen([self.binary, '--probe', '--hold-ms', str(hold)],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(self.stop, process)
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            for pid in descendants(process.pid):
                try:
                    if os.getsid(pid) == pid:
                        return process, pid
                except ProcessLookupError:
                    pass
            if process.poll() is not None:
                out, err = process.communicate()
                self.fail(f'Namespace workload did not start: {process.returncode}, {out!r}, {err!r}')
            time.sleep(0.01)
        self.fail('Detached probe child did not appear')

    @staticmethod
    def stop(process):
        if process.poll() is None:
            process.kill()
        process.communicate(timeout=5)

    def assert_gone(self, pid):
        deadline = time.monotonic() + 3
        while Path(f'/proc/{pid}').exists() and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertFalse(Path(f'/proc/{pid}').exists(), f'Remaining namespace child: {pid}')

    def test_normal_exit_reaps_detached_child_and_preserves_host_namespace(self):
        before = os.stat('/proc/self/ns/mnt').st_ino
        process, detached = self.start(500)
        self.assertNotEqual(before, os.stat(f'/proc/{process.pid}/ns/mnt').st_ino)
        mountinfo = Path(f'/proc/{process.pid}/mountinfo').read_text()
        self.assertNotIn('shared:', mountinfo)
        out, err = process.communicate(timeout=5)
        self.assertEqual(0, process.returncode, err.decode())
        self.assertTrue(json.loads(out)['namespaceInitReaped'])
        self.assert_gone(detached)
        self.assertEqual(before, os.stat('/proc/self/ns/mnt').st_ino)

    def test_parent_termination_and_sigkill_reap_detached_children(self):
        for signum in (signal.SIGTERM, signal.SIGKILL):
            with self.subTest(signum=signum):
                process, detached = self.start()
                process.send_signal(signum)
                process.communicate(timeout=5)
                self.assertEqual(-signum, process.returncode)
                self.assert_gone(detached)

    def test_init_sigkill_tears_down_namespace_without_success_result(self):
        process, detached = self.start()
        init = children(process.pid)
        self.assertEqual(1, len(init))
        os.kill(init[0], signal.SIGKILL)
        out, _ = process.communicate(timeout=5)
        self.assertEqual(137, process.returncode)
        self.assertEqual(b'', out)
        self.assert_gone(detached)

    def test_ordinary_uid_is_refused_and_bad_arguments_are_rejected(self):
        def ordinary():
            os.setgid(65534)
            os.setuid(65534)
        result = subprocess.run([self.binary, '--probe'], preexec_fn=ordinary, capture_output=True, timeout=5)
        self.assertEqual(126, result.returncode)
        self.assertEqual('root_required', json.loads(result.stderr)['operation'])
        for args in [[], ['--unknown'], ['--probe', '--hold-ms', '9000']]:
            self.assertEqual(64, subprocess.run([self.binary] + args, capture_output=True, timeout=5).returncode)


if __name__ == '__main__':
    unittest.main(verbosity=2)
