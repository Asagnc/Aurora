#define _GNU_SOURCE
#include "guard.h"
#include "scope_plan.h"
#include "reap.h"
#include "../linux/scope_state.h"
#include <errno.h>
#include <limits.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>
int managed_spawn_recover(const struct managed_journal *, const struct managed_key *, const struct managed_admission *, unsigned);

static int recovery_failure(const char *stage) {
    int saved = errno;
    fprintf(stderr, "{\"error\":\"managed_recovery_unconfirmed\",\"stage\":\"%s\",\"errno\":%d}\n", stage, saved);
    errno = saved; return -1;
}

static int wait_pidfd(int descriptor, int64_t deadline) {
    for (;;) {
        long long now = task_scope_now(), remaining = deadline - now;
        if (now < 0) return -1;
        struct pollfd event = {.fd = descriptor, .events = POLLIN};
        int result = poll(&event, 1, remaining <= 0 ? 0 : (int)(remaining > INT_MAX ? INT_MAX : remaining));
        if (result < 0 && errno == EINTR) continue;
        if (result < 0) return -1;
        if (!result) return 0;
        if (event.revents & (POLLIN | POLLHUP)) return 1;
        errno = EIO; return -1;
    }
}

static int stop_worker(const struct managed_admission *admission, int64_t deadline) {
    uint64_t ticks;
    /* A reused PID can now identify a non-leader thread, for which pidfd_open
     * returns EINVAL. Check replacement before opening, then check again on
     * the pinned identity before ever signalling it. */
    if (!scope_process_start((pid_t)admission->worker_pid, &ticks) && ticks != admission->worker_ticks) return 0;
    int descriptor = (int)syscall(SYS_pidfd_open, (pid_t)admission->worker_pid, 0);
    if (descriptor < 0) return errno == ESRCH ? 0 : -1;
    if (scope_process_start((pid_t)admission->worker_pid, &ticks)) {
        int saved = errno, exited = wait_pidfd(descriptor, task_scope_now());
        close(descriptor); errno = saved; return exited == 1 ? 0 : -1;
    }
    if (ticks != admission->worker_ticks) { close(descriptor); return 0; }
    long long now = task_scope_now();
    if (now < 0) { close(descriptor); return -1; }
    long long cooperative = now + 15000;
    if (cooperative > deadline - 5000) cooperative = deadline - 5000;
    int exited = wait_pidfd(descriptor, cooperative), result = 0;
    if (exited < 0) result = -1;
    else if (!exited) {
        if (syscall(SYS_pidfd_send_signal, descriptor, SIGKILL, NULL, 0) < 0 && errno != ESRCH) result = -1;
        else if (wait_pidfd(descriptor, deadline) != 1) { errno = ETIMEDOUT; result = -1; }
    }
    int saved = errno; close(descriptor); errno = saved; return result;
}

static int recover_worker(unsigned phase, const struct managed_admission *admission, int64_t deadline) {
    if (!(phase & MANAGED_READY)) return 0;
    char boot[40];
    if (scope_boot_id(boot)) return -1;
    if (strcmp(boot, admission->boot)) return 0; // Never signal a current-boot PID using an old-boot record.
    struct scope_record guard = {.guard_pid = admission->guard_pid, .guard_start = admission->guard_ticks};
    int exited = scope_guard_exited(&guard);
    if (exited < 0) return -1;
    if (!exited) { errno = EBUSY; return -1; }
    return stop_worker(admission, deadline);
}

int managed_guard_recover(const struct managed_journal *journal, const struct managed_key *key, int64_t deadline_ms) {
    if (managed_journal_retire(journal, key)) return recovery_failure("retire");
    struct managed_admission admission;
    int phase = managed_journal_status(journal, key, &admission);
    if (phase < 0) return recovery_failure("journal");
    if (recover_worker((unsigned)phase, &admission, deadline_ms)) return recovery_failure("worker");
    if (scope_state_recover(journal->directory)) return recovery_failure("scope");
    if (managed_scope_plan_recover(journal, key)) return recovery_failure("scope_plan");
    if (managed_spawn_recover(journal, key, &admission, (unsigned)phase)) return recovery_failure("spawn");
    int ignored = -1;
    if (managed_reap_children(0, deadline_ms, &ignored)) return recovery_failure("children");
    struct managed_stopped existing;
    int read = managed_stopped_read(journal, key, &existing);
    if (read < 0) return recovery_failure("stopped_read");
    if (read == 1) return 0;
    struct managed_stopped stopped = {.phase = (unsigned)phase,
        .worker_state = phase & MANAGED_READY ? MANAGED_CLEAR : phase & MANAGED_INTENT ? MANAGED_UNKNOWN : MANAGED_NOT_CREATED,
        .domain_state = phase & MANAGED_INTENT ? MANAGED_CLEAR : MANAGED_NOT_CREATED,
        .exit_code = -1, .reason = MANAGED_RECOVERED};
    return managed_stopped_store(journal, key, &stopped) ? recovery_failure("stopped_store") : 0;
}
