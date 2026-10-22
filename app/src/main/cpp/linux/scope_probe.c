#define _GNU_SOURCE
#include "task_scope.h"
#include "scope_state.h"
#include "mount_probe.h"
#include "guest_run.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

struct fixture { const char *rootfs, *workspace; int shell; struct guest_run run; };

static int failure(const char *operation) {
    int saved = errno;
    fprintf(stderr, "{\"error\":\"root_scope_unavailable\",\"operation\":\"%s\",\"errno\":%d}\n", operation, saved);
    return 126;
}

static int wait_child(pid_t child) {
    int status;
    while (waitpid(child, &status, 0) < 0) { if (errno != EINTR) return 125; }
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}

static int read_ready(int pipe, int timeout_ms) {
    struct pollfd event = {.fd = pipe, .events = POLLIN};
    char byte = 0;
    int status;
    do { status = poll(&event, 1, timeout_ms); } while (status < 0 && errno == EINTR);
    if (status == 1 && read(pipe, &byte, 1) == 1 && byte == 'r') return 0;
    errno = EPIPE; return -1;
}

/* Qualification deliberately detaches TERM-ignoring descendants. Run mode
 * instead execs the supplied guest argv after the same admission/mount setup. */
static int workload(const struct task_scope *scope, int report, int hold_ms, const struct fixture *fixture) {
    if (task_scope_enter(scope) < 0) return failure("scope_enter");
    close(scope->processes); close(scope->directory); close(scope->parent);
    close(scope->events); close(scope->kill);
    struct stat before, after;
    if (stat("/proc/self/ns/mnt", &before) < 0 || unshare(CLONE_NEWNS) < 0 ||
        mount(NULL, "/", NULL, MS_REC | MS_PRIVATE, NULL) < 0 || stat("/proc/self/ns/mnt", &after) < 0)
        return failure("private_mount_namespace");
    if (before.st_ino == after.st_ino) { errno = EINVAL; return failure("unchanged_mount_namespace"); }
    if (fixture->run.argv) {
        if (scope_chroot_enter(fixture->run.rootfs, fixture->run.workspace) < 0) return failure("guest_mount");
        if (write(report, "r", 1) != 1) return failure("guest_ready");
        close(report);
        return guest_run_exec(&fixture->run);
    }
    if (fixture->rootfs && scope_chroot_fixture(fixture->rootfs, fixture->workspace) < 0) return failure("chroot_fixture");
    if (fixture->shell && scope_chroot_shell_fixture() < 0) return failure("chroot_shell_fixture");
    int ready[2];
    if (pipe2(ready, O_CLOEXEC) < 0) return failure("detached_pipe");
    pid_t child = fork();
    if (child < 0) { close(ready[0]); close(ready[1]); return failure("detached_fork"); }
    if (child == 0) {
        close(ready[0]); close(report);
        if (setsid() < 0) _exit(125);
        signal(SIGTERM, SIG_IGN);
        if (write(ready[1], "r", 1) != 1) _exit(125);
        close(ready[1]); close(STDIN_FILENO); close(STDOUT_FILENO); close(STDERR_FILENO);
        for (;;) pause();
    }
    close(ready[1]);
    int started = read_ready(ready[0], 2000); close(ready[0]);
    if (started < 0 || write(report, "r", 1) != 1) return failure("detached_readiness");
    close(report);
    if (hold_ms) {
        struct pollfd unused = {.fd = -1};
        if (poll(&unused, 0, hold_ms) < 0) return failure("workload_delay");
    }
    return 0;
}

static int observe(int owner, int worker, int report, int timeout_ms) {
    long long deadline = task_scope_now() + timeout_ms;
    int ready = 0;
    for (;;) {
        struct pollfd events[] = {{.fd = owner, .events = POLLIN}, {.fd = worker, .events = POLLIN},
            {.fd = ready ? -1 : report, .events = POLLIN}};
        long long left = deadline - task_scope_now();
        if (left <= 0) { errno = ETIMEDOUT; return -1; }
        int result = poll(events, 3, (int)left);
        if (result < 0) { if (errno == EINTR) continue; return -1; }
        if (events[0].revents) { errno = EPIPE; return -1; }
        if (events[2].revents && !ready) {
            char byte;
            if (read(report, &byte, 1) != 1 || byte != 'r') { errno = EPIPE; return -1; }
            ready = 1;
        }
        if (events[1].revents) {
            if (ready) return 0;
            /* The report pipe can become readable in the same scheduling turn. */
            return read_ready(report, 0);
        }
    }
}

static int cleanup(struct task_scope *scope, pid_t worker, int *exit_code) {
    /* Read before kill to arm kernfs notifications. Qualification requires an
     * actual populated->empty notification, not just readable control files. */
    int populated = task_scope_populated(scope);
    if (populated < 0 || task_scope_kill(scope) < 0) return failure("scope_kill");
    if (populated) {
        struct pollfd event = {.fd = scope->events, .events = POLLPRI | POLLERR};
        int changed;
        do { changed = poll(&event, 1, 5000); } while (changed < 0 && errno == EINTR);
        if (changed != 1 || !(event.revents & (POLLPRI | POLLERR))) {
            errno = ETIMEDOUT; return failure("scope_empty_notification");
        }
    }
    if (task_scope_wait_empty(scope, 5000) < 0) return failure("scope_empty");
    int status;
    for (;;) {
        pid_t reaped = waitpid(-1, &status, 0);
        if (reaped < 0) { if (errno == EINTR) continue; if (errno == ECHILD) break; return failure("scope_reap"); }
        if (reaped == worker) *exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
    }
    if (task_scope_remove(scope) < 0) return failure("scope_remove");
    return 0;
}

static int guard(int owner, int hold_ms, int state, int run_lock, const struct fixture *fixture) {
    /* A separate session survives the command supervisor's group cancellation.
     * This guard ends after this one bounded job; it is not a daemon. */
    if (setsid() < 0 || prctl(PR_SET_CHILD_SUBREAPER, 1) < 0) return failure("guard_setup");
    struct pollfd alive = {.fd = owner, .events = POLLIN};
    if (poll(&alive, 1, 0) != 0) { errno = EPIPE; return failure("owner_already_exited"); }
    struct task_scope scope;
    if (task_scope_create(&scope) < 0) return failure("scope_create");
    if (scope_state_save(state, &scope) < 0) { int saved = errno; task_scope_remove(&scope); errno = saved; return failure("scope_record"); }
    int report[2];
    if (pipe2(report, O_CLOEXEC) < 0) {
        int saved = errno;
        if (task_scope_remove(&scope) == 0) scope_state_forget(state, scope.name);
        errno = saved; return failure("report_pipe");
    }
    pid_t guard_pid = getpid();
    pid_t worker = fork();
    if (worker < 0) { close(report[0]); close(report[1]); task_scope_remove(&scope); return failure("worker_fork"); }
    if (worker == 0) {
        close(report[0]); close(owner); close(state); close(run_lock);
        /* Close the pre-admission fork race if the guard dies before this
         * process can join its recorded group. Descendants remain group-owned. */
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) < 0 || getppid() != guard_pid) _exit(125);
        _exit(workload(&scope, report[1], hold_ms, fixture));
    }
    close(report[1]);
    int worker_fd = (int)syscall(SYS_pidfd_open, worker, 0);
    int observed = worker_fd < 0 ? -1 : observe(owner, worker_fd, report[0], fixture->run.argv ? fixture->run.timeout_ms : 10000);
    int saved = errno;
    close(report[0]); if (worker_fd >= 0) close(worker_fd);
    int populated = task_scope_populated(&scope);
    /* Also handles a worker stopped before admission: this unreaped child PID
     * cannot be recycled, and only it is signalled outside the scope. */
    if (observed < 0) kill(worker, SIGKILL);
    int exit_code = -1;
    int cleaned = cleanup(&scope, worker, &exit_code);
    if (cleaned) {
        fprintf(stderr, "{\"unconfirmedTaskScope\":\"%s\"}\n", scope.name);
        return cleaned;
    }
    if (scope_state_forget(state, scope.name) < 0) return failure("scope_record_finish");
    if (fixture->run.argv) {
        int interrupted = observed < 0 || poll(&alive, 1, 0) != 0;
        if (guest_run_finish(state, exit_code, interrupted) < 0) return failure("guest_completion");
        if (interrupted) { errno = saved; return failure("guest_interrupted"); }
        return exit_code;
    }
    if (observed < 0) { errno = saved; return failure("owner_or_worker_observation"); }
    if (exit_code || populated != 1) { errno = EINVAL; return failure("workload_or_population"); }
    if (poll(&alive, 1, 0) != 0) { errno = EPIPE; return failure("owner_exited"); }
    printf("{\"protocol\":1,\"privateMountNamespace\":true,\"taskCgroup\":true,\"pidfdObserved\":true,\"subreaperDrained\":true,\"populatedThenEmpty\":true,\"scopeRemoved\":true,\"chrootWorkload\":%s}\n",
        fixture->rootfs ? "true" : "false");
    return 0;
}

int main(int argc, char **argv) {
    int hold_ms = 0;
    if (argc < 4 || (strcmp(argv[1], "--probe") && strcmp(argv[1], "--recover") && strcmp(argv[1], "--run") && strcmp(argv[1], "--run-pty")) ||
        strcmp(argv[2], "--state-dir")) return 64;
    int recovering = !strcmp(argv[1], "--recover");
    struct fixture fixture = {0};
    int next = 4;
    int running = !strcmp(argv[1], "--run") || !strcmp(argv[1], "--run-pty");
    if (running) {
        if (guest_run_parse(argc, argv, &fixture.run) < 0) return 64;
        next = argc;
    }
    if (!recovering && !running && argc >= 7 && (!strcmp(argv[4], "--chroot-fixture") || !strcmp(argv[4], "--chroot-shell-fixture"))) {
        fixture.shell = !strcmp(argv[4], "--chroot-shell-fixture");
        fixture.rootfs = argv[5]; fixture.workspace = argv[6]; next = 7;
    }
    if (argc != next) {
        if (recovering || argc != next + 2 || strcmp(argv[next], "--hold-ms")) return 64;
        char *end;
        long value = strtol(argv[next + 1], &end, 10);
        if (!*argv[next + 1] || *end || value < 1 || value > 5000) return 64;
        hold_ms = (int)value;
    }
    if (geteuid() != 0) { errno = EPERM; return failure("root_required"); }
    sigset_t mask; sigemptyset(&mask);
    if (sigprocmask(SIG_SETMASK, &mask, NULL) < 0) return failure("signal_mask");
    signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL);
    signal(SIGCHLD, SIG_DFL); signal(SIGPIPE, SIG_IGN);
    if (fixture.run.terminal && guest_terminal_release() < 0) return failure("guest_terminal_release");
    int state = scope_state_open(argv[3]);
    if (state < 0) return failure("state_directory");
    int run_lock = guest_run_lock(state);
    if (run_lock < 0) { close(state); return failure("run_lock"); }
    if (scope_state_recover(state) < 0) { close(state); return failure("state_recovery"); }
    if (recovering) { close(state); puts("{\"protocol\":1,\"recovered\":true}"); return 0; }
    if (running && guest_run_begin(state, &fixture.run) < 0) { close(state); return failure("guest_run_identity"); }
    if (prctl(PR_SET_CHILD_SUBREAPER, 1) < 0) { close(state); return failure("coordinator_subreaper"); }
    int owner = (int)syscall(SYS_pidfd_open, getpid(), 0);
    if (owner < 0) return failure("owner_pidfd");
    pid_t child = fork();
    if (child < 0) { close(owner); return failure("guard_fork"); }
    if (child == 0) { int code = guard(owner, hold_ms, state, run_lock, &fixture); close(owner); close(state); exit(code); }
    close(owner);
    int code = wait_child(child);
    if (scope_state_recover(state) < 0) { close(state); return failure("coordinator_recovery"); }
    close(state);
    /* A guard killed by SIGKILL can orphan its workers to this subreaper.
     * Recovery has confirmed its recorded group empty before this wait. */
    int status;
    while (waitpid(-1, &status, 0) >= 0 || errno == EINTR) {}
    return code;
}
