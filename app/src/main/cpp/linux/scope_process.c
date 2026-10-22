#define _GNU_SOURCE
#include "scope_state.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

int scope_process_start(pid_t pid, uint64_t *start) {
    char path[64], buffer[65537];
    snprintf(path, sizeof(path), "/proc/%ld/stat", (long)pid);
    int file = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (file < 0) return -1;
    ssize_t count;
    do { count = read(file, buffer, sizeof(buffer) - 1); } while (count < 0 && errno == EINTR);
    int saved = errno; close(file); errno = saved;
    if (count <= 0 || count >= (ssize_t)sizeof(buffer) - 1) { errno = EINVAL; return -1; }
    buffer[count] = 0;
    char *end = strrchr(buffer, ')');
    if (!end) { errno = EINVAL; return -1; }
    char *state, *token = strtok_r(end + 1, " \t\n", &state);
    for (int i = 0; i < 19 && token; ++i) token = strtok_r(NULL, " \t\n", &state);
    if (!token || !*token || strspn(token, "0123456789") != strlen(token)) { errno = EINVAL; return -1; }
    errno = 0;
    unsigned long long ticks = strtoull(token, &end, 10);
    if (errno || *end || !ticks) { errno = EINVAL; return -1; }
    *start = ticks; return 0;
}

int scope_boot_id(char boot[40]) {
    int file = open("/proc/sys/kernel/random/boot_id", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (file < 0) return -1;
    ssize_t count;
    do { count = read(file, boot, 39); } while (count < 0 && errno == EINTR);
    int saved = errno; close(file); errno = saved;
    if (count != 37 || boot[36] != '\n') { errno = EINVAL; return -1; }
    boot[36] = 0;
    for (int i = 0; i < 36; ++i) {
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (boot[i] == '-') continue; }
        else if (strchr("0123456789abcdef", boot[i])) continue;
        errno = EINVAL; return -1;
    }
    return 0;
}

/* Return 1 only for a proven exited/replaced identity, 0 for alive, -1 if
 * permissions or incomplete proc data prevent a trustworthy determination. */
static int missing_process(pid_t pid) {
    if (errno != ENOENT) return -1;
    if (kill(pid, 0) < 0 && errno == ESRCH) return 1;
    errno = EACCES; return -1;
}

int scope_guard_exited(const struct scope_record *record) {
    uint64_t start;
    if (scope_process_start((pid_t)record->guard_pid, &start) < 0) return missing_process((pid_t)record->guard_pid);
    if (start != record->guard_start) return 1;
    int descriptor = (int)syscall(SYS_pidfd_open, (pid_t)record->guard_pid, 0);
    if (descriptor < 0) return errno == ESRCH ? 1 : -1;
    int checked = scope_process_start((pid_t)record->guard_pid, &start);
    int saved = errno;
    struct pollfd event = {.fd = descriptor, .events = POLLIN};
    int polled = poll(&event, 1, 0);
    close(descriptor);
    if (checked < 0) { errno = saved; return missing_process((pid_t)record->guard_pid); }
    if (start != record->guard_start) return 1;
    if (polled < 0 || (event.revents & POLLNVAL)) return -1;
    return polled == 1 && (event.revents & POLLIN) ? 1 : 0;
}
