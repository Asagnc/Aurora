#ifndef AURORA_MANAGED_REAP_H
#define AURORA_MANAGED_REAP_H
#include <stdint.h>
#include <sys/types.h>
/* Dedicated single-threaded task subreaper only, SIGCHLD default/no auto-reap.
 * Stops/reaps ONLY this process's direct/adopted children, never a selected
 * existing target App. Each round follows actual pidfd exit events; no timer
 * scan. Bounded rounds/children and absolute deadline; unknown stays failure. */
int managed_reap_children(pid_t worker, int64_t deadline_ms, int *worker_exit);
#endif
