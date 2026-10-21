#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

static int failure(const char *operation) {
    int number = errno;
    fprintf(stderr, "{\"error\":\"root_namespace_unavailable\",\"operation\":\"%s\",\"errno\":%d}\n", operation, number);
    return 126;
}

static int wait_child(pid_t child) {
    int status;
    while (waitpid(child, &status, 0) < 0) { if (errno != EINTR) return 125; }
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}

/* The namespace init exits after its worker, so kernel PID-namespace teardown
 * also kills this deliberately detached, non-cooperating grandchild. */
static int detached_workload(int report, int hold_ms) {
    int ready[2];
    if (pipe2(ready, O_CLOEXEC) < 0) return failure("probe_pipe");
    pid_t child = fork();
    if (child < 0) return failure("probe_fork");
    if (child == 0) {
        close(ready[0]); close(report);
        if (setsid() < 0) _exit(125);
        signal(SIGTERM, SIG_IGN);
        if (write(ready[1], "r", 1) != 1) _exit(125);
        close(ready[1]);
        close(STDIN_FILENO); close(STDOUT_FILENO); close(STDERR_FILENO);
        for (;;) pause();
    }
    close(ready[1]);
    struct pollfd descriptor = {.fd = ready[0], .events = POLLIN};
    char marker = 0;
    int complete = poll(&descriptor, 1, 2000) == 1 && read(ready[0], &marker, 1) == 1 && marker == 'r';
    close(ready[0]);
    if (!complete) { errno = ETIMEDOUT; return failure("detached_worker_readiness"); }
    /* Report only after the detached child has completed setsid(). */
    if (write(report, "ready\n", 6) != 6) return 125;
    if (hold_ms) {
        struct pollfd delay = {.fd = -1};
        while (poll(&delay, 0, hold_ms) < 0) { if (errno != EINTR) return 125; }
    }
    return 0;
}

static __attribute__((noreturn)) void namespace_init(int lifetime, int report, ino_t previous_pid_ns, int hold_ms) {
    signal(SIGCHLD, SIG_DFL);
    if (prctl(PR_SET_PDEATHSIG, SIGKILL) < 0) _exit(failure("init_parent_death"));
    /* Parent can die before PR_SET_PDEATHSIG. Only it owns the pipe writer;
     * nonblocking EOF closes that race even though getppid() is 0 in PID1. */
    char byte;
    ssize_t live = read(lifetime, &byte, 1);
    close(lifetime);
    if (live != -1 || errno != EAGAIN) { errno = EPIPE; _exit(failure("parent_lifetime")); }
    struct stat identity;
    if (getpid() != 1) { errno = EINVAL; _exit(failure("namespace_init_pid")); }
    if (stat("/proc/self/ns/pid", &identity) < 0) _exit(failure("child_pid_namespace_identity"));
    if (identity.st_ino == previous_pid_ns) { errno = EINVAL; _exit(failure("unchanged_pid_namespace")); }
    pid_t worker = fork();
    if (worker < 0) _exit(failure("worker_fork"));
    if (worker == 0) _exit(detached_workload(report, hold_ms));
    close(report);
    /* Exiting PID1 causes the kernel to kill and reap remaining members,
     * including descendants outside the original process group/session. */
    _exit(wait_child(worker));
}

int main(int argc, char **argv) {
    int hold_ms = 0;
    if (argc != 2 && argc != 4) return 64;
    if (strcmp(argv[1], "--probe") != 0) return 64;
    if (argc == 4) {
        if (strcmp(argv[2], "--hold-ms") != 0) return 64;
        char *end;
        long value = strtol(argv[3], &end, 10);
        if (!*argv[3] || *end || value < 1 || value > 5000) return 64;
        hold_ms = (int)value;
    }
    if (geteuid() != 0) { errno = EPERM; return failure("root_required"); }
    pid_t parent = getppid();
    if (prctl(PR_SET_PDEATHSIG, SIGKILL) < 0 || getppid() != parent) return failure("parent_death");
    struct stat old_mount, old_pid, new_mount;
    if (stat("/proc/self/ns/mnt", &old_mount) < 0 || stat("/proc/self/ns/pid", &old_pid) < 0) return failure("namespace_identity");
    if (unshare(CLONE_NEWNS | CLONE_NEWPID) < 0) return failure("unshare");
    if (mount(NULL, "/", NULL, MS_REC | MS_PRIVATE, NULL) < 0) return failure("private_propagation");
    if (stat("/proc/self/ns/mnt", &new_mount) < 0 || old_mount.st_ino == new_mount.st_ino) return 125;
    int lifetime[2], report[2];
    if (pipe2(lifetime, O_CLOEXEC | O_NONBLOCK) < 0 || pipe2(report, O_CLOEXEC) < 0) return failure("parent_pipe");
    pid_t init = fork();
    if (init < 0) return failure("init_fork");
    if (init == 0) {
        close(lifetime[1]); close(report[0]);
        namespace_init(lifetime[0], report[1], old_pid.st_ino, hold_ms);
    }
    close(lifetime[0]); close(report[1]);
    char ready[6];
    ssize_t bytes;
    do { bytes = read(report[0], ready, sizeof(ready)); } while (bytes < 0 && errno == EINTR);
    close(report[0]);
    int code = wait_child(init);
    close(lifetime[1]);
    if (code != 0 || bytes != 6 || memcmp(ready, "ready\n", 6) != 0) return code ? code : 125;
    puts("{\"protocol\":1,\"privateMountNamespace\":true,\"privatePidNamespace\":true,\"detachedWorkerStarted\":true,\"namespaceInitReaped\":true}");
    return 0;
}
