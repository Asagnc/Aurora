#define _GNU_SOURCE
#include "cancel.h"
#include <errno.h>
#include <signal.h>
#include <sys/socket.h>
#include <unistd.h>

static volatile sig_atomic_t writer = -1;

static void requested(int signal) {
    (void)signal;
    int saved = errno;
    int descriptor = (int)writer;
    if (descriptor >= 0) {
        ssize_t count;
        do { count = write(descriptor, "c", 1); } while (count < 0 && errno == EINTR);
        /* EAGAIN already leaves a readable event; no allocation, logging,
         * locks, blocking write or cleanup inside a signal handler. */
    }
    errno = saved;
}

void managed_cancel_close(int reader) {
    int descriptor = (int)writer;
    writer = -1;
    if (descriptor >= 0) close(descriptor);
    if (reader >= 0) close(reader);
    signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL);
}

int managed_cancel_open(void) {
    if (writer >= 0) { errno = EALREADY; return -1; }
    int channels[2];
    if (socketpair(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0, channels)) return -1;
    struct sigaction action = {.sa_handler = requested};
    sigemptyset(&action.sa_mask); sigaddset(&action.sa_mask, SIGTERM); sigaddset(&action.sa_mask, SIGINT);
    writer = channels[1];
    if (sigaction(SIGTERM, &action, NULL) || sigaction(SIGINT, &action, NULL)) {
        int saved = errno; managed_cancel_close(channels[0]); errno = saved; return -1;
    }
    return channels[0];
}

void managed_cancel_child(void) {
    int descriptor = (int)writer;
    writer = -1;
    if (descriptor >= 0) close(descriptor);
    signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL);
}
