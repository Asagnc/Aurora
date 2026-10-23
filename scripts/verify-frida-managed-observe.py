#!/usr/bin/env python3
"""Real pidfd/deadline/control tests with owned native children, no injection."""
import errno
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HARNESS = r'''
#define _GNU_SOURCE
#include "managed_observe.h"
#include "input.h"
#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
static int64_t now_ms(void) {
    struct timespec value; clock_gettime(CLOCK_MONOTONIC, &value);
    return (int64_t)value.tv_sec * 1000 + value.tv_nsec / 1000000;
}
static int report(int fd, int cancelled) {
    struct aurora_managed_control control = {.fd = fd}; memset(control.ticket, 17, 32);
    struct aurora_managed_facts facts = {.flags = cancelled ? 63 : 127};
    return aurora_managed_report(&control, &facts, cancelled ? 1 : 0);
}
static void child(int fd, const char *mode) {
    if (!strcmp(mode, "ignore") || !strncmp(mode, "owner-ignore", 12)) for (;;) pause();
    if (!strcmp(mode, "silent")) { close(fd); _exit(0); }
    if (!strcmp(mode, "truncated")) { write(fd, "AEX1short", 9); close(fd); _exit(0); }
    if (!strcmp(mode, "valid-live")) {
        if (report(fd, 0)) _exit(80);
        shutdown(fd, SHUT_WR);
        for (;;) pause();
    }
    if (!strcmp(mode, "bad")) {
        char bytes[53] = {0}; if (write(fd, bytes, sizeof(bytes)) != (ssize_t)sizeof(bytes)) _exit(80);
    }
    int cancelled = 0;
    if (strcmp(mode, "normal")) {
        unsigned char command = 0;
        if (read(fd, &command, 1) != 1 || (command != 1 && command != 2)) _exit(81);
        cancelled = command == 2;
        if (!strcmp(mode, "owner") && !cancelled) _exit(82);
    }
    if (report(fd, cancelled)) _exit(83);
    close(fd); _exit(0);
}
int main(int argc, char **argv) {
    if (argc != 2) return 64;
    signal(SIGPIPE, SIG_IGN);
    pid_t parent = getpid();
    pid_t owner = fork();
    if (owner < 0) return 80;
    if (!owner) {
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != parent) _exit(80);
        if (!strncmp(argv[1], "owner", 5)) _exit(0);
        for (;;) pause();
    }
    int owner_fd = (int)syscall(SYS_pidfd_open, owner, 0), pair[2];
    if (owner_fd < 0 || socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, pair)) return 80;
    pid_t worker = fork();
    if (worker < 0) return 80;
    if (!worker) {
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != parent) _exit(80);
        close(owner_fd); close(pair[0]); child(pair[1], argv[1]);
    }
    close(pair[1]);
    struct aurora_managed_observer observer = {.socket = pair[0], .owner_pidfd = owner_fd,
        .worker_pidfd = (int)syscall(SYS_pidfd_open, worker, 0), .worker = {.pid = (uint32_t)worker}};
    if (observer.worker_pidfd < 0 || frida_process_start(worker, &observer.worker.ticks)) return 80;
    memset(observer.ticket, 17, 32);
    int64_t start = now_ms();
    int bounded = !strcmp(argv[1], "duration") || !strcmp(argv[1], "ignore") || !strcmp(argv[1], "valid-live");
    observer.operation_deadline_ms = start + (bounded ? 100 : 2000);
    observer.force_deadline_ms = observer.operation_deadline_ms + 500;
    if (!strcmp(argv[1], "owner-ignore-long")) {
        observer.operation_deadline_ms = start + 30000;
        observer.force_deadline_ms = start + 50000;
    }
    if (!strcmp(argv[1], "invalid")) observer.force_deadline_ms = start;
    if (!strcmp(argv[1], "owner-no-go")) {
        observer.suppress_commands = 1;
        observer.operation_deadline_ms = observer.force_deadline_ms = start + 500;
    }
    if (!strcmp(argv[1], "bad-fd")) { close(observer.owner_pidfd); observer.owner_pidfd = 999999; }
    struct aurora_managed_observation observation;
    int result = aurora_managed_observe(&observer, &observation), error = errno;
    int64_t elapsed = now_ms() - start;
    /* Fixture owns both children. Force teardown even on assertion failure;
     * this is not production cgroup cleanup or a target-App operation. */
    kill(worker, SIGKILL); kill(owner, SIGKILL);
    int status = 0; while (waitpid(worker, &status, 0) < 0 && errno == EINTR) {}
    int worker_status = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
    while (waitpid(owner, &status, 0) < 0 && errno == EINTR) {}
    close(observer.worker_pidfd); close(owner_fd); close(pair[0]);
    printf("{\"result\":%d,\"error\":%d,\"reason\":%d,\"frame\":%d,\"flags\":%u,\"signalled\":%d,\"workerStatus\":%d,\"elapsed\":%lld}\n",
        result, error, observation.stop, observation.receiver.state, observation.receiver.receipt.flags,
        observation.worker_signalled, worker_status, (long long)elapsed);
    return 0;
}
'''


@unittest.skipUnless(sys.platform.startswith("linux") and shutil.which("cc"), "Linux C compiler required")
class ObserveTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="aurora-managed-observe-")
        cls.root = Path(cls.temporary.name)
        source = cls.root / "harness.c"; source.write_text(HARNESS, encoding="utf-8")
        cls.binary = cls.root / "harness"
        native = ROOT / "app/src/main/cpp/frida"
        subprocess.run(["cc", "-Wall", "-Wextra", "-Werror", "-I" + str(native), str(source),
            *[str(native / name) for name in ["input.c", "managed_control.c", "managed_protocol.c",
                "managed_receive.c", "managed_observe.c"]], "-o", str(cls.binary)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def run_mode(self, mode, timeout=5):
        result = subprocess.run([str(self.binary), mode], capture_output=True, text=True, timeout=timeout, check=True)
        return json.loads(result.stdout)

    def test_normal_report_and_worker_pidfd_are_independent_facts(self):
        result = self.run_mode("normal")
        self.assertEqual(0, result["result"]); self.assertEqual(1, result["signalled"])
        self.assertEqual(1, result["frame"]); self.assertEqual(127, result["flags"])
        self.assertEqual(0, result["workerStatus"])

    def test_operation_deadline_requests_duration_and_allows_cooperative_completion(self):
        result = self.run_mode("duration")
        self.assertEqual(0, result["result"]); self.assertEqual(1, result["reason"])
        self.assertEqual(127, result["flags"]); self.assertEqual(0, result["workerStatus"])
        self.assertGreaterEqual(result["elapsed"], 90)

    def test_owner_pidfd_requests_cancel_without_waiting_for_operation_deadline(self):
        result = self.run_mode("owner")
        self.assertEqual(0, result["result"]); self.assertEqual(2, result["reason"])
        self.assertEqual(63, result["flags"]); self.assertEqual(0, result["workerStatus"])

    def test_unreleased_stub_never_receives_owner_stop_command(self):
        result = self.run_mode("owner-no-go")
        self.assertEqual(-1, result["result"]); self.assertEqual(errno.ETIMEDOUT, result["error"])
        self.assertEqual((2, 0, 137), (result["reason"], result["frame"], result["workerStatus"]))

    def test_protocol_violation_requests_cancel_but_never_repairs_report(self):
        result = self.run_mode("bad")
        self.assertEqual(0, result["result"]); self.assertEqual(3, result["reason"])
        self.assertEqual(-1, result["frame"]); self.assertEqual(0, result["flags"])
        self.assertEqual(0, result["workerStatus"])

    def test_worker_exit_without_report_cannot_prove_cleanup(self):
        for mode in ["silent", "truncated"]:
            with self.subTest(mode=mode):
                result = self.run_mode(mode)
                self.assertEqual(0, result["result"]); self.assertEqual(1, result["signalled"])
                self.assertEqual(-1, result["frame"]); self.assertEqual(0, result["flags"])

    def test_unresponsive_worker_hits_force_deadline_and_requires_forced_cleanup(self):
        result = self.run_mode("ignore")
        self.assertEqual(-1, result["result"]); self.assertEqual(errno.ETIMEDOUT, result["error"])
        self.assertEqual(0, result["signalled"]); self.assertEqual(0, result["flags"])
        self.assertGreaterEqual(result["elapsed"], 590); self.assertLess(result["elapsed"], 2500)

    def test_complete_report_does_not_substitute_for_worker_exit(self):
        result = self.run_mode("valid-live")
        self.assertEqual(-1, result["result"]); self.assertEqual(errno.ETIMEDOUT, result["error"])
        self.assertEqual(1, result["frame"]); self.assertEqual(127, result["flags"])
        self.assertEqual(0, result["signalled"])

    def test_owner_loss_with_unresponsive_worker_still_requires_forced_cleanup(self):
        result = self.run_mode("owner-ignore")
        self.assertEqual(-1, result["result"]); self.assertEqual(errno.ETIMEDOUT, result["error"])
        self.assertEqual(2, result["reason"]); self.assertEqual(0, result["signalled"])
        self.assertEqual(0, result["flags"])

    def test_early_cancel_limits_cooperative_window_to_fifteen_seconds(self):
        result = self.run_mode("owner-ignore-long", timeout=22)
        self.assertEqual(-1, result["result"]); self.assertEqual(errno.ETIMEDOUT, result["error"])
        self.assertEqual(2, result["reason"]); self.assertEqual(0, result["signalled"])
        self.assertGreaterEqual(result["elapsed"], 14900); self.assertLess(result["elapsed"], 21000)

    def test_invalid_budget_and_invalid_pidfd_are_rejected(self):
        for mode, error in [("invalid", errno.EINVAL), ("bad-fd", errno.EBADF)]:
            with self.subTest(mode=mode):
                result = self.run_mode(mode)
                self.assertEqual(-1, result["result"]); self.assertEqual(error, result["error"])
                self.assertEqual(0, result["signalled"])


if __name__ == "__main__":
    unittest.main()
