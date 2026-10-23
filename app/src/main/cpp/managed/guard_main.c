/* Development native guard: not APK-packaged until full worker qualification. */
#define _GNU_SOURCE
#include "guard.h"
#include "reap.h"
#include "../linux/scope_state.h"
#include <errno.h>
#include <limits.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

struct options { const char *state; struct managed_key key; unsigned observe_ms; int recover; char **worker; };

static int parse(int argc, char **argv, struct options *options) {
    if (argc < 8 || (strcmp(argv[1], "run") && strcmp(argv[1], "recover")) || strcmp(argv[2], "--state-dir") ||
        argv[3][0] != '/' || strcmp(argv[4], "--run-id") || strcmp(argv[6], "--ticket-sha")) return -1;
    *options = (struct options){.state = argv[3], .recover = !strcmp(argv[1], "recover")};
    if (managed_key_init(&options->key, argv[5], argv[7])) return -1;
    if (options->recover) return argc == 8 ? 0 : -1;
    if (argc != 16 || strcmp(argv[8], "--observe-ms") || strcmp(argv[10], "--") ||
        !*argv[9] || strspn(argv[9], "0123456789") != strlen(argv[9])) return -1;
    errno = 0; char *end;
    unsigned long observe = strtoul(argv[9], &end, 10);
    if (errno || *end || !observe || observe > 250000) return -1;
    options->observe_ms = (unsigned)observe; options->worker = argv + 11;
    return 0;
}

static int failure(const char *operation) {
    fprintf(stderr, "{\"error\":\"managed_task_unconfirmed\",\"operation\":\"%s\",\"errno\":%d}\n", operation, errno);
    return 126;
}

static int wait_guard(pid_t guard, int64_t deadline, int *status) {
    int descriptor = (int)syscall(SYS_pidfd_open, guard, 0);
    if (descriptor < 0) return -1;
    int forced = 0;
    for (;;) {
        long long now = task_scope_now(), remaining = deadline - now;
        if (now < 0) { close(descriptor); return -1; }
        struct pollfd event = {.fd = descriptor, .events = POLLIN};
        int ready = poll(&event, 1, remaining <= 0 ? 0 : (int)(remaining > INT_MAX ? INT_MAX : remaining));
        if (ready < 0 && errno == EINTR) continue;
        if (ready > 0 && (event.revents & POLLIN)) break;
        if (ready || forced) { int saved = ready < 0 ? errno : ETIMEDOUT; close(descriptor); errno = saved; return -1; }
        if (syscall(SYS_pidfd_send_signal, descriptor, SIGKILL, NULL, 0) < 0 && errno != ESRCH) {
            int saved = errno; close(descriptor); errno = saved; return -1;
        }
        forced = 1; deadline = task_scope_now() + 1000;
    }
    close(descriptor);
    pid_t reaped;
    do { reaped = waitpid(guard, status, WNOHANG); } while (reaped < 0 && errno == EINTR);
    if (reaped != guard) { if (reaped >= 0) errno = ECHILD; return -1; }
    return 0;
}

static int coordinate(const struct managed_guard_spec *spec) {
    int owner = (int)syscall(SYS_pidfd_open, getpid(), 0);
    if (owner < 0) return failure("owner_pidfd");
    pid_t guard = fork();
    if (guard < 0) { close(owner); return failure("guard_fork"); }
    if (!guard) {
        int result = managed_guard_execute(spec, owner);
        struct managed_journal inherited = *spec->journal;
        close(owner); managed_journal_close(&inherited);
        _exit(result < 0 ? 126 : result);
    }
    close(owner);
    int64_t deadline = spec->started_ms + spec->observe_ms + 20000;
    int status;
    if (wait_guard(guard, deadline, &status)) return failure("guard_wait");
    int exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
    if (managed_guard_recover(spec->journal, spec->key, deadline)) {
        int saved = errno, ignored = -1;
        /* Corrupt metadata must not keep this coordinator's own adopted
         * children running. Do not touch any unverified external task domain. */
        managed_reap_children(0, deadline, &ignored);
        errno = saved; return failure("guard_recovery");
    }
    int outcome = managed_guard_summary(spec->journal, spec->key);
    if (outcome < 0) return failure("final_record");
    return exit_code > 1 ? exit_code : outcome;
}

int main(int argc, char **argv) {
    struct options options;
    if (parse(argc, argv, &options)) return 64;
    if (geteuid()) return 77;
    sigset_t signals; sigemptyset(&signals);
    if (sigprocmask(SIG_SETMASK, &signals, NULL)) return failure("signal_mask");
    signal(SIGCHLD, SIG_DFL); signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL); signal(SIGPIPE, SIG_IGN);
    if (prctl(PR_SET_CHILD_SUBREAPER, 1)) return failure("subreaper");
    int64_t started = task_scope_now();
    if (started < 0) return failure("clock");
    int directory = scope_state_open(options.state);
    if (directory < 0) return failure("state_directory");
    struct managed_journal journal;
    int opened = managed_journal_open(&journal, directory), saved = errno;
    close(directory); errno = saved;
    if (opened) return failure("task_lock");
    int result;
    if (options.recover) {
        result = managed_guard_recover(&journal, &options.key, started + 20000);
        if (result) result = failure("recovery");
        else result = managed_guard_summary(&journal, &options.key) < 0 ? failure("final_record") : 0;
    } else if (managed_journal_begin(&journal, &options.key)) result = failure("intent");
    else {
        struct managed_guard_spec spec = {.journal = &journal, .key = &options.key, .argv = options.worker,
            .observe_ms = options.observe_ms, .started_ms = started};
        result = coordinate(&spec);
    }
    managed_journal_close(&journal);
    return result;
}
