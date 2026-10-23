#define _GNU_SOURCE
#include "reap.h"
#include "../linux/task_scope.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/statfs.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#define MAX_CHILDREN 512

static int children(pid_t output[MAX_CHILDREN]) {
    char path[96], bytes[MAX_CHILDREN * 12 + 1];
    snprintf(path, sizeof(path), "/proc/self/task/%ld/children", (long)getpid());
    int file = open(path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
    if (file < 0) return -1;
    struct statfs filesystem;
    if (fstatfs(file, &filesystem) || filesystem.f_type != 0x9fa0) { close(file); errno = EPERM; return -1; }
    size_t size = 0;
    for (;;) {
        ssize_t count = read(file, bytes + size, sizeof(bytes) - 1 - size);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) { int saved = errno; close(file); errno = saved; return -1; }
        if (!count) break;
        size += (size_t)count;
        if (size == sizeof(bytes) - 1) { close(file); errno = E2BIG; return -1; }
    }
    close(file);
    if (memchr(bytes, 0, size) || (size && bytes[size - 1] != ' ' && bytes[size - 1] != '\n')) { errno = EPROTO; return -1; }
    bytes[size] = 0;
    char *cursor = bytes; int count = 0;
    while (*cursor) {
        while (*cursor == ' ' || *cursor == '\n') cursor++;
        if (!*cursor) break;
        if (*cursor < '0' || *cursor > '9' || count == MAX_CHILDREN) { errno = EPROTO; return -1; }
        errno = 0; char *end;
        unsigned long value = strtoul(cursor, &end, 10);
        if (errno || !value || value > INT_MAX || (*end && *end != ' ' && *end != '\n')) { errno = EPROTO; return -1; }
        for (int index = 0; index < count; index++) if (output[index] == (pid_t)value) { errno = EPROTO; return -1; }
        output[count++] = (pid_t)value; cursor = end;
    }
    return count;
}

static int stop_child(pid_t child, int64_t deadline, int *status) {
    /* Default SIGCHLD and this guard's sole reaper keep an unreaped direct
     * child PID owned until waitpid, even if it already became a zombie. */
    int descriptor = (int)syscall(SYS_pidfd_open, child, 0);
    if (descriptor < 0) return -1;
    siginfo_t information = {0};
    if (waitid(P_PIDFD, (id_t)descriptor, &information, WEXITED | WNOHANG | WNOWAIT)) {
        int saved = errno; close(descriptor); errno = saved; return -1;
    }
    if (syscall(SYS_pidfd_send_signal, descriptor, SIGKILL, NULL, 0) < 0 && errno != ESRCH) {
        int saved = errno; close(descriptor); errno = saved; return -1;
    }
    for (;;) {
        long long now = task_scope_now(), remaining = deadline - now;
        if (now < 0) { close(descriptor); return -1; }
        struct pollfd event = {.fd = descriptor, .events = POLLIN};
        int result = poll(&event, 1, remaining <= 0 ? 0 : (int)(remaining > INT_MAX ? INT_MAX : remaining));
        if (result < 0 && errno == EINTR) continue;
        int saved = errno; close(descriptor); errno = saved;
        if (result != 1 || !(event.revents & POLLIN)) { if (result >= 0) errno = ETIMEDOUT; return -1; }
        pid_t reaped;
        do { reaped = waitpid(child, status, WNOHANG); } while (reaped < 0 && errno == EINTR);
        if (reaped != child) { if (reaped >= 0) errno = ECHILD; return -1; }
        return 0;
    }
}

int managed_reap_children(pid_t worker, int64_t deadline_ms, int *worker_exit) {
    int subreaper = 0; struct sigaction action;
    if (getpid() != (pid_t)syscall(SYS_gettid) || prctl(PR_GET_CHILD_SUBREAPER, &subreaper) ||
        !subreaper || sigaction(SIGCHLD, NULL, &action) || action.sa_handler != SIG_DFL || (action.sa_flags & SA_NOCLDWAIT)) {
        errno = EINVAL; return -1;
    }
    for (unsigned round = 0; round < 64; round++) {
        pid_t found[MAX_CHILDREN]; int count = children(found);
        if (count < 0) return -1;
        if (!count) return 0;
        for (int index = 0; index < count; index++) {
            int status;
            if (stop_child(found[index], deadline_ms, &status)) return -1;
            if (found[index] == worker) *worker_exit = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
        }
        /* Child death reparents descendants to this task subreaper. Only now
         * enumerate the new direct children, with the same absolute budget. */
    }
    errno = E2BIG; return -1;
}
