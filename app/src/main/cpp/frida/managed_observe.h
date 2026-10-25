#ifndef AURORA_MANAGED_OBSERVE_H
#define AURORA_MANAGED_OBSERVE_H
#include "managed_receive.h"

enum aurora_managed_stop {
    AURORA_STOP_NONE, AURORA_STOP_DURATION, AURORA_STOP_OWNER, AURORA_STOP_PROTOCOL
};
struct aurora_managed_observation {
    struct aurora_managed_receiver receiver;
    enum aurora_managed_stop stop;
    int worker_signalled;
};
struct aurora_managed_observer {
    int socket, owner_pidfd, worker_pidfd;
    struct aurora_managed_worker worker;
    unsigned char ticket[32];
    int64_t operation_deadline_ms, force_deadline_ms;
    int cancel_fd, cancel_enabled;
    int suppress_commands; // Unreleased launcher stub: no Frida engine needs cooperative commands.
    int capture; // Selects AEX2 and CLOCK_BOOTTIME; existing Frida defaults remain AEX1/MONOTONIC.
};
/* Owns no FDs and does not reap, kill, remove a scope or publish durable facts.
 * Parent must capture/admit worker and authenticate the private socket first.
 * 0 means worker pidfd became readable, NOT that injection/domain cleanup passed.
 * -1 requires caller's forced cgroup cleanup. Partial receiver facts are retained.
 * After killing/reaping helpers, caller may drain a pending receiver once more.
 * Caller sets force_deadline before the total task deadline to leave enough
 * time for forced kill/reap/removal; this function does not spend that reserve.
 * Owner loss/deadline/protocol failure requests cooperative stop once, allowing
 * at most 15s (within force deadline) before caller's final forced cleanup window. */
int aurora_managed_observe(const struct aurora_managed_observer *observer,
    struct aurora_managed_observation *result);
#endif
