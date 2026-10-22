#!/usr/bin/env python3
"""Compose the real PTY transport with chroot/cgroup supervision on a Root host.
Uses the host shell and matching libraries, not Ubuntu/Android evidence.
"""
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import signal
import struct
import subprocess
import threading
import time
import unittest


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(file))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


mounts = module('mount_tests', 'verify-linux-chroot-mounts.py')
pty = module('pty_tests', 'verify-native-pty.py')


class LinuxPtyTest(mounts.MountTest):
    until = pty.NativePtyTest.until
    frame = staticmethod(pty.NativePtyTest.frame)
    result = pty.NativePtyTest.result

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.pty_binary = str(Path(cls.directory.name) / 'pty')
        source = Path(__file__).resolve().parents[1] / 'app/src/main/cpp/pty.c'
        subprocess.run(['cc', '-std=c11', '-Wall', '-Wextra', '-Werror', str(source), '-o', cls.pty_binary], check=True)

    def executable(self, root, command, target):
        source = Path(shutil.which(command)).resolve()
        libraries = subprocess.run(['ldd', str(source)], check=True, capture_output=True, text=True).stdout
        for name in re.findall(r'(/[^\s()]+)', libraries):
            destination = root / name.lstrip('/')
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(name, destination)
        destination = root / target.lstrip('/')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)

    def open_terminal(self, command, timeout=6000, guest_timeout=5000):
        root, work = self.shell_fixture()
        for executable in ('stty', 'setsid', 'sleep'):
            self.executable(root, executable, '/bin/' + executable)
        args = self.run_arguments(root, work, command, guest_timeout)
        args[1] = '--run-pty'
        child = subprocess.Popen([self.pty_binary, '24', '80', str(timeout), '--'] + args,
                                 stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        output, changed = bytearray(), threading.Condition()

        def consume():
            while True:
                data = os.read(child.stdout.fileno(), 16384)
                if not data:
                    break
                with changed:
                    output.extend(data)
                    changed.notify_all()

        reader = threading.Thread(target=consume, daemon=True)
        reader.start()
        def cleanup():
            pty.NativePtyTest.cleanup(child)
            reader.join(3)
            self.await_recovery(work / 'run-state')

        self.addCleanup(cleanup)
        return child, output, changed, reader, root, work

    def await_recovery(self, state):
        # The separate guard may still own the startup lock after PTY transport
        # exit. A test retries explicit recovery; no false stopped proof.
        deadline = time.monotonic() + 6
        while True:
            result = subprocess.run([self.binary, '--recover', '--state-dir', str(state)],
                                    capture_output=True, timeout=8)
            if result.returncode == 0:
                self.assertTrue(json.loads(result.stdout)['recovered'])
                return
            self.assertLess(time.monotonic(), deadline, result.stderr.decode())
            time.sleep(0.02)

    def completion(self, work):
        state = work / 'run-state'
        self.await_recovery(state)
        self.assertEqual([], list(state.glob('*.state')))
        result = json.loads((state / 'completed.json').read_text())
        self.assertTrue(result['scopeRemoved'])
        return result

    def test_guest_tty_unicode_resize_and_nonzero_exit(self):
        child, out, changed, reader, root, work = self.open_terminal(
            'test -t 0 && test -t 1 && test -t 2 || exit 91; '
            'test "$TERM" = xterm-256color || exit 92; '
            'printf ready; read value; printf "<%s>" "$value"; /bin/stty size; exit 37')
        self.until(out, changed, b'ready')
        self.frame(child, 2, struct.pack('<HH', 41, 113))
        self.frame(child, 1, '极光😀\n'.encode())
        result = self.result(child, reader)
        self.assertEqual('exit', result['reason'])
        self.assertEqual(37, result['exitCode'])
        self.assertIn('<极光😀>'.encode(), out)
        self.assertIn(b'41 113', out)
        completion = self.completion(work)
        self.assertEqual(37, completion['exitCode'])
        self.assertFalse(completion['interrupted'])
        self.assert_host_unchanged(root, work)

    def test_ctrl_c_reaches_guest_foreground_without_killing_supervision(self):
        child, out, changed, reader, _, work = self.open_terminal(
            "trap 'printf caught; exit 19' INT; printf ready; read value")
        self.until(out, changed, b'ready')
        self.frame(child, 1, b'\x03')
        self.assertEqual(19, self.result(child, reader)['exitCode'])
        self.assertIn(b'caught', out)
        self.assertFalse(self.completion(work)['interrupted'])

    def detached_writer(self, timeout=6000):
        values = self.open_terminal(
            "/bin/setsid /bin/sh -c 'trap \"\" HUP TERM; echo $$ > /mnt/detached; exec /bin/sleep 60' & "
            'printf ready; read value', timeout=timeout, guest_timeout=10000)
        child, out, changed, _, _, work = values
        self.until(out, changed, b'ready')
        deadline = time.monotonic() + 3
        marker = work / 'project/detached'
        while (not marker.exists() or not marker.read_text().strip()) and time.monotonic() < deadline:
            time.sleep(0.01)
        pid = int(marker.read_text())
        scope = mounts.base.group(pid)
        self.assertTrue(Path(scope).name.startswith('aurora-linux-'))
        self.assertEqual(pid, os.getsid(pid))
        return values, pid, scope

    def test_transport_stop_reclaims_detached_guest_session(self):
        (child, _, _, reader, root, work), pid, scope = self.detached_writer()
        child.terminate()
        self.assertEqual('stopped', self.result(child, reader)['reason'])
        self.await_recovery(work / 'run-state')
        self.assert_gone(pid)
        self.assert_scope_removed(scope)
        self.assertTrue(self.completion(work)['interrupted'])
        self.assert_host_unchanged(root, work)

    def test_transport_deadline_reclaims_detached_guest_session(self):
        (child, _, _, reader, root, work), pid, scope = self.detached_writer(timeout=1200)
        self.assertEqual('deadline', self.result(child, reader)['reason'])
        self.await_recovery(work / 'run-state')
        self.assert_gone(pid)
        self.assert_scope_removed(scope)
        self.assertTrue(self.completion(work)['interrupted'])
        self.assert_host_unchanged(root, work)

    def test_terminal_mode_refuses_pipes_without_creating_run_state(self):
        root, work = self.shell_fixture()
        args = self.run_arguments(root, work, 'exit 0')
        args[1] = '--run-pty'
        result = subprocess.run(args, capture_output=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((work / 'run-state').exists())
        self.assertEqual(b'', result.stdout)

    def test_fresh_recovery_after_both_supervisors_die_does_not_invent_completion(self):
        (child, _, _, reader, root, work), pid, scope = self.detached_writer()
        guard = int(Path(scope).name.split('-')[2])
        stat = Path(f'/proc/{guard}/stat').read_text().rsplit(')', 1)[1].split()
        coordinator = int(stat[1])
        os.kill(guard, signal.SIGSTOP)
        os.kill(coordinator, signal.SIGKILL)
        os.kill(guard, signal.SIGKILL)
        self.assertNotEqual(0, self.result(child, reader)['exitCode'])
        self.await_recovery(work / 'run-state')
        self.assert_gone(pid)
        self.assert_scope_removed(scope)
        self.assertFalse((work / 'run-state/completed.json').exists())
        self.assert_host_unchanged(root, work)


if __name__ == '__main__':
    suite = unittest.TestSuite(LinuxPtyTest(name) for name in LinuxPtyTest.__dict__ if name.startswith('test_'))
    outcome = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(not outcome.wasSuccessful())
