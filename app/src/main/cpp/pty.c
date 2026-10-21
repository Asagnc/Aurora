#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <sys/signalfd.h>
#include <sys/wait.h>
#include <termios.h>
#include <time.h>
#include <unistd.h>

/* Framed stdin: byte opcode + little-endian uint16 length + payload (<=4096).
 * 1 writes literal bytes; 2 sets uint16 rows,columns; 3 closes the session.
 * stdout is unmodified merged PTY bytes. stderr belongs to the supervisor. */
typedef struct { unsigned char data[65536]; size_t used; } queue;
static void consume(queue *q, size_t n) { q->used -= n; memmove(q->data, q->data + n, q->used); }
static uint16_t u16(const unsigned char *p) { return (uint16_t)(p[0] | (p[1] << 8)); }
static int nonblock(int fd) { return fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK); }
static int64_t millis(void) {
    struct timespec now;
    clock_gettime(CLOCK_BOOTTIME, &now);
    return (int64_t)now.tv_sec * 1000 + now.tv_nsec / 1000000;
}

static void kill_session(pid_t child) {
    /* Include shell job-control groups in this session; a deliberately detached
     * new session remains outside this execution contract, as with command_execute. */
    DIR *proc = opendir("/proc");
    if (proc) {
        struct dirent *entry;
        while ((entry = readdir(proc))) {
            char *end;
            long pid = strtol(entry->d_name, &end, 10);
            if (*entry->d_name && !*end && pid > 1 && getsid((pid_t)pid) == child) kill((pid_t)pid, SIGKILL);
        }
        closedir(proc);
    }
    kill(-child, SIGKILL);
    kill(child, SIGKILL);
}

static void spawn(int master, const char *slave, char **argv, pid_t parent) {
    sigset_t empty;
    sigemptyset(&empty);
    sigprocmask(SIG_SETMASK, &empty, NULL);
    signal(SIGPIPE, SIG_DFL);
    if (setsid() < 0 || prctl(PR_SET_PDEATHSIG, SIGKILL) < 0 || getppid() != parent) _exit(126);
    int fd = open(slave, O_RDWR);
    if (fd < 0 || ioctl(fd, TIOCSCTTY, 0) < 0) _exit(126);
    for (int i = 0; i < 3; i++) if (dup2(fd, i) < 0) _exit(126);
    if (fd > 2) close(fd);
    close(master);
    execvp(argv[0], argv);
    int failure = errno;
    perror("Aurora PTY exec");
    _exit(failure == ENOENT ? 127 : 126);
}

static int frames(queue *control, queue *input, int master) {
    while (control->used >= 3) {
        unsigned char op = control->data[0];
        size_t length = u16(control->data + 1);
        if (length > 4096) return -1;
        if (control->used < length + 3) break;
        const unsigned char *body = control->data + 3;
        if (op == 1) {
            if (input->used + length > sizeof(input->data)) break;
            memcpy(input->data + input->used, body, length);
            input->used += length;
        } else if (op == 2 && length == 4) {
            struct winsize size = { .ws_row = u16(body), .ws_col = u16(body + 2) };
            if (size.ws_row < 2 || size.ws_row > 200 || size.ws_col < 2 || size.ws_col > 300 ||
                ioctl(master, TIOCSWINSZ, &size) < 0) return -1;
        } else return -1;
        consume(control, length + 3);
    }
    return 0;
}

static int pump(int master, int signals, pid_t child, int64_t deadline) {
    queue control = {0}, input = {0}, output = {0};
    int status = 0, exited = 0, eof = 0, stopped = 0;
    const char *reason = "exit";
    int64_t drain_until = 0;
    for (;;) {
        siginfo_t info = {0};
        if (!exited && waitid(P_PID, (id_t)child, &info, WEXITED | WNOHANG | WNOWAIT) == 0 && info.si_pid == child) {
            kill_session(child);
            waitpid(child, &status, 0);
            exited = 1; drain_until = millis() + 1000;
        }
        if ((exited && (!output.used && eof)) || (exited && millis() >= drain_until)) break;
        if (millis() >= deadline) { stopped = 1; reason = "deadline"; break; }
        struct pollfd fds[4] = {
            { STDIN_FILENO, control.used < sizeof(control.data) ? POLLIN : 0, 0 },
            { ((!eof && output.used < sizeof(output.data)) || input.used) ? master : -1,
                (short)((!eof && output.used < sizeof(output.data) ? POLLIN : 0) | (input.used ? POLLOUT : 0)), 0 },
            { STDOUT_FILENO, output.used ? POLLOUT : 0, 0 }, { signals, POLLIN, 0 }
        };
        int ready = poll(fds, 4, 100);
        if (ready < 0) { if (errno == EINTR) continue; stopped = 1; break; }
        if (fds[3].revents & POLLIN) {
            struct signalfd_siginfo info;
            while (read(signals, &info, sizeof(info)) == sizeof(info))
                if (info.ssi_signo != SIGCHLD) stopped = 1;
            if (stopped) break;
        }
        if (fds[0].revents & (POLLIN | POLLHUP)) {
            ssize_t n = read(0, control.data + control.used, sizeof(control.data) - control.used);
            if (!n) { stopped = 1; break; }
            if (n > 0) control.used += (size_t)n;
        }
        if (frames(&control, &input, master) < 0) { stopped = 1; break; }
        if (output.used < sizeof(output.data) && (fds[1].revents & (POLLIN | POLLHUP))) {
            ssize_t n = read(master, output.data + output.used, sizeof(output.data) - output.used);
            if (n > 0) output.used += (size_t)n;
            else if (!n || (n < 0 && errno == EIO)) { eof = 1; input.used = 0; }
        }
        if (input.used && (fds[1].revents & POLLOUT)) {
            ssize_t n = write(master, input.data, input.used);
            if (n > 0) consume(&input, (size_t)n);
        }
        if (output.used && (fds[2].revents & POLLOUT)) {
            ssize_t n = write(1, output.data, output.used);
            if (n > 0) consume(&output, (size_t)n);
        }
        if (fds[2].revents & (POLLERR | POLLHUP | POLLNVAL)) { stopped = 1; break; }
    }
    if (!exited) {
        kill_session(child);
        while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
    }
    if (stopped && !strcmp(reason, "exit")) reason = "stopped";
    if (!stopped && (output.used || !eof)) reason = "output_incomplete";
    fprintf(stderr, "{\"protocol\":1,\"exitCode\":%d,\"reason\":\"%s\"}",
        WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status), reason);
    return stopped ? 124 : WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}

int main(int argc, char **argv) {
    if (argc < 6 || strcmp(argv[4], "--")) return 125;
    int rows = atoi(argv[1]), columns = atoi(argv[2]), timeout = atoi(argv[3]);
    if (rows < 2 || rows > 200 || columns < 2 || columns > 300 || timeout < 1 || timeout > 3600000) return 125;
    signal(SIGCHLD, SIG_DFL); signal(SIGPIPE, SIG_IGN);
    signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL); signal(SIGHUP, SIG_DFL);
    sigset_t blocked;
    sigemptyset(&blocked);
    sigaddset(&blocked, SIGTERM); sigaddset(&blocked, SIGINT); sigaddset(&blocked, SIGHUP); sigaddset(&blocked, SIGCHLD);
    pid_t parent = getppid();
    if (sigprocmask(SIG_BLOCK, &blocked, NULL) < 0 || prctl(PR_SET_PDEATHSIG, SIGTERM) < 0) return 125;
    if (getppid() != parent) raise(SIGTERM);
    int signals = signalfd(-1, &blocked, SFD_CLOEXEC | SFD_NONBLOCK);
    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    char slave[128];
    if (signals < 0 || master < 0 || grantpt(master) || unlockpt(master) || ptsname_r(master, slave, sizeof(slave))) return 125;
    struct termios mode;
    if (tcgetattr(master, &mode) < 0) return 125;
    mode.c_iflag = (mode.c_iflag | IUTF8) & ~(IXON | IXOFF);
    tcsetattr(master, TCSANOW, &mode);
    struct winsize size = { .ws_row = (unsigned short)rows, .ws_col = (unsigned short)columns };
    if (ioctl(master, TIOCSWINSZ, &size) < 0) return 125;
    pid_t self = getpid(), child = fork();
    if (child < 0) return 125;
    if (!child) spawn(master, slave, argv + 5, self);
    if (nonblock(master) < 0 || nonblock(0) < 0 || nonblock(1) < 0) { kill_session(child); return 125; }
    int result = pump(master, signals, child, millis() + timeout);
    close(master); close(signals);
    return result;
}
