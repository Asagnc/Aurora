#!/usr/bin/env python3
"""Real cgroup v2/pidfd host tests. Root and a writable current cgroup are required.
These tests do not qualify Android SELinux or chroot execution.
"""
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


def group(pid):
    return next(line[3:] for line in Path(f'/proc/{pid}/cgroup').read_text().splitlines() if line.startswith('0::'))


class ScopeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.geteuid() != 0:
            raise RuntimeError('Run this explicit host Root suite with sudo')
        cls.directory = tempfile.TemporaryDirectory(prefix='aurora-task-scope-')
        Path(cls.directory.name).chmod(0o755)
        cls.binary = str(Path(cls.directory.name) / 'scope')
        source = Path(__file__).resolve().parents[1] / 'app/src/main/cpp/linux'
        subprocess.run(['cc', '-std=c11', '-O2', '-DNDEBUG', '-Wall', '-Wextra', '-Werror',
                        str(source / 'scope_probe.c'), str(source / 'task_scope.c'), str(source / 'scope_identity.c'),
                        str(source / 'scope_state.c'), str(source / 'scope_process.c'), '-o', cls.binary], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def start(self, hold=5000):
        self.state = Path(self.directory.name) / ('state-' + str(time.monotonic_ns()))
        process = subprocess.Popen([self.binary, '--probe', '--state-dir', str(self.state), '--hold-ms', str(hold)],
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
        self.addCleanup(self.stop, process)
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            for pid in descendants(process.pid):
                try:
                    path = group(pid)
                    if os.getsid(pid) == pid and Path(path).name.startswith('aurora-linux-'):
                        return process, pid, path
                except (ProcessLookupError, FileNotFoundError):
                    pass
            if process.poll() is not None:
                out, err = process.communicate()
                self.fail(f'cgroup workload did not start: {process.returncode}, {out!r}, {err!r}')
            time.sleep(0.01)
        self.fail('Detached task-scope worker did not appear')

    @staticmethod
    def stop(process):
        if process.poll() is None:
            process.kill()
        process.communicate(timeout=8)

    def assert_gone(self, pid):
        deadline = time.monotonic() + 5
        while Path(f'/proc/{pid}').exists() and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertFalse(Path(f'/proc/{pid}').exists(), f'Remaining task worker: {pid}')

    def assert_scope_removed(self, group_path):
        # Verify via the cgroup2 mount; handle non-root mount roots in CI.
        candidates = []
        for line in Path('/proc/self/mountinfo').read_text().splitlines():
            if ' - cgroup2 ' not in line:
                continue
            fields = line.split()
            root, mount = fields[3:5]
            if root == '/':
                candidates.append(Path(mount) / group_path.lstrip('/'))
            elif group_path.startswith(root + '/'):
                candidates.append(Path(mount) / group_path[len(root) + 1:])
        self.assertTrue(candidates)
        self.assertTrue(all(not path.exists() for path in candidates), f'Remaining task scope: {candidates}')

    def test_normal_exit_kills_detached_worker_reaps_and_removes_scope(self):
        before_group = group(os.getpid())
        before_mount = os.stat('/proc/self/ns/mnt').st_ino
        process, detached, scope = self.start(300)
        self.assertNotEqual(before_mount, os.stat(f'/proc/{detached}/ns/mnt').st_ino)
        self.assertNotIn('shared:', Path(f'/proc/{detached}/mountinfo').read_text())
        out, err = process.communicate(timeout=8)
        self.assertEqual(0, process.returncode, err.decode())
        observation = json.loads(out)
        for field in ('taskCgroup', 'pidfdObserved', 'subreaperDrained', 'populatedThenEmpty', 'scopeRemoved'):
            self.assertTrue(observation[field])
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assertEqual(before_group, group(os.getpid()))
        self.assertEqual(before_mount, os.stat('/proc/self/ns/mnt').st_ino)

    def test_coordinator_term_and_kill_trigger_guard_cleanup(self):
        for signum in (signal.SIGTERM, signal.SIGKILL):
            with self.subTest(signum=signum):
                process, detached, scope = self.start()
                process.send_signal(signum)
                out, _ = process.communicate(timeout=8)
                self.assertEqual(-signum, process.returncode)
                self.assertEqual(b'', out)
                self.assert_gone(detached)
                self.assert_scope_removed(scope)

    def test_process_group_kill_leaves_guard_alive_to_clean_only_task(self):
        sentinel = subprocess.Popen(['sleep', '30'])
        self.addCleanup(self.stop, sentinel)
        process, detached, scope = self.start()
        os.killpg(process.pid, signal.SIGKILL)
        out, _ = process.communicate(timeout=8)
        self.assertEqual(b'', out)
        self.assert_gone(detached)
        self.assert_scope_removed(scope)
        self.assertIsNone(sentinel.poll())

    def test_workload_sigkill_reaps_descendants_without_success_reply(self):
        process, detached, scope = self.start()
        worker = int(Path(f'/proc/{detached}/status').read_text().split('PPid:')[1].split()[0])
        os.kill(worker, signal.SIGKILL)
        out, _ = process.communicate(timeout=8)
        self.assertNotEqual(0, process.returncode)
        self.assertEqual(b'', out)
        self.assert_gone(detached)
        self.assert_scope_removed(scope)

    def test_repeated_probes_remove_independent_scopes(self):
        names = set()
        for _ in range(3):
            process, detached, scope = self.start(200)
            out, err = process.communicate(timeout=8)
            self.assertEqual(0, process.returncode, err.decode())
            self.assertTrue(json.loads(out)['scopeRemoved'])
            self.assertNotIn(scope, names)
            names.add(scope)
            self.assert_gone(detached)
            self.assert_scope_removed(scope)

    def test_ordinary_uid_and_arbitrary_scope_arguments_are_rejected(self):
        def ordinary():
            os.setgid(65534)
            os.setuid(65534)
        result = subprocess.run([self.binary, '--probe', '--state-dir', str(Path(self.directory.name) / 'denied')], preexec_fn=ordinary, capture_output=True, timeout=5)
        self.assertEqual(126, result.returncode)
        self.assertEqual('root_required', json.loads(result.stderr)['operation'])
        for args in [[], ['--unknown'], ['--probe', '--hold-ms', '9000'], ['--probe', '/sys/fs/cgroup/system']]:
            result = subprocess.run([self.binary] + args, capture_output=True, timeout=5)
            self.assertEqual(64, result.returncode)


if __name__ == '__main__':
    unittest.main(verbosity=2)
