"""Native journal fixture and syscall failures, never part of the APK."""
HARNESS = r'''
#define _GNU_SOURCE
#include "journal.h"
#include "completion.h"
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
    else if (!strcmp(argv[2], "worker-store") || !strcmp(argv[2], "worker-pending")) {
        struct aurora_managed_receiver receiver = {.state = !strcmp(argv[2], "worker-store") ? 1 : 0};
        memcpy(receiver.frame, "AEX1", 4); receiver.frame[4] = 1; receiver.frame[5] = 127;
        memset(receiver.frame + 8, 17, 32);
        receiver.frame[40] = 789 & 255; receiver.frame[41] = 789 >> 8;
        receiver.frame[44] = 999 & 255; receiver.frame[45] = 999 >> 8;
        result = managed_worker_store(&journal, &key, &receiver);
    }
    else if (!strcmp(argv[2], "worker-read")) {
        struct aurora_managed_receipt receipt;
        result = managed_worker_read(&journal, &key, &receipt);
    }
    else if (!strcmp(argv[2], "stop-store") || !strcmp(argv[2], "stop-read")) {
        struct managed_stopped stopped = {.phase = (unsigned)managed_journal_status(&journal, &key, NULL),
            .worker_state = MANAGED_CLEAR, .domain_state = MANAGED_CLEAR, .exit_code = 0};
        if (!(stopped.phase & MANAGED_INTENT)) {
            stopped.worker_state = stopped.domain_state = MANAGED_NOT_CREATED; stopped.exit_code = -1;
        }
        result = !strcmp(argv[2], "stop-store") ? managed_stopped_store(&journal, &key, &stopped) :
            managed_stopped_read(&journal, &key, &stopped);
    }
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

RUN = "00000000-0000-0000-0000-000000000002"
TICKET = "11" * 32
KEY_BYTES = b"ARI1\n" + RUN.encode() + b"\n" + TICKET.encode() + b"\n"
