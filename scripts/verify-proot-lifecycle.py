#!/usr/bin/env python3
"""Actual derived PRoot on Linux/glibc; does not qualify Android/Bionic."""
import ctypes
import importlib.util
import os
from pathlib import Path
import select
import signal
import subprocess
import tempfile
import time
import unittest

SOURCE = r'''
#define _GNU_SOURCE
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
static void record(const char *path) {
    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0600);
    if (fd < 0 || dprintf(fd, "%d\n", getpid()) < 0) _exit(20);
    close(fd);
}
int main(int argc, char **argv) {
    if (argc != 3) return 2;
    record(argv[1]);
    pid_t child = fork();
    if (child < 0) return 3;
    if (!child) {
        if (setsid() < 0) _exit(4);
        record(argv[1]);
        child = fork();
        if (child < 0) _exit(5);
        if (!child) {
            if (setsid() < 0) _exit(6);
            record(argv[1]);
        }
        for (;;) pause();
    }
    while (access(argv[2], F_OK) != 0) usleep(10000);
    return 37;
}
'''


class ProotLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0) != 0:
            raise OSError(ctypes.get_errno(), 'PR_SET_CHILD_SUBREAPER')
        cls.directory = tempfile.TemporaryDirectory(prefix='aurora-proot-lifecycle-')
        root = Path(cls.directory.name)
        spec = importlib.util.spec_from_file_location('build_host_proot', Path(__file__).with_name('build-host-proot.py'))
        builder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(builder)
        cls.proot = builder.build(root / 'build')
        source = root / 'workload.c'
        source.write_text(SOURCE)
        cls.workload = source.with_suffix('')
        subprocess.run(['cc', '-O2', '-Wall', '-Wextra', '-Werror', str(source),
                        '-o', str(cls.workload)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def check_lifecycle(self, no_seccomp, kill):
        with tempfile.TemporaryDirectory(dir=self.directory.name) as directory:
            root = Path(directory)
            record, gate = root / 'pids', root / 'exit'
            env = {**os.environ, 'PROOT_TMP_DIR': directory}
            env.pop('PROOT_NO_SECCOMP', None)
            if no_seccomp:
                env['PROOT_NO_SECCOMP'] = '1'
            log = (root / 'tracer.log').open('wb')
            process = subprocess.Popen([str(self.proot), '--kill-on-exit', '-r', '/', '-w', directory,
                                        str(self.workload), str(record), str(gate)],
                                       env=env, stdout=log, stderr=subprocess.STDOUT)
            handles = {}
            try:
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    if record.exists():
                        for value in record.read_text().splitlines():
                            pid = int(value)
                            if pid not in handles:
                                handles[pid] = os.pidfd_open(pid)
                    if len(handles) == 3 or process.poll() is not None:
                        break
                    time.sleep(.01)
                self.assertIsNone(process.poll(), (root / 'tracer.log').read_text())
                self.assertEqual(len(handles), 3, 'All generations must execute')
                if kill:
                    process.kill()
                else:
                    gate.touch()
                self.assertEqual(process.wait(timeout=10), -signal.SIGKILL if kill else 37,
                                 (root / 'tracer.log').read_text())
                for pid, fd in handles.items():
                    poll = select.poll()
                    poll.register(fd, select.POLLIN)
                    self.assertTrue(poll.poll(5000), f'Guest PID {pid} survived tracer exit')
            finally:
                if process.poll() is None:
                    process.kill()
                process.wait(timeout=10)
                for pid, fd in handles.items():
                    try:
                        signal.pidfd_send_signal(fd, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    poll = select.poll()
                    poll.register(fd, select.POLLIN)
                    poll.poll(5000)
                    try:
                        os.waitpid(pid, os.WNOHANG)
                    except ChildProcessError:
                        pass
                    os.close(fd)
                log.close()

    def test_killed_tracer_with_seccomp(self):
        self.check_lifecycle(False, True)

    def test_killed_tracer_without_seccomp(self):
        self.check_lifecycle(True, True)

    def test_guest_exit_stops_detached_descendants(self):
        self.check_lifecycle(False, False)

    def test_guest_exit_without_seccomp_stops_detached_descendants(self):
        self.check_lifecycle(True, False)


if __name__ == '__main__':
    unittest.main(verbosity=2)
