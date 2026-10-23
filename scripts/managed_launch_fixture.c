/* Host/NDK qualification fixture only. Simulated facts, no Frida injection. */
#define _GNU_SOURCE
#include "launch.h"
#include "../linux/scope_state.h"
#include "../frida/managed_observe.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

static int worker(char **argv) {
    struct aurora_managed_control control;
    if (aurora_managed_open(&control) || control.fd < 3) return 80;
    const char *private_fd = getenv("AURORA_FIXTURE_FD"), *marker = getenv("AURORA_FIXTURE_MARKER");
    if (!private_fd || !marker || fcntl(atoi(private_fd), F_GETFD) >= 0 || errno != EBADF) return 81;
    char descriptor[32]; snprintf(descriptor, sizeof(descriptor), "%d", control.fd);
    pid_t helper = fork();
    if (helper < 0) return 82;
    if (!helper) { execl(argv[0], argv[0], "--helper-check", descriptor, NULL); _exit(83); }
    int status;
    if (waitpid(helper, &status, 0) != helper || status) return 84;
    int file = open(marker, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
    if (file < 0) return 85;
    close(file);
    /* These bits intentionally simulate the worker protocol for this fixture;
     * only the separate Frida session suite verifies actual injection. */
    struct aurora_managed_facts facts = {.flags = 127};
    int result = aurora_managed_report(&control, &facts, 0);
    aurora_managed_close(&control);
    return result ? 86 : 0;
}

struct fixture {
    struct task_scope scope;
    struct managed_journal journal;
    struct managed_key key;
    struct managed_launch launch;
    struct managed_launch_input input;
    struct aurora_managed_observation observation;
    pid_t owner;
    int owner_fd, private_fd, scope_created, scope_recorded;
    int cancellation[2];
};

static int setup(struct fixture *fixture, char **argv, char **arguments) {
    if (prctl(PR_SET_CHILD_SUBREAPER, 1)) return -1;
    int state = open(argv[2], O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (state < 0) return -1;
    int opened = managed_journal_open(&fixture->journal, state);
    int saved = errno; close(state); errno = saved;
    if (opened || managed_key_init(&fixture->key, "00000000-0000-0000-0000-000000000002",
        "1111111111111111111111111111111111111111111111111111111111111111") ||
        managed_journal_begin(&fixture->journal, &fixture->key)) return -1;
    pid_t parent = getpid();
    fixture->owner = fork();
    if (fixture->owner < 0) return -1;
    if (!fixture->owner) {
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != parent) _exit(80);
        managed_journal_close(&fixture->journal);
        for (;;) pause();
    }
    fixture->owner_fd = (int)syscall(SYS_pidfd_open, fixture->owner, 0);
    if (fixture->owner_fd < 0 || task_scope_create(&fixture->scope)) return -1;
    fixture->scope_created = 1;
    if (scope_state_save(fixture->journal.directory, &fixture->scope)) return -1;
    fixture->scope_recorded = 1;
    fixture->private_fd = open("/dev/null", O_RDONLY); // Deliberately not CLOEXEC.
    char descriptor[32]; snprintf(descriptor, sizeof(descriptor), "%d", fixture->private_fd);
    if (fixture->private_fd < 0 || setenv("AURORA_FIXTURE_FD", descriptor, 1) ||
        setenv("AURORA_FIXTURE_MARKER", argv[3], 1)) return -1;
    fixture->input = (struct managed_launch_input){.scope = &fixture->scope, .journal = &fixture->journal,
        .key = &fixture->key, .argv = arguments, .owner_pidfd = fixture->owner_fd,
        .admission_deadline_ms = task_scope_now() + 3000};
    return 0;
}

static void stop_owner(struct fixture *fixture) {
    if (fixture->owner > 0) {
        kill(fixture->owner, SIGKILL);
        while (waitpid(fixture->owner, NULL, 0) < 0 && errno == EINTR) {}
        fixture->owner = 0;
    }
}

static int observe(struct fixture *fixture) {
    struct managed_launch *launch = &fixture->launch;
    if (launch->pidfd < 0 || !launch->admission.worker_ticks) return 0;
    struct aurora_managed_observer observer = {.socket = launch->control, .owner_pidfd = fixture->owner_fd,
        .worker_pidfd = launch->pidfd, .worker = {.pid = (uint32_t)launch->pid, .ticks = launch->admission.worker_ticks},
        .operation_deadline_ms = task_scope_now() + 2000, .force_deadline_ms = task_scope_now() + 3000,
        .cancel_fd = fixture->input.cancel_fd, .cancel_enabled = fixture->input.cancel_enabled};
    memset(observer.ticket, 17, 32);
    return aurora_managed_observe(&observer, &fixture->observation);
}

static int teardown(struct fixture *fixture, int *worker_status) {
    int failed = 0;
    if (fixture->launch.pid > 0) kill(fixture->launch.pid, SIGKILL); // Unreaped, owned child only.
    if (fixture->scope_created && (task_scope_kill(&fixture->scope) || task_scope_wait_empty(&fixture->scope, 5000))) failed = 1;
    if (fixture->launch.pid > 0) {
        int status;
        pid_t reaped;
        do { reaped = waitpid(fixture->launch.pid, &status, 0); } while (reaped < 0 && errno == EINTR);
        if (reaped != fixture->launch.pid) failed = 1;
        else *worker_status = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
    }
    stop_owner(fixture);
    if (!failed) while (waitpid(-1, NULL, 0) >= 0 || errno == EINTR) {}
    if (fixture->scope_created && task_scope_remove(&fixture->scope)) failed = 1;
    if (!failed && fixture->scope_recorded && scope_state_forget(fixture->journal.directory, fixture->scope.name)) failed = 1;
    managed_launch_close(&fixture->launch);
    if (fixture->owner_fd >= 0) close(fixture->owner_fd);
    if (fixture->private_fd >= 0) close(fixture->private_fd);
    for (unsigned index = 0; index < 2; index++) if (fixture->cancellation[index] >= 0) close(fixture->cancellation[index]);
    return failed ? -1 : 0;
}

static int qualify(char **argv) {
    struct fixture fixture = {.journal = {.directory = -1, .lock = -1},
        .launch = {.pidfd = -1, .control = -1, .gate = -1}, .owner_fd = -1, .private_fd = -1, .cancellation = {-1, -1}};
    char *arguments[] = {argv[0], "attach-channel", "fixture-channel",
        "2222222222222222222222222222222222222222222222222222222222222222", "{}", NULL};
    if (!strcmp(argv[1], "exec-fail")) arguments[0] = "/aurora-fixture-not-installed/worker";
    if (!strcmp(argv[1], "bad-argv")) arguments[3] = "bad";
    if (setup(&fixture, argv, arguments)) goto failure;
    if (!strncmp(argv[1], "cancel-", 7)) {
        if (socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0, fixture.cancellation)) goto failure;
        fixture.input.cancel_enabled = 1; fixture.input.cancel_fd = fixture.cancellation[0];
        if (!strcmp(argv[1], "cancel-before") && write(fixture.cancellation[1], "c", 1) != 1) goto failure;
    }
    if (!strcmp(argv[1], "retired-before") && managed_journal_retire(&fixture.journal, &fixture.key)) goto failure;
    if (!strcmp(argv[1], "owner-before")) stop_owner(&fixture);
    if (!strcmp(argv[1], "auto-reap")) signal(SIGCHLD, SIG_IGN);
    if (!strcmp(argv[1], "admission-fail")) { close(fixture.scope.processes); fixture.scope.processes = -1; }
    int prepared = managed_launch_prepare(&fixture.input, &fixture.launch), prepare_error = errno;
    int phase = managed_journal_status(&fixture.journal, &fixture.key, NULL);
    int before = access(argv[3], F_OK) == 0, released = -2, release_error = 0, second = 0;
    if (!prepared) {
        if (!strcmp(argv[1], "cancel-ready") && write(fixture.cancellation[1], "c", 1) != 1) goto failure;
        if (!strcmp(argv[1], "retired-ready") && managed_journal_retire(&fixture.journal, &fixture.key)) goto failure;
        if (!strcmp(argv[1], "owner-after")) stop_owner(&fixture);
        if (!strcmp(argv[1], "worker-before-go")) {
            kill(fixture.launch.pid, SIGKILL);
            struct pollfd event = {.fd = fixture.launch.pidfd, .events = POLLIN};
            if (poll(&event, 1, 3000) != 1) goto failure;
        }
        released = managed_launch_release(&fixture.input, &fixture.launch); release_error = errno;
        if (!strcmp(argv[1], "duplicate-go")) second = managed_launch_release(&fixture.input, &fixture.launch);
    }
    if (fixture.launch.gate >= 0) { close(fixture.launch.gate); fixture.launch.gate = -1; }
    int observed = observe(&fixture), worker_status = -1;
    int cleaned = teardown(&fixture, &worker_status), after = access(argv[3], F_OK) == 0;
    int retired = cleaned ? -1 : managed_journal_retire(&fixture.journal, &fixture.key);
    managed_journal_close(&fixture.journal);
    printf("{\"prepared\":%d,\"prepareError\":%d,\"phase\":%d,\"before\":%d,\"released\":%d,\"releaseError\":%d,\"second\":%d,\"observed\":%d,\"frame\":%d,\"flags\":%u,\"after\":%d,\"workerStatus\":%d,\"cleaned\":%d,\"retired\":%d}\n",
        prepared, prepare_error, phase, before, released, release_error, second, observed,
        fixture.observation.receiver.state, fixture.observation.receiver.receipt.flags, after, worker_status, cleaned, retired);
    return 0;
failure:
    perror("fixture qualification");
    int ignored; teardown(&fixture, &ignored); managed_journal_close(&fixture.journal);
    return 80;
}

int main(int argc, char **argv) {
    if (argc == 3 && !strcmp(argv[1], "--helper-check"))
        return fcntl(atoi(argv[2]), F_GETFD) < 0 && errno == EBADF &&
            !getenv("AURORA_MANAGED_FD") && !getenv("AURORA_MANAGED_TICKET") ? 0 : 87;
    if (argc == 5 && !strcmp(argv[1], "attach-channel")) return worker(argv);
    if (argc != 4 || geteuid()) return 64;
    return qualify(argv);
}
