#define _GNU_SOURCE
#include "managed_observe.h"
#include <errno.h>
#include <limits.h>
#include <poll.h>
#include <sys/socket.h>
#include <time.h>

static int64_t now_ms(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now)) return -1;
    return (int64_t) now.tv_sec * 1000 + now.tv_nsec / 1000000;
}

static void shorten(int64_t *deadline, int64_t now) {
    if (now + 15000 < *deadline) *deadline = now + 15000;
}

static int request_stop(const struct aurora_managed_observer *observer,
    struct aurora_managed_observation *result, enum aurora_managed_stop reason, int64_t *deadline) {
    if (result->stop != AURORA_STOP_NONE) return 0;
    result->stop = reason;
    int64_t now = now_ms();
    if (now < 0) return -1;
    shorten(deadline, now);
    if (observer->suppress_commands) return 0;
    unsigned char command = reason == AURORA_STOP_DURATION ? 1 : 2;
    ssize_t count;
    do { count = send(observer->socket, &command, 1, MSG_NOSIGNAL | MSG_DONTWAIT); } while (count < 0 && errno == EINTR);
    /* A closed report channel still needs worker/domain observation. Never
     * synthesize a cleanup fact from its closure or resend a stop command. */
    if (count == 1 || (count < 0 && (errno == EPIPE || errno == ECONNRESET))) return 0;
    if (!count) errno = EIO;
    return -1;
}

static int drain(const struct aurora_managed_observer *observer,
    struct aurora_managed_observation *result, int64_t *deadline) {
    int state = aurora_managed_receive(&result->receiver, observer->socket, observer->ticket, &observer->worker);
    if (state < 0) return request_stop(observer, result, AURORA_STOP_PROTOCOL, deadline);
    if (state > 0) {
        int64_t now = now_ms();
        if (now < 0) return -1;
        shorten(deadline, now);
    }
    return 0;
}

int aurora_managed_observe(const struct aurora_managed_observer *observer,
    struct aurora_managed_observation *result) {
    *result = (struct aurora_managed_observation){0};
    if (observer->socket < 0 || observer->owner_pidfd < 0 || observer->worker_pidfd < 0 ||
        !observer->worker.pid || !observer->worker.ticks || observer->operation_deadline_ms <= 0 ||
        observer->force_deadline_ms < observer->operation_deadline_ms ||
        (observer->cancel_enabled && observer->cancel_fd < 0)) { errno = EINVAL; return -1; }
    int64_t deadline = observer->force_deadline_ms;
    for (;;) {
        int64_t now = now_ms();
        if (now < 0) return -1;
        if (now >= deadline) { errno = ETIMEDOUT; return -1; }
        if (!result->stop && now >= observer->operation_deadline_ms &&
            request_stop(observer, result, AURORA_STOP_DURATION, &deadline)) return -1;
        int64_t until = result->stop ? deadline : observer->operation_deadline_ms;
        if (until > deadline) until = deadline;
        struct pollfd events[] = {{.fd = result->receiver.state ? -1 : observer->socket, .events = POLLIN},
            {.fd = result->stop ? -1 : observer->owner_pidfd, .events = POLLIN},
            {.fd = observer->worker_pidfd, .events = POLLIN},
            {.fd = result->stop || !observer->cancel_enabled ? -1 : observer->cancel_fd, .events = POLLIN}};
        int64_t remaining = until - now;
        int ready = poll(events, 4, (int) (remaining > INT_MAX ? INT_MAX : remaining));
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) return -1;
        if (!ready) continue;
        if ((events[0].revents | events[1].revents | events[2].revents | events[3].revents) & POLLNVAL) { errno = EBADF; return -1; }
        if (events[0].revents && drain(observer, result, &deadline)) return -1;
        if (events[2].revents & POLLIN) {
            /* A ready pidfd may race the final bytes/EOF. Drain once even if
             * socket readiness was absent from this particular poll result. */
            if (!result->receiver.state && drain(observer, result, &deadline)) return -1;
            result->worker_signalled = 1; return 0;
        }
        if (events[2].revents) { errno = EIO; return -1; }
        if ((events[1].revents & POLLIN) && request_stop(observer, result, AURORA_STOP_OWNER, &deadline)) return -1;
        if (events[1].revents && !(events[1].revents & POLLIN)) { errno = EIO; return -1; }
        /* Owner cancellation and owner death share the stored owner-stop
         * policy; neither is an injection cleanup fact. */
        if ((events[3].revents & POLLIN) && request_stop(observer, result, AURORA_STOP_OWNER, &deadline)) return -1;
        if (events[3].revents && !(events[3].revents & POLLIN)) { errno = EIO; return -1; }
    }
}
