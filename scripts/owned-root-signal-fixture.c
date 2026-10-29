/* Explicit device test helper, never packaged. Caller supplies the current
 * Aurora test's verified Root endpoint. pidfd pins the signalled identity. */
#define _GNU_SOURCE
#include "input.h"
#include <errno.h>
#include <limits.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

static int root_uids(pid_t pid) {
    char path[64], line[512];
    snprintf(path, sizeof(path), "/proc/%d/status", pid);
    FILE *file = fopen(path, "r");
    if (!file) return 0;
    unsigned a, b, c, d;
    int matched = 0;
    while (fgets(line, sizeof(line), file)) {
        if (sscanf(line, "Uid: %u %u %u %u", &a, &b, &c, &d) == 4) matched = !a && !b && !c && !d;
    }
    fclose(file);
    return matched;
}

int main(int argc, char **argv) {
    if (argc != 4 || strcmp(argv[1], "--owned-root") || getuid() != 0) return 64;
    if (!*argv[2] || strspn(argv[2], "0123456789") != strlen(argv[2]) ||
        !*argv[3] || strspn(argv[3], "0123456789") != strlen(argv[3])) return 64;
    errno = 0;
    unsigned long long number = strtoull(argv[2], NULL, 10), expected = strtoull(argv[3], NULL, 10);
    if (errno || number <= 1 || number > INT_MAX || !expected) return 64;
    pid_t pid = (pid_t) number;
    int fd = (int) syscall(SYS_pidfd_open, pid, 0);
    if (fd < 0) return 65;
    struct pollfd event = {.fd = fd, .events = POLLIN};
    uint64_t observed;
    int verified = !frida_process_start(pid, &observed) && observed == expected && root_uids(pid) && poll(&event, 1, 0) == 0;
    int result = verified ? (int) syscall(SYS_pidfd_send_signal, fd, SIGKILL, NULL, 0) : -1;
    close(fd);
    if (result) return 66;
    puts("{\"exactRootPidfdSignalled\":true}");
    return 0;
}
