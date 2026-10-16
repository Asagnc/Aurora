#define _GNU_SOURCE
#include "capture.h"
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <sys/signalfd.h>
#include <sys/wait.h>
#include <unistd.h>
#include <time.h>

#define ARTIFACT_LIMIT (8U * 1024U * 1024U)

static int drain(capture_stream *stream) {
    char buffer[32768];
    /* One bounded read per iteration keeps cancellation responsive under continuous output. */
    ssize_t count = read(stream->input, buffer, sizeof(buffer));
    if (count == 0) {
        close(stream->input);
        stream->input = -1;
        stream->complete = true;
        return 0;
    }
    if (count < 0) return errno == EAGAIN || errno == EINTR ? 0 : -1;
    stream->total += (uint64_t) count;
    size_t keep = (size_t) count;
    if (keep > ARTIFACT_LIMIT - stream->stored) keep = ARTIFACT_LIMIT - stream->stored;
    size_t done = 0;
    while (done < keep) {
        ssize_t written = write(stream->output, buffer + done, keep - done);
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) return -1;
        done += (size_t) written;
    }
    stream->stored += keep;
    return 0;
}

static long long now_ms(void) {
    struct timespec value;
    clock_gettime(CLOCK_MONOTONIC, &value);
    return (long long) value.tv_sec * 1000 + value.tv_nsec / 1000000;
}

int collect_command(int child, int signals, capture_stream streams[2], bool *stopped) {
    bool exited = false;
    long long deadline = 0;
    long long stop_deadline = 0;
    while (streams[0].input >= 0 || streams[1].input >= 0 || !exited) {
        if (exited && now_ms() >= deadline) break;
        int timeout = exited ? 200 : -1;
        if (!exited && stop_deadline > 0) {
            long long remaining = stop_deadline - now_ms();
            if (remaining <= 0) { kill(-child, SIGKILL); stop_deadline = 0; }
            else timeout = (int) remaining;
        }
        struct pollfd descriptors[3] = {
            {.fd = signals, .events = POLLIN},
            {.fd = streams[0].input, .events = POLLIN},
            {.fd = streams[1].input, .events = POLLIN}
        };
        int ready = poll(descriptors, 3, timeout);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) return -1;
        if (ready == 0 && exited) break;
        if (descriptors[0].revents & POLLIN) {
            struct signalfd_siginfo event;
            while (read(signals, &event, sizeof(event)) == sizeof(event)) {
                if (event.ssi_signo != SIGCHLD) {
                    if (!*stopped) {
                        *stopped = true;
                        stop_deadline = now_ms() + 250;
                        kill(-child, SIGTERM);
                    }
                }
            }
        }
        if (!exited) {
            siginfo_t status = {0};
            if (waitid(P_PID, (id_t) child, &status, WEXITED | WNOHANG | WNOWAIT) < 0) return -1;
            if (status.si_pid == child) {
                exited = true;
                deadline = now_ms() + 200;
                /* Keep the leader unreaped until group cleanup to prevent PID reuse. */
                kill(-child, SIGKILL);
            }
        }
        for (int i = 0; i < 2; i++) {
            if (descriptors[i + 1].revents && streams[i].input >= 0 && drain(&streams[i]) < 0) return -1;
        }
    }
    return 0;
}
