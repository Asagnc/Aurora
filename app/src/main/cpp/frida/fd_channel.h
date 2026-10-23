#ifndef AURORA_FRIDA_FD_CHANNEL_H
#define AURORA_FRIDA_FD_CHANNEL_H
#include <stdint.h>
#include <sys/types.h>

#define AURORA_FD_FRAME_BYTES 72
struct aurora_fd_offer {
    pid_t peer_pid;
    uid_t peer_uid;
    uint64_t peer_ticks;
    unsigned char nonce[32], ticket[32];
    unsigned count;
};
struct aurora_fd_channel {
    int socket, cancel, peer;
    int64_t deadline_ms;
};
/* Caller owns socket/cancel. On error all received FDs are closed; output stays -1. */
int aurora_fd_receive(struct aurora_fd_channel *channel, const struct aurora_fd_offer *offer, int output[2]);
int64_t aurora_fd_now(void);
int aurora_fd_wait(struct aurora_fd_channel *channel, short events);
int aurora_fd_send(struct aurora_fd_channel *channel, const unsigned char frame[AURORA_FD_FRAME_BYTES]);
#endif
