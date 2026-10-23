#define _GNU_SOURCE
#include "guard.h"
#include "launch.h"
#include "reap.h"
#include "scope_plan.h"
#include "../linux/scope_state.h"
#include "../frida/managed_observe.h"
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <unistd.h>

struct runtime {
    struct task_scope scope;
    struct managed_launch launch;
    struct aurora_managed_observation observation;
    int error;
    unsigned reason;
    int64_t operation, force, total;
};

static void ticket_bytes(const struct managed_key *key, unsigned char ticket[32]) {
    for (unsigned index = 0; index < 32; index++) {
        char byte[3] = {key->ticket[index * 2], key->ticket[index * 2 + 1], 0};
        ticket[index] = (unsigned char)strtoul(byte, NULL, 16);
    }
}

static int observe(const struct managed_guard_spec *spec, int owner, struct runtime *runtime) {
    if (runtime->launch.pidfd < 0 || !runtime->launch.admission.worker_ticks) return 0;
    struct aurora_managed_observer observer = {.socket = runtime->launch.control, .owner_pidfd = owner,
        .worker_pidfd = runtime->launch.pidfd, .worker = {.pid = (uint32_t)runtime->launch.pid,
            .ticks = runtime->launch.admission.worker_ticks}, .operation_deadline_ms = runtime->operation,
        .force_deadline_ms = runtime->force, .cancel_fd = spec->cancel_fd, .cancel_enabled = spec->cancel_fd >= 0,
        .suppress_commands = !runtime->launch.dispatched};
    if (!runtime->launch.dispatched) {
        long long pending = task_scope_now() + 1000;
        if (pending < observer.force_deadline_ms) observer.force_deadline_ms = pending;
        if (observer.force_deadline_ms < observer.operation_deadline_ms)
            observer.operation_deadline_ms = observer.force_deadline_ms;
    }
    ticket_bytes(spec->key, observer.ticket);
    return aurora_managed_observe(&observer, &runtime->observation);
}

static int save_worker(const struct managed_guard_spec *spec, const struct runtime *runtime) {
    if (runtime->observation.receiver.state != 1) return 0;
    int phase = managed_journal_status(spec->journal, spec->key, NULL);
    if (phase < 0) return -1;
    if (!(phase & MANAGED_READY)) return 0; // No GO: retain distinct never-dispatched stage.
    return managed_worker_store(spec->journal, spec->key, &runtime->observation.receiver);
}

static int teardown(const struct managed_guard_spec *spec, struct runtime *runtime) {
    int metadata = managed_journal_retire(spec->journal, spec->key);
    int saved = metadata ? errno : 0;
    int report_saved = runtime->observation.receiver.state == 1;
    if (save_worker(spec, runtime) && !saved) saved = errno;
    if (runtime->launch.pid > 0) kill(runtime->launch.pid, SIGKILL); // Sole, unreaped owned child.
    if (task_scope_kill(&runtime->scope)) return -1;
    long long now = task_scope_now();
    if (now < 0) return -1;
    long long remaining = runtime->total - now;
    int budget = remaining < 0 ? 0 : remaining > 5000 ? 5000 : (int)remaining;
    int exit_code = -1;
    if (task_scope_wait_empty(&runtime->scope, budget) || managed_reap_children(runtime->launch.pid, runtime->total, &exit_code)) return -1;
    /* Helpers may have held an inherited report endpoint until actual teardown. */
    if (!runtime->observation.receiver.state && runtime->launch.control >= 0 && runtime->launch.admission.worker_ticks) {
        struct aurora_managed_worker worker = {.pid = (uint32_t)runtime->launch.pid, .ticks = runtime->launch.admission.worker_ticks};
        unsigned char ticket[32]; ticket_bytes(spec->key, ticket);
        aurora_managed_receive(&runtime->observation.receiver, runtime->launch.control, ticket, &worker);
    }
    if (!report_saved && save_worker(spec, runtime) && !saved) saved = errno;
    if (task_scope_remove(&runtime->scope) || scope_state_forget(spec->journal->directory, runtime->scope.name) ||
        managed_scope_plan_forget(spec->journal, spec->key)) return -1;
    if (saved) { errno = saved; return -1; }
    int phase = managed_journal_status(spec->journal, spec->key, NULL);
    if (phase < 0) return -1;
    struct managed_stopped stopped = {.phase = (unsigned)phase,
        .worker_state = runtime->launch.pid > 0 ? MANAGED_CLEAR : MANAGED_NOT_CREATED,
        .domain_state = MANAGED_CLEAR, .exit_code = exit_code, .reason = runtime->reason, .error = (unsigned)runtime->error};
    return managed_stopped_store(spec->journal, spec->key, &stopped);
}

int managed_guard_execute(const struct managed_guard_spec *spec, int owner_pidfd) {
    if (setsid() < 0 || prctl(PR_SET_CHILD_SUBREAPER, 1)) return 126;
    struct runtime runtime = {.launch = {.pidfd = -1, .control = -1, .gate = -1},
        .operation = spec->started_ms + spec->observe_ms, .force = spec->started_ms + spec->observe_ms + 15000,
        .total = spec->started_ms + spec->observe_ms + 20000};
    if (managed_scope_create(spec->journal, spec->key, &runtime.scope)) return 126; // Coordinator recovers the creation plan.
    struct managed_launch_input input = {.scope = &runtime.scope, .journal = spec->journal, .key = spec->key,
        .argv = spec->argv, .owner_pidfd = owner_pidfd, .admission_deadline_ms = runtime.operation,
        .cancel_fd = spec->cancel_fd, .cancel_enabled = spec->cancel_fd >= 0};
    long long quick = task_scope_now() + 5000;
    if (input.admission_deadline_ms > quick) input.admission_deadline_ms = quick;
    if (task_scope_now() >= runtime.operation) {
        runtime.error = ETIMEDOUT; runtime.reason = MANAGED_DURATION;
    } else if (managed_launch_prepare(&input, &runtime.launch) || managed_launch_release(&input, &runtime.launch)) {
        runtime.error = errno; runtime.reason = MANAGED_PREPARE_FAILED;
    }
    if (runtime.launch.gate >= 0) { close(runtime.launch.gate); runtime.launch.gate = -1; }
    if (observe(spec, owner_pidfd, &runtime) && !runtime.error) runtime.error = errno;
    if (!runtime.reason) runtime.reason = runtime.observation.stop;
    long long cleanup = task_scope_now() + 5000;
    if (cleanup < runtime.total) runtime.total = cleanup;
    int cleaned = teardown(spec, &runtime), saved = errno;
    managed_launch_close(&runtime.launch); errno = saved;
    if (cleaned) return 126;
    return managed_guard_status(spec->journal, spec->key);
}
