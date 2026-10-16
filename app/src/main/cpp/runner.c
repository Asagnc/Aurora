#define _GNU_SOURCE
#include "capture.h"
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/signalfd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

static int open_artifact(int directory, const char *name) {
    return openat(directory, name, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
}

static void execute(char **arguments, pid_t parent, int pipes[2][2]) {
    if (setpgid(0, 0) < 0 || prctl(PR_SET_PDEATHSIG, SIGKILL) < 0 || getppid() != parent) _exit(126);
    for (int i = 0; i < 2; i++) {
        if (dup2(pipes[i][1], i + 1) < 0) _exit(126);
        close(pipes[i][0]); close(pipes[i][1]);
    }
    signal(SIGPIPE, SIG_DFL);
    sigset_t empty;
    sigemptyset(&empty);
    sigprocmask(SIG_SETMASK, &empty, NULL);
    execvp(arguments[0], arguments);
    int failure = errno;
    perror("Aurora exec");
    _exit(failure == ENOENT ? 127 : 126);
}

static int save_result(int directory, int status, bool stopped, capture_stream streams[2]) {
    int descriptor = open_artifact(directory, ".result.tmp");
    if (descriptor < 0) return -1;
    FILE *file = fdopen(descriptor, "w");
    if (!file) { close(descriptor); return -1; }
    int result = fprintf(file,
        "{\"protocol\":1,\"exitCode\":%d,\"stopped\":%s,\"stdoutBytes\":%llu,\"stderrBytes\":%llu,"
        "\"stdoutStored\":%llu,\"stderrStored\":%llu,\"captureComplete\":%s}",
        WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status), stopped ? "true" : "false",
        (unsigned long long) streams[0].total, (unsigned long long) streams[1].total,
        (unsigned long long) streams[0].stored, (unsigned long long) streams[1].stored,
        streams[0].complete && streams[1].complete ? "true" : "false");
    bool committed = result > 0 && fflush(file) == 0 && fsync(descriptor) == 0;
    if (fclose(file) != 0) committed = false;
    return committed && renameat(directory, ".result.tmp", directory, "result.json") == 0 ? 0 : -1;
}

int main(int argc, char **argv) {
    if (argc < 4 || strcmp(argv[2], "--") != 0) return 125;
    int directory = open(argv[1], O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (directory < 0) return 125;
    signal(SIGCHLD, SIG_DFL);
    signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL); signal(SIGHUP, SIG_DFL);
    sigset_t blocked;
    sigemptyset(&blocked);
    sigaddset(&blocked, SIGTERM); sigaddset(&blocked, SIGINT);
    sigaddset(&blocked, SIGHUP); sigaddset(&blocked, SIGCHLD);
    pid_t original_parent = getppid();
    if (sigprocmask(SIG_BLOCK, &blocked, NULL) < 0 || prctl(PR_SET_PDEATHSIG, SIGTERM) < 0) return 125;
    if (getppid() != original_parent) raise(SIGTERM);
    int signals = signalfd(-1, &blocked, SFD_CLOEXEC | SFD_NONBLOCK);
    int pipes[2][2];
    capture_stream streams[2] = {0};
    for (int i = 0; i < 2; i++) {
        if (pipe2(pipes[i], O_CLOEXEC) < 0 || fcntl(pipes[i][0], F_SETFL, O_NONBLOCK) < 0) return 125;
        streams[i].input = pipes[i][0];
        streams[i].output = open_artifact(directory, i == 0 ? "stdout.bin" : "stderr.bin");
        if (streams[i].output < 0) return 125;
    }
    if (signals < 0) return 125;
    pid_t supervisor = getpid();
    pid_t child = fork();
    if (child < 0) return 125;
    if (child == 0) execute(argv + 3, supervisor, pipes);
    bool failed = setpgid(child, child) < 0 && errno != EACCES && errno != ESRCH;
    for (int i = 0; i < 2; i++) close(pipes[i][1]);
    bool stopped = false;
    if (!failed) failed = collect_command(child, signals, streams, &stopped) < 0;
    if (failed) { kill(-child, SIGKILL); kill(child, SIGKILL); }
    int status = 0;
    while (waitpid(child, &status, 0) < 0) { if (errno != EINTR) return 125; }
    for (int i = 0; i < 2; i++) {
        if (streams[i].input >= 0) close(streams[i].input);
        if (close(streams[i].output) < 0) failed = true;
    }
    if (failed || save_result(directory, status, stopped, streams) < 0) return 125;
    close(directory); close(signals);
    return stopped ? 124 : WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}
