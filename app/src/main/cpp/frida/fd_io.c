#define _GNU_SOURCE
#include "fd_channel.h"
#include <errno.h>
#include <limits.h>
#include <poll.h>
#include <sys/socket.h>
#include <time.h>

int64_t aurora_fd_now(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now)) return -1;
    return (int64_t) now.tv_sec * 1000 + now.tv_nsec / 1000000;
}

int aurora_fd_wait(struct aurora_fd_channel *channel, short events) {
    struct pollfd descriptors[] = {{.fd = channel->socket, .events = events},
        {.fd = channel->cancel, .events = POLLIN}, {.fd = channel->peer, .events = POLLIN}};
    for (;;) {
        int64_t now = aurora_fd_now(), remaining = channel->deadline_ms - now;
        if (now < 0) return -1;
        if (remaining <= 0) { errno = ETIMEDOUT; return -1; }
        int result = poll(descriptors, 3, (int) (remaining > INT_MAX ? INT_MAX : remaining));
        if (result < 0 && errno == EINTR) continue;
        if (result < 0) return -1;
        if (!result) { errno = ETIMEDOUT; return -1; }
        if (descriptors[1].revents) { errno = ECANCELED; return -1; }
        if (descriptors[2].revents) { errno = ESRCH; return -1; }
        if (descriptors[0].revents & (events | POLLHUP)) return 0;
        errno = EIO; return -1;
    }
}

int aurora_fd_send(struct aurora_fd_channel *channel, const unsigned char frame[AURORA_FD_FRAME_BYTES]) {
    size_t offset = 0;
    while (offset < AURORA_FD_FRAME_BYTES) {
        if (aurora_fd_wait(channel, POLLOUT)) return -1;
        ssize_t count = send(channel->socket, frame + offset, AURORA_FD_FRAME_BYTES - offset,
            MSG_DONTWAIT | MSG_NOSIGNAL);
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        if (count <= 0) return -1;
        offset += (size_t) count;
    }
    return 0;
}
