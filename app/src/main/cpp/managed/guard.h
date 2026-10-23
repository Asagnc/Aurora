#ifndef AURORA_MANAGED_GUARD_H
#define AURORA_MANAGED_GUARD_H
#include "completion.h"
struct managed_guard_spec {
    const struct managed_journal *journal;
    const struct managed_key *key;
    char *const *argv;
    unsigned observe_ms;
    int64_t started_ms;
    int cancel_fd;
};
int managed_guard_execute(const struct managed_guard_spec *spec, int owner_pidfd);
int managed_guard_recover(const struct managed_journal *journal, const struct managed_key *key, int64_t deadline_ms);
int managed_guard_summary(const struct managed_journal *journal, const struct managed_key *key);
int managed_guard_status(const struct managed_journal *journal, const struct managed_key *key);
#endif
