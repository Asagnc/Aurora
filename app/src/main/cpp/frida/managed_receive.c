#define _GNU_SOURCE
#include "managed_receive.h"
#include <errno.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static int reject(struct aurora_managed_receiver *receiver, int error) {
    receiver->state = -1; receiver->error = error;
    receiver->receipt = (struct aurora_managed_receipt){0};
    errno = error; return -1;
}

static int unexpected_control(struct msghdr *message) {
    int present = message->msg_controllen != 0 || (message->msg_flags & (MSG_CTRUNC | MSG_TRUNC));
    for (struct cmsghdr *item = CMSG_FIRSTHDR(message); item; item = CMSG_NXTHDR(message, item)) {
        if (item->cmsg_level != SOL_SOCKET || item->cmsg_type != SCM_RIGHTS || item->cmsg_len < CMSG_LEN(0)) continue;
        size_t count = (item->cmsg_len - CMSG_LEN(0)) / sizeof(int);
        for (size_t index = 0; index < count; index++) {
            int descriptor;
            memcpy(&descriptor, (unsigned char *) CMSG_DATA(item) + index * sizeof(int), sizeof(int));
            close(descriptor);
        }
    }
    return present;
}

int aurora_managed_receive(struct aurora_managed_receiver *receiver, int socket,
    const unsigned char ticket[32], const struct aurora_managed_worker *worker) {
    if (receiver->state < 0) { errno = receiver->error; return -1; }
    if (receiver->state > 0) return 1;
    for (;;) {
        /* The extra byte detects any tail without allocating or draining an
         * attacker-controlled stream. Even a complete frame stays private until EOF. */
        unsigned char bytes[AURORA_MANAGED_FACT_BYTES + 1];
        union { struct cmsghdr alignment; unsigned char bytes[CMSG_SPACE(sizeof(int) * 16)]; } control;
        struct iovec vector = {.iov_base = bytes, .iov_len = sizeof(bytes) - receiver->used};
        struct msghdr message = {.msg_iov = &vector, .msg_iovlen = 1,
            .msg_control = control.bytes, .msg_controllen = sizeof(control.bytes)};
        ssize_t count = recvmsg(socket, &message, MSG_DONTWAIT | MSG_CMSG_CLOEXEC);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) return 0;
        if (count < 0) return reject(receiver, errno);
        if (unexpected_control(&message)) return reject(receiver, EPROTO);
        if (!count) {
            if (receiver->used != sizeof(receiver->frame) ||
                aurora_managed_decode(receiver->frame, ticket, worker, &receiver->receipt)) return reject(receiver, EPROTO);
            receiver->state = 1; return 1;
        }
        if ((size_t) count > sizeof(receiver->frame) - receiver->used) return reject(receiver, EPROTO);
        memcpy(receiver->frame + receiver->used, bytes, (size_t) count);
        receiver->used += (size_t) count;
    }
}
