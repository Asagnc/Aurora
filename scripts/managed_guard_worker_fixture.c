/* Native guard protocol fixture. All injection/cleanup flags are simulated. */
#define _GNU_SOURCE
#include "managed_control.h"
#include "input.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>

static int marker(const char *path, pid_t pid) {
    int file = open(path, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (file < 0) return -1;
    int result = dprintf(file, "%ld\n", (long)pid) < 0;
    close(file); return result ? -1 : 0;
}

static int helper(const char *mode, int control, const char *path) {
    int ready[2];
    if (pipe2(ready, O_CLOEXEC)) return -1;
    pid_t child = fork();
    if (!child) {
        close(ready[0]); close(control);
        if (setsid() < 0) _exit(80);
        signal(SIGTERM, SIG_IGN);
        if (!strcmp(mode, "escaped")) {
            const char *escape = getenv("AURORA_FIXTURE_ESCAPE");
            int group = escape ? open(escape, O_WRONLY | O_CLOEXEC | O_NOFOLLOW) : -1;
            if (group < 0 || write(group, "0", 1) != 1) _exit(81);
            close(group);
        }
        if (write(ready[1], "r", 1) != 1) _exit(82);
        close(ready[1]); close(0); close(1); close(2);
        for (;;) pause();
    }
    close(ready[1]);
    struct pollfd event = {.fd = ready[0], .events = POLLIN}; unsigned char byte;
    int valid = child > 0 && poll(&event, 1, 3000) == 1 && read(ready[0], &byte, 1) == 1 && byte == 'r';
    close(ready[0]);
    if (!valid) { if (child > 0) { kill(child, SIGKILL); waitpid(child, NULL, 0); } return -1; }
    char name[4096];
    int size = snprintf(name, sizeof(name), "%s.helper", path);
    return size < 0 || (size_t)size >= sizeof(name) ? -1 : marker(name, child);
}

static int wait_stop(int descriptor) {
    for (;;) {
        struct pollfd event = {.fd = descriptor, .events = POLLIN};
        int result = poll(&event, 1, -1);
        if (result < 0 && errno == EINTR) continue;
        if (result < 0) return -1;
        unsigned char byte;
        ssize_t count = recv(descriptor, &byte, 1, MSG_DONTWAIT);
        if (count < 0 && (errno == EAGAIN || errno == EINTR)) continue;
        if (count == 1 && byte == 1) return 0;
        return 1;
    }
}

static int capture_report(struct aurora_managed_control *control, int cancelled) {
    unsigned char frame[AURORA_MANAGED_FACT_BYTES] = {0}; uint64_t ticks;
    if (frida_process_start(getpid(), &ticks)) return -1;
    memcpy(frame, "AEX2", 4); frame[4] = 2; frame[5] = cancelled ? 129 : 193; frame[6] = cancelled ? 1 : 0;
    memcpy(frame + 8, control->ticket, 32);
    for (unsigned i = 0; i < 4; i++) frame[40 + i] = (unsigned char)((uint32_t)getpid() >> (8 * i));
    for (unsigned i = 0; i < 8; i++) frame[44 + i] = (unsigned char)(ticks >> (8 * i));
    return send(control->fd, frame, sizeof(frame), MSG_NOSIGNAL) == (ssize_t)sizeof(frame) ? 0 : -1;
}

int main(int argc, char **argv) {
    int capture = argc == 6 && !strcmp(argv[1], "capture-channel");
    if (!capture && (argc != 5 || strcmp(argv[1], "attach-channel"))) return 64;
    struct aurora_managed_control control;
    if (aurora_managed_open(&control) || control.fd < 3) return 80;
    const char *path = getenv("AURORA_FIXTURE_MARKER");
    if (!path || marker(path, getpid())) return 81;
    if (!strcmp(argv[2], "orphan") || !strcmp(argv[2], "escaped")) {
        if (helper(argv[2], control.fd, path)) return 82;
    }
    if (!strcmp(argv[2], "ignore")) for (;;) pause();
    if (!strcmp(argv[2], "partial")) {
        if (send(control.fd, "AEX1bad", 7, MSG_NOSIGNAL) != 7) return 83;
        aurora_managed_close(&control); return 1;
    }
    int cancelled = !strcmp(argv[2], "wait") ? wait_stop(control.fd) : 0;
    if (cancelled < 0) return 84;
    struct aurora_managed_facts facts = {.flags = cancelled ? 63 : 127};
    int use_capture = (capture && strcmp(argv[2], "wrong-frida")) || !strcmp(argv[2], "wrong-capture");
    if (use_capture ? capture_report(&control, cancelled) : aurora_managed_report(&control, &facts, cancelled ? 1 : 0)) return 74;
    if (!strcmp(argv[2], "frame-alive")) {
        shutdown(control.fd, SHUT_WR);
        for (;;) pause();
    }
    aurora_managed_close(&control);
    return cancelled ? 1 : 0;
}
