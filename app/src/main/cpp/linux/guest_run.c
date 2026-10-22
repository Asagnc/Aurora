#define _GNU_SOURCE
#include "guest_run.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <unistd.h>

/* Inherited by the guard before it can create a scope. Workers must close it
 * before admission so orphan cleanup can acquire it after both supervisors die. */
int guest_run_lock(int state) {
    int file = openat(state, ".run.lock", O_RDWR | O_CREAT | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (file < 0) return -1;
    struct stat info;
    if (fstat(file, &info) < 0 || !S_ISREG(info.st_mode) || info.st_uid != 0 ||
        (info.st_mode & 077) || info.st_nlink != 1 || flock(file, LOCK_EX | LOCK_NB) < 0) {
        int saved = errno ? errno : EPERM; close(file); errno = saved; return -1;
    }
    return file;
}

static int identifier(const char *text, int uuid) {
    size_t length = uuid ? 36 : 64;
    if (strlen(text) != length) return 0;
    for (size_t i = 0; i < length; ++i) {
        if (uuid && (i == 8 || i == 13 || i == 18 || i == 23)) {
            if (text[i] != '-') return 0;
        } else if (!((text[i] >= '0' && text[i] <= '9') || (text[i] >= 'a' && text[i] <= 'f'))) return 0;
    }
    return 1;
}

int guest_run_parse(int argc, char **argv, struct guest_run *run) {
    if (argc < 16 || strcmp(argv[4], "--generation") || strcmp(argv[6], "--run-id") ||
        strcmp(argv[8], "--rootfs") || strcmp(argv[10], "--workspace") ||
        strcmp(argv[12], "--timeout-ms") || strcmp(argv[14], "--") ||
        !identifier(argv[5], 0) || !identifier(argv[7], 1) ||
        argv[9][0] != '/' || argv[11][0] != '/' || argv[15][0] != '/') return -1;
    char *end;
    long timeout = strtol(argv[13], &end, 10);
    if (!*argv[13] || *end || timeout < 1 || timeout > 300000) return -1;
    *run = (struct guest_run){argv[5], argv[7], argv[9], argv[11], argv + 15, (int)timeout, !strcmp(argv[1], "--run-pty")};
    return 0;
}

/* One-use state directory: an old job can never publish completion for a new
 * job. A partial durable file is retained and rejected, never treated as done. */
static int durable_file(int directory, const char *name, const char *text) {
    int file = openat(directory, name, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (file < 0) return -1;
    size_t size = strlen(text), offset = 0;
    while (offset < size) {
        ssize_t count = write(file, text + offset, size - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { int saved = errno ? errno : EIO; close(file); errno = saved; return -1; }
        offset += (size_t)count;
    }
    int status = fsync(file), saved = errno;
    close(file);
    if (status < 0) { errno = saved; return -1; }
    return fsync(directory);
}

int guest_run_begin(int state, const struct guest_run *run) {
    char record[256];
    snprintf(record, sizeof(record), "{\"protocol\":1,\"generation\":\"%s\",\"runId\":\"%s\"}\n", run->generation, run->token);
    return durable_file(state, "run.json", record);
}

int guest_run_finish(int state, int exit_code, int interrupted, int timed_out) {
    char record[160];
    snprintf(record, sizeof(record), "{\"protocol\":1,\"scopeRemoved\":true,\"exitCode\":%d,\"interrupted\":%s,\"timedOut\":%s}\n",
        exit_code, interrupted ? "true" : "false", timed_out ? "true" : "false");
    return durable_file(state, "completed.json", record);
}

int guest_run_exec(const struct guest_run *run) {
    char *const environment[] = {"HOME=/root", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TMPDIR=/tmp", "LANG=C.UTF-8", run->terminal ? "TERM=xterm-256color" : NULL, NULL};
    if (run->terminal) {
        /* The coordinator released only its own PTY before forking. Never steal
         * a terminal from another session (TIOCSCTTY arg must remain zero). */
        if (setsid() < 0 || ioctl(STDIN_FILENO, TIOCSCTTY, 0) < 0 ||
            tcsetpgrp(STDIN_FILENO, getpgrp()) < 0) return 125;
        signal(SIGHUP, SIG_DFL);
    }
    if (chdir("/mnt") < 0 || syscall(SYS_close_range, 3u, UINT_MAX, 4u /* CLOSE_RANGE_CLOEXEC */) < 0) return 125;
    signal(SIGPIPE, SIG_DFL);
    execve(run->argv[0], run->argv, environment);
    return errno == ENOENT ? 127 : 126;
}

int guest_terminal_release(void) {
    struct stat terminal, other;
    if (fstat(STDIN_FILENO, &terminal) < 0 || !S_ISCHR(terminal.st_mode)) return -1;
    for (int fd = 1; fd <= 2; ++fd) {
        if (fstat(fd, &other) < 0 || terminal.st_rdev != other.st_rdev || !isatty(fd)) { errno = ENOTTY; return -1; }
    }
    if (getsid(0) != getpid() || tcgetsid(STDIN_FILENO) != getpid() || tcgetpgrp(STDIN_FILENO) != getpgrp()) {
        errno = EPERM; return -1;
    }
    /* TIOCNOTTY sends HUP to the foreground group. Keep the coordinator alive;
     * the admitted guest resets it after acquiring the now unowned terminal. */
    if (signal(SIGHUP, SIG_IGN) == SIG_ERR) return -1;
    return ioctl(STDIN_FILENO, TIOCNOTTY);
}
