#!/usr/bin/env python3
"""Fixed native chroot/bind tests, not an Ubuntu guest/Android execution claim."""
import importlib.util
import json
import os
from pathlib import Path
import signal
import re
import shutil
import subprocess
import tempfile
import time
import unittest

spec = importlib.util.spec_from_file_location('scope_tests', Path(__file__).with_name('verify-linux-scopes.py'))
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)


class MountTest(base.ScopeTest):
    def recover_state(self, state):
        result = subprocess.run([self.binary, '--recover', '--state-dir', str(state)], capture_output=True, timeout=8)
        self.assertEqual(0, result.returncode, result.stderr.decode())

    def fixture(self, root=None):
        directory = Path(tempfile.mkdtemp(prefix='mount-fixture-', dir=self.directory.name))
        if root is None:
            root = directory / 'rootfs'
            root.mkdir()
            for name in ('root', 'tmp', 'mnt', 'proc', 'dev'):
                (root / name).mkdir()
            (root / 'aurora-fixture').write_text('aurora-chroot-fixture-v1\n')
        work = directory / 'work'
        work.mkdir()
        for name in ('view', 'home', 'tmp', 'project'):
            (work / name).mkdir()
        self.mount_args = ['--chroot-fixture', str(root), str(work)]
        return root, work

    def shell_fixture(self):
        root, work = self.fixture()
        shell = Path('/bin/sh').resolve()
        libraries = subprocess.run(['ldd', str(shell)], check=True, capture_output=True, text=True).stdout
        paths = [str(shell)] + re.findall(r'(/[^\s()]+)', libraries)
        for name in paths:
            source = Path(name)
            target = root / source.relative_to('/')
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        (root / 'bin').mkdir(exist_ok=True)
        shutil.copy2(shell, root / 'bin/sh')
        self.mount_args[0] = '--chroot-shell-fixture'
        return root, work

    def test_real_guest_shell_clean_environment_and_nonzero_exit(self):
        root, work = self.shell_fixture()
        previous = os.environ.get('AURORA_HOST_SECRET')
        os.environ['AURORA_HOST_SECRET'] = 'must-not-enter-guest'
        try:
            process, detached, scope = self.start(100)
            out, err = process.communicate(timeout=8)
        finally:
            if previous is None:
                os.environ.pop('AURORA_HOST_SECRET', None)
            else:
                os.environ['AURORA_HOST_SECRET'] = previous
        self.assertEqual(0, process.returncode, err.decode())
        self.assertTrue(json.loads(out)['chrootWorkload'])
        self.assertEqual('guest-shell', (work / 'home/shell.txt').read_text())
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assert_host_unchanged(root, work)

    def test_missing_guest_loader_fails_and_cleans_scope(self):
        root, work = self.shell_fixture()
        # A dynamic shell without its PT_INTERP must fail rather than run a host shell.
        for path in root.rglob('*'):
            if path.is_file() and path.name.startswith('ld-linux'):
                path.unlink()
        state = work / 'state'
        result = subprocess.run([self.binary, '--probe', '--state-dir', str(state)] + self.mount_args,
                                capture_output=True, timeout=8)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'', result.stdout)
        self.assertEqual([], list(state.glob('*.state')))
        self.assertFalse((work / 'home/shell.txt').exists())
        self.assert_host_unchanged(root, work)

    def run_arguments(self, root, work, command, timeout=3000):
        state = work / 'run-state'
        return [self.binary, '--run', '--state-dir', str(state), '--generation', 'a' * 64,
                '--run-id', '12345678-1234-1234-1234-123456789abc', '--rootfs', str(root),
                '--workspace', str(work), '--timeout-ms', str(timeout), '--', '/bin/sh', '-c', command]

    def test_guest_command_streams_status_and_durable_completion(self):
        root, work = self.shell_fixture()
        with tempfile.TemporaryFile() as host_file:
            command = (f'test ! -e /proc/self/fd/{host_file.fileno()} || exit 92; '
                       'test -z "$AURORA_HOST_SECRET" || exit 93; '
                       'test "$HOME" = /root && test "$PWD" = /mnt || exit 94; '
                       'printf output; printf error >&2; printf artifact > /mnt/result; exit 23')
            args = self.run_arguments(root, work, command)
            result = subprocess.run(args, capture_output=True, timeout=8, pass_fds=(host_file.fileno(),),
                                    env={**os.environ, 'AURORA_HOST_SECRET': 'must-not-enter-guest'})
        self.assertEqual(23, result.returncode, result.stderr.decode())
        self.assertEqual(b'output', result.stdout)
        self.assertEqual(b'error', result.stderr)
        self.assertEqual('artifact', (work / 'project/result').read_text())
        state = work / 'run-state'
        identity = json.loads((state / 'run.json').read_text())
        self.assertEqual('a' * 64, identity['generation'])
        self.assertEqual({'protocol': 1, 'scopeRemoved': True, 'exitCode': 23, 'interrupted': False},
                         json.loads((state / 'completed.json').read_text()))
        self.assertEqual([], list(state.glob('*.state')))
        repeated = subprocess.run(args, capture_output=True, timeout=8)
        self.assertNotEqual(0, repeated.returncode)
        self.assertEqual(b'', repeated.stdout)
        self.assert_host_unchanged(root, work)

    def test_guest_timeout_kills_background_descendants_before_completion(self):
        root, work = self.shell_fixture()
        result = subprocess.run(self.run_arguments(root, work,
            'trap "" TERM; (while :; do :; done) & printf %s $! > /mnt/child; while :; do :; done', 1000),
            capture_output=True, timeout=8)
        self.assertNotEqual(0, result.returncode)
        state = work / 'run-state'
        completion = json.loads((state / 'completed.json').read_text())
        self.assert_gone(int((work / 'project/child').read_text()))
        self.assertTrue(completion['interrupted'])
        self.assertTrue(completion['scopeRemoved'])
        self.assertEqual([], list(state.glob('*.state')))
        self.assert_host_unchanged(root, work)

    def test_guest_exec_failure_is_not_reported_as_success(self):
        root, work = self.shell_fixture()
        args = self.run_arguments(root, work, 'unused')
        args[-3:] = ['/missing-executable']
        result = subprocess.run(args, capture_output=True, timeout=8)
        self.assertEqual(127, result.returncode)
        completion = json.loads((work / 'run-state/completed.json').read_text())
        self.assertEqual(127, completion['exitCode'])
        self.assertFalse(completion['interrupted'])
        self.assertTrue(completion['scopeRemoved'])
        self.assert_host_unchanged(root, work)

    def test_guest_both_supervisors_die_requires_recovery_without_completion(self):
        root, work = self.shell_fixture()
        args = self.run_arguments(root, work, 'printf ready > /mnt/ready; while :; do :; done', 5000)
        process = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.addCleanup(self.stop, process)
        state = work / 'run-state'
        self.addCleanup(self.recover_state, state)
        deadline = time.monotonic() + 3
        while not (work / 'project/ready').exists() and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertTrue((work / 'project/ready').exists())
        guard, = base.children(process.pid)
        worker, = base.children(guard)
        scope = base.group(worker)
        os.kill(guard, signal.SIGSTOP)
        process.kill()
        os.kill(guard, signal.SIGKILL)
        self.recover_state(state)
        process.communicate(timeout=8)
        self.assertFalse((state / 'completed.json').exists())
        self.assertEqual([], list(state.glob('*.state')))
        self.assert_gone(worker)
        self.assert_scope_removed(scope)
        self.assert_host_unchanged(root, work)

    def assert_host_unchanged(self, root, work):
        self.assertEqual('aurora-chroot-fixture-v1\n', (root / 'aurora-fixture').read_text())
        self.assertFalse((root / 'must-remain-readonly').exists())
        for name in ('root', 'tmp', 'mnt', 'proc', 'dev'):
            self.assertEqual([], list((root / name).iterdir()))
        self.assertEqual([], list((work / 'view').iterdir()))
        self.assertFalse(os.path.ismount(work / 'view'))

    def assert_outputs(self, work):
        self.assertEqual('home', (work / 'home/home.txt').read_text())
        self.assertEqual('project', (work / 'project/project.txt').read_text())
        self.assertEqual('temporary', (work / 'tmp/source').read_text())
        self.assertEqual(os.stat(work / 'tmp/source').st_ino, os.stat(work / 'tmp/hardlink').st_ino)

    def test_readonly_guest_root_writable_bindings_and_host_mounts(self):
        root, work = self.fixture()
        before_mount = os.stat('/proc/self/ns/mnt').st_ino
        before_owner = os.stat(root / 'aurora-fixture').st_uid
        process, detached, scope = self.start(300)
        mountinfo = Path(f'/proc/{detached}/mountinfo').read_text()
        roots = [line.split() for line in mountinfo.splitlines() if line.split()[4] == '/']
        self.assertEqual(1, len(roots))
        self.assertIn('ro', roots[0][5].split(','))
        out, err = process.communicate(timeout=8)
        self.assertEqual(0, process.returncode, err.decode())
        self.assertTrue(json.loads(out)['chrootWorkload'])
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assert_outputs(work)
        self.assert_host_unchanged(root, work)
        self.assertEqual(before_owner, os.stat(root / 'aurora-fixture').st_uid)
        self.assertEqual(before_mount, os.stat('/proc/self/ns/mnt').st_ino)

    def test_guard_sigkill_releases_mounted_descendants_without_success(self):
        root, work = self.fixture()
        process, detached, scope = self.start()
        os.kill(base.children(process.pid)[0], signal.SIGKILL)
        out, _ = process.communicate(timeout=8)
        self.assertNotEqual(0, process.returncode)
        self.assertEqual(b'', out)
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assert_outputs(work)
        self.assert_host_unchanged(root, work)

    def test_fresh_recovery_after_both_supervisors_die_preserves_outputs(self):
        root, work = self.fixture()
        process, detached, scope = self.start()
        guard, = base.children(process.pid)
        os.kill(guard, signal.SIGSTOP)
        os.kill(process.pid, signal.SIGKILL)
        os.kill(guard, signal.SIGKILL)
        process.communicate(timeout=8)
        self.addCleanup(self.recover_state, self.state)
        self.recover_state(self.state)
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assert_outputs(work)
        self.assert_host_unchanged(root, work)

    def test_guest_target_symlink_fails_without_following_or_host_mounts(self):
        root, work = self.fixture()
        (root / 'tmp').rmdir()
        (root / 'tmp').symlink_to(work / 'tmp')
        state = work / 'state'
        result = subprocess.run([self.binary, '--probe', '--state-dir', str(state)] + self.mount_args,
                                capture_output=True, timeout=8)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(b'', result.stdout)
        self.assertEqual([], list(state.glob('*.state')))
        self.assertEqual([], list((work / 'tmp').iterdir()))
        self.assertEqual([], list((work / 'view').iterdir()))
        self.assertTrue((root / 'tmp').is_symlink())

    def test_two_private_views_share_base_without_sharing_writable_homes(self):
        root, first = self.fixture()
        one, detached_one, scope_one = self.start()
        (first / 'home/home.txt').write_text('first-private-home')
        _, second = self.fixture(root)
        two, detached_two, scope_two = self.start()
        self.assert_outputs(second)
        self.assertEqual('first-private-home', (first / 'home/home.txt').read_text())
        one.kill()
        one.communicate(timeout=8)
        self.assert_gone(detached_one)
        self.assert_scope_removed(scope_one)
        self.assertTrue(Path(f'/proc/{detached_two}').exists())
        two.kill()
        two.communicate(timeout=8)
        self.assert_gone(detached_two)
        self.assert_scope_removed(scope_two)
        self.assert_host_unchanged(root, first)
        self.assert_host_unchanged(root, second)


if __name__ == '__main__':
    suite = unittest.TestSuite(MountTest(name) for name in MountTest.__dict__ if name.startswith('test_'))
    outcome = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(not outcome.wasSuccessful())
