#define _GNU_SOURCE
#include "managed_control.h"
#include "input.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static int ticket_decode(const char *text, unsigned char output[32]) {
    if (!text || strlen(text) != 64 || strspn(text, "0123456789abcdef") != 64) return -1;
    for (unsigned index = 0; index < 32; index++) {
        char byte[3] = {text[index * 2], text[index * 2 + 1], 0};
        output[index] = (unsigned char) strtoul(byte, NULL, 16);
    }
    return 0;
}

int aurora_managed_open(struct aurora_managed_control *control) {
    *control = (struct aurora_managed_control){.fd = -1};
    const char *descriptor = getenv("AURORA_MANAGED_FD"), *ticket = getenv("AURORA_MANAGED_TICKET");
    if (!descriptor && !ticket) return 0;
    if (!descriptor || !*descriptor || strspn(descriptor, "0123456789") != strlen(descriptor) || ticket_decode(ticket, control->ticket)) {
        errno = EINVAL; return -1;
    }
    errno = 0; char *end;
    unsigned long fd = strtoul(descriptor, &end, 10);
    if (errno || *end || fd < 3 || fd > INT_MAX) { errno = EINVAL; return -1; }
    int type, flags = fcntl((int) fd, F_GETFD), options = fcntl((int) fd, F_GETFL);
    socklen_t length = sizeof(type);
    struct ucred peer; socklen_t peer_length = sizeof(peer);
    if (flags < 0 || options < 0 || getsockopt((int) fd, SOL_SOCKET, SO_TYPE, &type, &length) || type != SOCK_STREAM ||
        getsockopt((int) fd, SOL_SOCKET, SO_PEERCRED, &peer, &peer_length) || peer_length != sizeof(peer) ||
        peer.uid != 0 || peer.pid != getppid() || fcntl((int) fd, F_SETFD, flags | FD_CLOEXEC) < 0 ||
        fcntl((int) fd, F_SETFL, options | O_NONBLOCK) < 0) { errno = EPERM; return -1; }
    control->fd = (int) fd;
    unsetenv("AURORA_MANAGED_FD"); unsetenv("AURORA_MANAGED_TICKET");
    return 0;
}

static void little(unsigned char *output, uint64_t value, unsigned bytes) {
    for (unsigned index = 0; index < bytes; index++) output[index] = (unsigned char) (value >> (index * 8));
}

int aurora_managed_report(struct aurora_managed_control *control, const struct aurora_managed_facts *facts, int status) {
    if (control->fd < 0) return 0;
    if (!(facts->flags & AURORA_FACT_FINAL) || facts->flags > 127 || status < 0 || status > 255) return -1;
    uint64_t ticks;
    if (frida_process_start(getpid(), &ticks)) return -1;
    unsigned char frame[AURORA_MANAGED_FACT_BYTES] = {0};
    memcpy(frame, "AEX1", 4); frame[4] = 1; frame[5] = (unsigned char) facts->flags; frame[6] = (unsigned char) status;
    memcpy(frame + 8, control->ticket, 32);
    little(frame + 40, (uint32_t) getpid(), 4); little(frame + 44, ticks, 8);
    size_t offset = 0;
    while (offset < sizeof(frame)) {
        ssize_t count = send(control->fd, frame + offset, sizeof(frame) - offset, MSG_NOSIGNAL | MSG_DONTWAIT);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return -1;
        offset += (size_t) count;
    }
    return 0;
}

void aurora_managed_close(struct aurora_managed_control *control) {
    if (control->fd >= 0) close(control->fd);
    control->fd = -1;
}
