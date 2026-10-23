#define _GNU_SOURCE
#include "fd_channel.h"
#include "input.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

struct incoming { unsigned char frame[AURORA_FD_FRAME_BYTES]; size_t used; int files[2]; unsigned count; };

static int peer_open(struct aurora_fd_channel *channel, const struct aurora_fd_offer *offer) {
    struct ucred peer;
    socklen_t length = sizeof(peer);
    int type; socklen_t type_length = sizeof(type);
    if (getsockopt(channel->socket, SOL_SOCKET, SO_TYPE, &type, &type_length) || type != SOCK_STREAM ||
        getsockopt(channel->socket, SOL_SOCKET, SO_PEERCRED, &peer, &length)) return -1;
    if (length != sizeof(peer) || peer.pid != offer->peer_pid || peer.uid != offer->peer_uid) {
        errno = EPERM; return -1;
    }
    channel->peer = (int) syscall(SYS_pidfd_open, peer.pid, 0);
    if (channel->peer < 0) return -1;
    struct frida_attach_input identity = {.pid = peer.pid, .start_ticks = offer->peer_ticks};
    if (!frida_target_matches(&identity)) { errno = ESTALE; return -1; }
    return 0;
}

static void frame_make(unsigned char frame[AURORA_FD_FRAME_BYTES], unsigned char role,
    const struct aurora_fd_offer *offer) {
    memcpy(frame, "AFD1", 4); frame[4] = role; frame[5] = (unsigned char) offer->count;
    frame[6] = frame[7] = 0;
    memcpy(frame + 8, offer->nonce, 32); memcpy(frame + 40, offer->ticket, 32);
}

/* Always inspect/close delivered rights, including when the kernel reports truncation. */
static int rights(struct incoming *incoming, struct msghdr *message, unsigned expected) {
    int invalid = (message->msg_flags & (MSG_CTRUNC | MSG_TRUNC)) != 0;
    unsigned delivered = 0;
    for (struct cmsghdr *control = CMSG_FIRSTHDR(message); control; control = CMSG_NXTHDR(message, control)) {
        if (control->cmsg_level != SOL_SOCKET || control->cmsg_type != SCM_RIGHTS || control->cmsg_len < CMSG_LEN(0)) {
            invalid = 1; continue;
        }
        size_t bytes = control->cmsg_len - CMSG_LEN(0);
        if (bytes % sizeof(int)) invalid = 1;
        for (size_t offset = 0; offset + sizeof(int) <= bytes; offset += sizeof(int)) {
            int fd; memcpy(&fd, CMSG_DATA(control) + offset, sizeof(fd)); delivered++;
            if (!incoming->used && incoming->count < expected && incoming->count < 2)
                incoming->files[incoming->count++] = fd;
            else { close(fd); invalid = 1; }
        }
    }
    if (delivered != (incoming->used ? 0 : expected)) invalid = 1;
    if (invalid) { errno = EPROTO; return -1; }
    return 0;
}

static ssize_t receive(struct aurora_fd_channel *channel, struct incoming *incoming, int tail, unsigned expected) {
    unsigned char extra;
    struct iovec vector = {.iov_base = tail ? &extra : incoming->frame + incoming->used,
        .iov_len = tail ? 1 : AURORA_FD_FRAME_BYTES - incoming->used};
    union { struct cmsghdr align; unsigned char bytes[CMSG_SPACE(8 * sizeof(int))]; } control;
    for (;;) {
        if (aurora_fd_wait(channel, POLLIN)) return -1;
        struct msghdr message = {.msg_iov = &vector, .msg_iovlen = 1,
            .msg_control = control.bytes, .msg_controllen = sizeof(control.bytes)};
        ssize_t count = recvmsg(channel->socket, &message, MSG_CMSG_CLOEXEC | MSG_DONTWAIT);
        if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) continue;
        if (count < 0) return -1;
        if (rights(incoming, &message, tail ? 0 : expected)) return -1;
        return count;
    }
}

static int files_valid(const struct incoming *incoming) {
    struct stat previous = {0};
    for (unsigned index = 0; index < incoming->count; index++) {
        struct stat info;
        int fd = incoming->files[index];
        int seals = fcntl(fd, F_GET_SEALS), flags = fcntl(fd, F_GETFD);
        if (seals < 0 || flags < 0 || fstat(fd, &info) || !S_ISREG(info.st_mode) || info.st_size < 1 || info.st_size > 1048576 ||
            (seals & (F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_SEAL)) !=
                (F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_SEAL) ||
            !(flags & FD_CLOEXEC)) { errno = EPROTO; return -1; }
        if (index && info.st_dev == previous.st_dev && info.st_ino == previous.st_ino) { errno = EPROTO; return -1; }
        previous = info;
    }
    return 0;
}

int aurora_fd_receive(struct aurora_fd_channel *channel, const struct aurora_fd_offer *offer, int output[2]) {
    struct incoming incoming = {.files = {-1, -1}};
    unsigned char expected[AURORA_FD_FRAME_BYTES];
    output[0] = output[1] = -1; channel->peer = -1;
    if (offer->count < 1 || offer->count > 2 || offer->peer_pid <= 0 || !offer->peer_ticks) { errno = EINVAL; return -1; }
    int result = -1;
    if (peer_open(channel, offer)) goto done;
    frame_make(expected, 1, offer);
    if (aurora_fd_send(channel, expected)) goto done;
    while (incoming.used < AURORA_FD_FRAME_BYTES) {
        ssize_t count = receive(channel, &incoming, 0, offer->count);
        if (count <= 0) { if (!count) errno = EPROTO; goto done; }
        incoming.used += (size_t) count;
    }
    frame_make(expected, 2, offer);
    if (memcmp(incoming.frame, expected, sizeof(expected))) { errno = EPROTO; goto done; }
    if (files_valid(&incoming)) goto done;
    ssize_t tail = receive(channel, &incoming, 1, 0);
    if (tail != 0) { if (tail > 0) errno = EPROTO; goto done; }
    frame_make(expected, 3, offer);
    if (aurora_fd_send(channel, expected)) goto done;
    for (unsigned index = 0; index < incoming.count; index++) {
        output[index] = incoming.files[index]; incoming.files[index] = -1;
    }
    result = 0;
done: {
    int saved = errno;
    for (unsigned index = 0; index < 2; index++) if (incoming.files[index] >= 0) close(incoming.files[index]);
    if (channel->peer >= 0) close(channel->peer);
    channel->peer = -1; errno = saved; return result;
} }
