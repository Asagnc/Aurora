#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

/* Exercise the production implementation with CONFIG_PROC_CHILDREN absent. */
static int missing_children(const char *path, int flags, ...) {
    if (strstr(path, "/children")) { errno = ENOENT; return -1; }
    return open(path, flags);
}
#define open missing_children
#include "../app/src/main/cpp/managed/reap.c"
#undef open

long long task_scope_now(void) {
    struct timespec value;
    assert(!clock_gettime(CLOCK_MONOTONIC, &value));
    return (long long)value.tv_sec * 1000 + value.tv_nsec / 1000000;
}

int main(void) {
    assert(!prctl(PR_SET_CHILD_SUBREAPER, 1));
    signal(SIGCHLD, SIG_DFL);
    int result = -1;
    assert(!managed_reap_children(0, task_scope_now() + 1000, &result));
    pid_t child = fork(); assert(child >= 0);
    if (!child) _exit(37);
    int descriptor = (int)syscall(SYS_pidfd_open, child, 0); assert(descriptor >= 0);
    struct pollfd event = {.fd = descriptor, .events = POLLIN};
    assert(poll(&event, 1, 1000) == 1); close(descriptor);
    assert(!managed_reap_children(child, task_scope_now() + 1000, &result));
    assert(result == 37);
    child = fork(); assert(child >= 0);
    if (!child) { for (;;) pause(); }
    assert(managed_reap_children(child, task_scope_now() + 1000, &result) == -1);
    assert(errno == ENOENT); /* Live child plus unavailable enumeration is unknown. */
    assert(!kill(child, SIGKILL)); assert(waitpid(child, NULL, 0) == child);
    assert(!managed_reap_children(0, task_scope_now() + 1000, &result));
    puts("managed_reap_no_proc_children_passed");
    return 0;
}
