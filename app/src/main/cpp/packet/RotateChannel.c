#define _GNU_SOURCE
#include "RotateConfig.h"
#include "../frida/fd_channel.h"
#include "../frida/input.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

static int integer(const char *text, uint64_t maximum, uint64_t *value) {
    if (!*text || strlen(text) > 20 || strspn(text, "0123456789") != strlen(text)) return -1;
    errno = 0; char *end; *value = strtoull(text, &end, 10);
    return errno || *end || !*value || *value > maximum ? -1 : 0;
}

static int connect_channel(const char *name, struct aurora_fd_channel *channel) {
    if (strlen(name) != 42 || strncmp(name, "aurora-fd-", 10) || strspn(name + 10, "0123456789abcdef") != 32) return -1;
    struct sockaddr_un address = {.sun_family = AF_UNIX}; memcpy(address.sun_path + 1, name, 42);
    channel->socket = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (channel->socket < 0) return -1;
    socklen_t size = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 43);
    if (!connect(channel->socket, (struct sockaddr *)&address, size)) return 0;
    if (errno != EINPROGRESS || aurora_fd_wait(channel, POLLOUT)) return -1;
    int error = 0; socklen_t length = sizeof(error);
    if (getsockopt(channel->socket, SOL_SOCKET, SO_ERROR, &error, &length)) return -1;
    if (error) { errno = error; return -1; }
    return 0;
}

static int boot_matches(const char *expected) {
    int fd = open("/proc/sys/kernel/random/boot_id", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char boot[37]; ssize_t count = read(fd, boot, sizeof(boot)); close(fd);
    return count != 37 || boot[36] != '\n' || memcmp(boot, expected, 36) ? -1 : 0;
}

int aurora_capture_receive(int argc, char **argv, struct aurora_managed_control *control, struct aurora_capture_config *config) {
    if (argc != 6 || strcmp(argv[1], "capture-channel") || strlen(argv[3]) != 64 || strspn(argv[3], "0123456789abcdef") != 64) return -1;
    uint64_t pid, ticks;
    if (integer(argv[4], INT_MAX, &pid) || integer(argv[5], INT64_MAX, &ticks)) return -1;
    struct aurora_fd_offer offer = {.peer_pid = (pid_t)pid, .peer_uid = 0, .peer_ticks = ticks, .count = 1};
    for (unsigned i = 0; i < 32; i++) {
        char pair[3] = {argv[3][i * 2], argv[3][i * 2 + 1], 0}; offer.nonce[i] = (unsigned char)strtoul(pair, NULL, 16);
    }
    memcpy(offer.ticket, control->ticket, 32);
    struct aurora_fd_channel channel = {.socket = -1, .cancel = control->fd, .peer = -1, .deadline_ms = aurora_fd_now() + 5000};
    int files[2] = {-1, -1}, result = -1;
    if (!connect_channel(argv[2], &channel) && !aurora_fd_receive(&channel, &offer, files) &&
        !aurora_capture_config_fd(files[0], config) && config->root_pid == pid && config->root_ticks == ticks &&
        !boot_matches(config->boot) && !aurora_capture_config_store(files[0])) result = 0;
    if (channel.socket >= 0) close(channel.socket);
    for (unsigned i = 0; i < 2; i++) if (files[i] >= 0) close(files[i]);
    return result;
}

int aurora_capture_report(struct aurora_managed_control *control, int complete, int status) {
    uint64_t ticks;
    if (control->fd < 0 || status < 0 || status > 255 || (complete && status) || (!complete && !status) || frida_process_start(getpid(), &ticks)) return -1;
    unsigned char frame[AURORA_MANAGED_FACT_BYTES] = {0};
    memcpy(frame, "AEX2", 4); frame[4] = 2; frame[5] = (unsigned char)(1 | 128 | (complete ? 64 : 0)); frame[6] = (unsigned char)status;
    memcpy(frame + 8, control->ticket, 32);
    for (unsigned i = 0; i < 4; i++) frame[40 + i] = (unsigned char)((uint32_t)getpid() >> (i * 8));
    for (unsigned i = 0; i < 8; i++) frame[44 + i] = (unsigned char)(ticks >> (i * 8));
    size_t used = 0;
    while (used < sizeof(frame)) {
        ssize_t count = send(control->fd, frame + used, sizeof(frame) - used, MSG_NOSIGNAL | MSG_DONTWAIT);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return -1;
        used += (size_t)count;
    }
    return 0;
}
