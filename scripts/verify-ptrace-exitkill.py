#!/usr/bin/env python3
"""Linux kernel prerequisite only, not full PRoot or Android qualification."""
import ctypes
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
import unittest


SOURCE = r'''
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <unistd.h>

static void record(const char *path) {
    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND, 0600);
    if (fd < 0 || dprintf(fd, "%d\n", getpid()) < 0) _exit(20);
    close(fd);
}

static void workload(const char *path) {
    record(path);
    pid_t child = fork();
    if (child < 0) _exit(21);
    if (!child) {
        if (setsid() < 0) _exit(22);
        record(path);
        child = fork();
        if (child < 0) _exit(23);
        if (!child) {
            if (setsid() < 0) _exit(24);
            record(path);
        }
    }
    for (;;) pause();
}

static int startup(const char *path, int early) {
    const pid_t parent = getpid();
    pid_t child = fork();
    if (child < 0) return 10;
    if (!child) {
        if (early) raise(SIGSTOP);
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) < 0 || getppid() != parent) _exit(31);
        raise(SIGSTOP);
        for (;;) pause();
    }
    int status;
    if (waitpid(child, &status, WUNTRACED) != child || !WIFSTOPPED(status)) return 11;
    int fd = open(path, O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fd < 0 || dprintf(fd, "%d\n", child) < 0) return 12;
    close(fd);
    for (;;) pause();
}

int main(int argc, char **argv) {
    if (argc == 4) return startup(argv[1], atoi(argv[3]));
    if (argc != 3) return 2;
    pid_t leader = fork();
    if (leader < 0) return 3;
    if (!leader) {
        if (ptrace(PTRACE_TRACEME, 0, 0, 0) < 0) _exit(4);
        raise(SIGSTOP);
        workload(argv[1]);
    }
    int status;
    if (waitpid(leader, &status, 0) != leader || !WIFSTOPPED(status)) return 5;
    unsigned long options = PTRACE_O_EXITKILL | PTRACE_O_TRACEFORK |
        PTRACE_O_TRACEVFORK | PTRACE_O_TRACECLONE | PTRACE_O_TRACEEXIT;
    if (atoi(argv[2])) options |= PTRACE_O_TRACESECCOMP;
    if (ptrace(PTRACE_SETOPTIONS, leader, 0, options) < 0) {
        kill(leader, SIGKILL);
        waitpid(leader, 0, 0);
        return 6;
    }
    if (ptrace(PTRACE_CONT, leader, 0, 0) < 0) return 7;
    for (;;) {
        pid_t pid = waitpid(-1, &status, __WALL);
        if (pid < 0) return errno == ECHILD ? 0 : 8;
        if (WIFSTOPPED(status) && ptrace(PTRACE_CONT, pid, 0, 0) < 0) return 9;
    }
}
'''


class ExitKillTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if os.name != 'posix' or not Path('/proc/self/status').exists():
            raise RuntimeError('This explicit qualification requires Linux')
        # Adopt only our orphaned descendants, so every fixture can be reaped.
        if ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0) != 0:
            raise OSError(ctypes.get_errno(), 'PR_SET_CHILD_SUBREAPER')
        cls.directory = tempfile.TemporaryDirectory(prefix='aurora-exitkill-')
        source = Path(cls.directory.name) / 'tracer.c'
        source.write_text(SOURCE)
        cls.binary = str(source.with_suffix(''))
        subprocess.run(['cc', '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
                        str(source), '-o', cls.binary], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def check_death(self, seccomp, death_signal):
        record = Path(self.directory.name) / f'pids-{seccomp}-{death_signal}'
        tracer = subprocess.Popen([self.binary, str(record), str(seccomp)])
        pids = []
        try:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                if record.exists():
                    pids = [int(pid) for pid in record.read_text().splitlines()]
                if len(pids) == 3 or tracer.poll() is not None:
                    break
                time.sleep(.01)
            self.assertIsNone(tracer.poll(), 'Tracer failed before workload readiness')
            self.assertEqual(len(pids), 3, 'All generations must execute before death')
            self.assertEqual(os.getsid(pids[1]), pids[1])
            self.assertEqual(os.getsid(pids[2]), pids[2])
            tracer.send_signal(death_signal)
            self.assertEqual(tracer.wait(timeout=5), -death_signal)
            remaining = set(pids)
            deadline = time.monotonic() + 5
            while remaining and time.monotonic() < deadline:
                for pid in list(remaining):
                    try:
                        waited, status = os.waitpid(pid, os.WNOHANG)
                    except ChildProcessError:
                        # A grandchild becomes ours only once its parent exits.
                        continue
                    if waited:
                        self.assertTrue(os.WIFSIGNALED(status))
                        self.assertEqual(os.WTERMSIG(status), signal.SIGKILL)
                        remaining.remove(pid)
                time.sleep(.01)
            self.assertFalse(remaining, f'Tracees survived tracer death: {remaining}')
        finally:
            if tracer.poll() is None:
                tracer.kill()
            tracer.wait(timeout=5)
            if record.exists():
                pids = [int(pid) for pid in record.read_text().splitlines()]
            for pid in pids:
                try:
                    waited, _ = os.waitpid(pid, os.WNOHANG)
                    if not waited:
                        os.kill(pid, signal.SIGKILL)
                        os.waitpid(pid, 0)
                except ChildProcessError:
                    pass

    def test_sigkill_with_seccomp_options(self):
        self.check_death(1, signal.SIGKILL)

    def test_sigkill_without_seccomp_options(self):
        self.check_death(0, signal.SIGKILL)

    def test_sigterm_without_signal_handler(self):
        self.check_death(0, signal.SIGTERM)

    def check_startup(self, early):
        record = Path(self.directory.name) / f'startup-{early}'
        tracer = subprocess.Popen([self.binary, str(record), '0', str(int(early))])
        child = None
        try:
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                if record.exists() and record.read_text().endswith('\n'):
                    child = int(record.read_text())
                    break
                self.assertIsNone(tracer.poll())
                time.sleep(.01)
            self.assertIsNotNone(child, 'Startup barrier not reached')
            tracer.kill()
            tracer.wait(timeout=5)
            if early:
                os.kill(child, signal.SIGCONT)
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                waited, status = os.waitpid(child, os.WNOHANG)
                if waited:
                    child = None
                    if early:
                        self.assertTrue(os.WIFEXITED(status))
                        self.assertEqual(os.WEXITSTATUS(status), 31)
                    else:
                        self.assertTrue(os.WIFSIGNALED(status))
                        self.assertEqual(os.WTERMSIG(status), signal.SIGKILL)
                    return
                time.sleep(.01)
            self.fail('Startup child survived parent death')
        finally:
            if tracer.poll() is None:
                tracer.kill()
            tracer.wait(timeout=5)
            if child is None and record.exists():
                child = int(record.read_text())
            if child is not None:
                try:
                    waited, _ = os.waitpid(child, os.WNOHANG)
                    if not waited:
                        os.kill(child, signal.SIGKILL)
                        os.waitpid(child, 0)
                except ChildProcessError:
                    pass

    def test_parent_dies_before_startup_guard(self):
        self.check_startup(True)

    def test_parent_dies_after_startup_guard_before_ptrace_options(self):
        self.check_startup(False)


if __name__ == '__main__':
    unittest.main(verbosity=2)
