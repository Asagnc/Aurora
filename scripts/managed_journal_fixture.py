"""Native journal fixture and syscall failures, never part of the APK."""
HARNESS = r'''
#define _GNU_SOURCE
#include "journal.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
int __real_fsync(int fd);
ssize_t __real_write(int fd, const void *bytes, size_t size);
int __wrap_fsync(int fd) {
    const char *fault = getenv("AURORA_JOURNAL_FAIL");
    struct stat info; static int directories;
    if (!fstat(fd, &info)) {
        if (S_ISDIR(info.st_mode)) directories++;
        if (fault && ((!strcmp(fault, "record") && S_ISREG(info.st_mode)) ||
            (!strcmp(fault, "publish") && S_ISDIR(info.st_mode) && directories == 2))) { errno = EIO; return -1; }
    }
    return __real_fsync(fd);
}
ssize_t __wrap_write(int fd, const void *bytes, size_t size) {
    const char *fault = getenv("AURORA_JOURNAL_FAIL");
    struct stat info; static int partial;
    if (fault && !strcmp(fault, "partial") && !fstat(fd, &info) && S_ISREG(info.st_mode)) {
        if (partial++) { errno = EIO; return -1; }
        return __real_write(fd, bytes, size > 5 ? 5 : size);
    }
    return __real_write(fd, bytes, size);
}
int main(int argc, char **argv) {
    if (argc != 5) return 64;
    struct managed_key key;
    if (managed_key_init(&key, argv[3], argv[4])) {
        printf("{\"opened\":-1,\"result\":-1,\"error\":%d}\n", errno); return 0;
    }
    int directory = open(argv[1], O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    struct managed_journal journal;
    int opened = managed_journal_open(&journal, directory), error = errno;
    if (directory >= 0) close(directory);
    if (opened) { printf("{\"opened\":-1,\"result\":-1,\"error\":%d}\n", error); return 0; }
    struct managed_admission admission = {.guard_pid = 123, .guard_ticks = 456, .worker_pid = 789, .worker_ticks = 999,
        .boot = "00000000-0000-0000-0000-000000000001"};
    errno = 0; int result = -1;
    if (!strcmp(argv[2], "begin")) result = managed_journal_begin(&journal, &key);
    else if (!strcmp(argv[2], "ready")) result = managed_journal_ready(&journal, &key, &admission);
    else if (!strcmp(argv[2], "release")) result = managed_journal_release(&journal, &key);
    else if (!strcmp(argv[2], "retire")) result = managed_journal_retire(&journal, &key);
    else if (!strcmp(argv[2], "status")) result = managed_journal_status(&journal, &key, &admission);
    else if (!strcmp(argv[2], "hold") || !strcmp(argv[2], "fork-close")) {
        if (!strcmp(argv[2], "fork-close")) {
            pid_t child = fork();
            if (child < 0) return 80;
            if (!child) { managed_journal_close(&journal); _exit(0); }
            int status; if (waitpid(child, &status, 0) != child || status) return 80;
        }
        puts("locked"); fflush(stdout); result = getchar() == EOF ? -1 : 0;
    }
    else errno = EINVAL;
    error = errno;
    managed_journal_close(&journal);
    printf("{\"opened\":0,\"result\":%d,\"error\":%d,\"guard\":%u,\"worker\":%u,\"ticks\":%llu}\n",
        result, error, admission.guard_pid, admission.worker_pid, (unsigned long long)admission.worker_ticks);
    return 0;
}
'''
