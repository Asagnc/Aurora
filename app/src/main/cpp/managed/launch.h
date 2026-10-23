#ifndef AURORA_MANAGED_LAUNCH_H
#define AURORA_MANAGED_LAUNCH_H
#include "journal.h"
#include "../linux/task_scope.h"
#include <sys/types.h>

struct managed_launch_input {
    const struct task_scope *scope;
    const struct managed_journal *journal;
    const struct managed_key *key;
    char *const *argv;
    int owner_pidfd;
    int64_t admission_deadline_ms;
    int cancel_fd, cancel_enabled;
};
struct managed_launch {
    pid_t pid;
    int pidfd, control, gate, prepared, dispatched;
    struct managed_admission admission;
};
/* Single-threaded native guard with default SIGCHLD (no auto-reaping) only.
 * Caller verifies/pins the immutable worker version/path and its normal exec
 * attributes, then owns that lease through real worker/domain exit.
 * Caller already owns the journal lock,
 * persisted INTENT and recorded its newly-created scope before this call.
 * On success the admitted child is still blocked, READY is durable and no
 * worker code has executed. SO_PEERCRED of control binds this native parent.
 * Even on error output may own an unreaped child and FDs: caller must stop its
 * child/scope, reap/remove, then close. This API never proves task cleanup. */
int managed_launch_prepare(const struct managed_launch_input *input, struct managed_launch *launch);
/* One use: durable RELEASED, recheck owner/worker, then GO. Failure after writing
 * RELEASED remains uncertain; never retry GO or replay a launch from this handle. */
int managed_launch_release(const struct managed_launch_input *input, struct managed_launch *launch);
/* Closes handles only, preserves PID identity for the caller's teardown. */
void managed_launch_close(struct managed_launch *launch);
#endif
