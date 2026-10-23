#define _GNU_SOURCE
#include "launch_internal.h"
#include "../linux/scope_state.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <signal.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>

static int alive(int descriptor) {
    struct pollfd event = {.fd = descriptor, .events = POLLIN};
    int result;
    do { result = poll(&event, 1, 0); } while (result < 0 && errno == EINTR);
    if (result == 0) return 0;
    if (result > 0) errno = event.revents & POLLNVAL ? EBADF : ECANCELED;
    return -1;
}

static int owner_active(const struct managed_launch_input *input) {
    if (alive(input->owner_pidfd)) return -1;
    return input->cancel_enabled ? alive(input->cancel_fd) : 0;
}

static int valid_input(const struct managed_launch_input *input) {
    if (geteuid()) { errno = EPERM; return -1; }
    if (!input->scope || !input->journal || !input->key || !input->argv || input->owner_pidfd < 0 ||
        input->admission_deadline_ms <= 0 || (input->cancel_enabled && input->cancel_fd < 0)) { errno = EINVAL; return -1; }
    for (int index = 0; index < 5; index++) if (!input->argv[index]) { errno = EINVAL; return -1; }
    if (input->argv[5] || input->argv[0][0] != '/' || strcmp(input->argv[1], "attach-channel") ||
        !*input->argv[2] || strnlen(input->argv[2], 97) > 96 || strnlen(input->argv[3], 65) != 64 ||
        strspn(input->argv[3], "0123456789abcdef") != 64 || strnlen(input->argv[4], 16385) > 16384) {
        errno = EINVAL; return -1;
    }
    for (int descriptor = 0; descriptor < 3; descriptor++) if (fcntl(descriptor, F_GETFD) < 0) return -1;
    struct sigaction child_signal;
    if (sigaction(SIGCHLD, NULL, &child_signal)) return -1;
    if (child_signal.sa_handler != SIG_DFL || (child_signal.sa_flags & SA_NOCLDWAIT)) { errno = EINVAL; return -1; }
    int phase = managed_journal_status(input->journal, input->key, NULL);
    if (phase < 0) return -1;
    if (phase != MANAGED_INTENT) { errno = phase & MANAGED_RETIRED ? ECANCELED : EPROTO; return -1; }
    return owner_active(input);
}

static int wait_admitted(const struct managed_launch_input *input, const struct managed_launch *launch) {
    for (;;) {
        long long now = task_scope_now(), left = input->admission_deadline_ms - now;
        if (now < 0) return -1;
        if (left <= 0) { errno = ETIMEDOUT; return -1; }
        struct pollfd events[] = {{.fd = input->owner_pidfd, .events = POLLIN},
            {.fd = launch->pidfd, .events = POLLIN}, {.fd = launch->gate, .events = POLLIN},
            {.fd = input->cancel_enabled ? input->cancel_fd : -1, .events = POLLIN}};
        int ready = poll(events, 4, (int)(left > INT_MAX ? INT_MAX : left));
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) return -1;
        if (!ready) { errno = ETIMEDOUT; return -1; }
        if (events[0].revents || events[1].revents || events[3].revents) { errno = ECANCELED; return -1; }
        if (events[2].revents) {
            unsigned char byte;
            if (recv(launch->gate, &byte, 1, MSG_DONTWAIT) == 1 && byte == 'r') return 0;
            errno = EPIPE; return -1;
        }
    }
}

void managed_launch_close(struct managed_launch *launch) {
    if (launch->control >= 0) close(launch->control);
    if (launch->gate >= 0) close(launch->gate);
    if (launch->pidfd >= 0) close(launch->pidfd);
    launch->control = launch->gate = launch->pidfd = -1;
    launch->prepared = 0;
}

int managed_launch_prepare(const struct managed_launch_input *input, struct managed_launch *launch) {
    *launch = (struct managed_launch){.pidfd = -1, .control = -1, .gate = -1};
    if (valid_input(input)) return -1;
    struct managed_child_channels channels = {.parent = getpid()};
    if (socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, channels.control)) return -1;
    if (socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, channels.gate)) {
        int saved = errno; close(channels.control[0]); close(channels.control[1]); errno = saved; return -1;
    }
    launch->pid = fork();
    if (!launch->pid) managed_launch_child(input, &channels);
    int saved = errno;
    close(channels.control[1]); close(channels.gate[1]);
    launch->control = channels.control[0]; launch->gate = channels.gate[0];
    if (launch->pid < 0) { managed_launch_close(launch); errno = saved; return -1; }
    launch->pidfd = (int)syscall(SYS_pidfd_open, launch->pid, 0);
    launch->admission.guard_pid = (uint32_t)getpid(); launch->admission.worker_pid = (uint32_t)launch->pid;
    char boot[40];
    if (launch->pidfd < 0 || scope_process_start(launch->pid, &launch->admission.worker_ticks) ||
        scope_process_start(getpid(), &launch->admission.guard_ticks) || scope_boot_id(boot) || wait_admitted(input, launch)) return -1;
    memcpy(launch->admission.boot, boot, 37);
    if (managed_journal_ready(input->journal, input->key, &launch->admission) || owner_active(input) || alive(launch->pidfd)) return -1;
    launch->prepared = 1; return 0;
}

int managed_launch_release(const struct managed_launch_input *input, struct managed_launch *launch) {
    if (!launch->prepared) { errno = EINVAL; return -1; }
    launch->prepared = 0;
    struct managed_admission recorded;
    int phase = managed_journal_status(input->journal, input->key, &recorded);
    if (phase < 0) return -1;
    if (phase & MANAGED_RETIRED) { errno = ECANCELED; return -1; }
    const struct managed_admission *expected = &launch->admission;
    if (phase != (MANAGED_INTENT | MANAGED_READY) || recorded.guard_pid != (uint32_t)getpid() ||
        recorded.guard_pid != expected->guard_pid || recorded.guard_ticks != expected->guard_ticks ||
        recorded.worker_pid != expected->worker_pid || recorded.worker_ticks != expected->worker_ticks || strcmp(recorded.boot, expected->boot)) {
        errno = EPROTO; return -1;
    }
    if (owner_active(input) || alive(launch->pidfd) || managed_journal_release(input->journal, input->key) ||
        owner_active(input) || alive(launch->pidfd)) return -1;
    ssize_t sent;
    do { sent = send(launch->gate, "g", 1, MSG_NOSIGNAL | MSG_DONTWAIT); } while (sent < 0 && errno == EINTR);
    if (sent != 1) { if (sent >= 0) errno = EPIPE; return -1; }
    launch->dispatched = 1;
    close(launch->gate); launch->gate = -1;
    return 0;
}
