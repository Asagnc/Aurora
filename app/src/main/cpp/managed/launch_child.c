#define _GNU_SOURCE
#include "launch_internal.h"
#include "../frida/managed_control.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/close_range.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>

static _Noreturn void fail_child(int descriptor, const struct managed_key *key) {
    struct aurora_managed_control control = {.fd = descriptor};
    for (unsigned index = 0; index < 32; index++) {
        char byte[3] = {key->ticket[index * 2], key->ticket[index * 2 + 1], 0};
        control.ticket[index] = (unsigned char)strtoul(byte, NULL, 16);
    }
    struct aurora_managed_facts facts = {.flags = AURORA_FACT_NO_SESSION};
    _exit(aurora_managed_report(&control, &facts, 125) ? 74 : 125);
}

_Noreturn void managed_launch_child(const struct managed_launch_input *input, const struct managed_child_channels *channels) {
    close(channels->control[0]); close(channels->gate[0]);
    int control = channels->control[1], gate = channels->gate[1];
    if (prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != channels->parent) _exit(125);
    sigset_t signals;
    if (sigemptyset(&signals) || sigprocmask(SIG_SETMASK, &signals, NULL)) fail_child(control, input->key);
    if (syscall(SYS_close_range, 3u, UINT_MAX, CLOSE_RANGE_CLOEXEC)) fail_child(control, input->key);
    struct managed_journal inherited = *input->journal;
    managed_journal_close(&inherited); close(input->owner_pidfd);
    if (input->cancel_enabled) close(input->cancel_fd);
    if (task_scope_enter(input->scope)) fail_child(control, input->key);
    struct task_scope admitted = *input->scope;
    task_scope_close(&admitted);
    if (send(gate, "r", 1, MSG_NOSIGNAL) != 1) fail_child(control, input->key);
    unsigned char go;
    ssize_t count;
    do { count = recv(gate, &go, 1, 0); } while (count < 0 && errno == EINTR);
    close(gate);
    if (count != 1 || go != 'g') fail_child(control, input->key);
    char descriptor[32]; snprintf(descriptor, sizeof(descriptor), "%d", control);
    int flags = fcntl(control, F_GETFD);
    if (flags < 0 || fcntl(control, F_SETFD, flags & ~FD_CLOEXEC) ||
        setenv("AURORA_MANAGED_FD", descriptor, 1) || setenv("AURORA_MANAGED_TICKET", input->key->ticket, 1))
        fail_child(control, input->key);
    /* The packaged worker bootstrap restores CLOEXEC before launching helpers.
     * Keep the guard's environment; the Root facade must supply its private TMPDIR. */
    execv(input->argv[0], input->argv);
    fail_child(control, input->key);
}
