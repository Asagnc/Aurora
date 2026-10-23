#ifndef AURORA_MANAGED_JOURNAL_H
#define AURORA_MANAGED_JOURNAL_H
#include <stdint.h>

enum managed_phase { MANAGED_INTENT = 1, MANAGED_READY = 2, MANAGED_RELEASED = 4, MANAGED_RETIRED = 8 };
struct managed_key { char run_id[37], ticket[65]; };
struct managed_admission { uint32_t guard_pid, worker_pid; uint64_t guard_ticks, worker_ticks; char boot[37]; };
struct managed_journal { int directory, lock; };
/* Root-owned directory, owned regular .run.lock, exclusive nonblocking flock.
 * No functions unlink files. Hold the lock through launch/cleanup/retirement;
 * child workers must close their inherited journal before admission. */
int managed_journal_open(struct managed_journal *journal, int directory);
void managed_journal_close(struct managed_journal *journal);
int managed_key_init(struct managed_key *key, const char *run_id, const char *ticket);
/* Returns phase bits, or -1 for mismatched/corrupt/unsafe files. A retired-only
 * state is valid and blocks a delayed start. No phase means nothing dispatched
 * only while this same lock is held; callers need retirement before recovery. */
int managed_journal_status(const struct managed_journal *journal, const struct managed_key *key,
    struct managed_admission *admission);
int managed_journal_begin(const struct managed_journal *journal, const struct managed_key *key);
int managed_journal_ready(const struct managed_journal *journal, const struct managed_key *key,
    const struct managed_admission *admission);
/* Persist RELEASED before writing GO. Crash between these steps is uncertain,
 * not proof that injection started or did not start. Never replay a release. */
int managed_journal_release(const struct managed_journal *journal, const struct managed_key *key);
/* Idempotent barrier only. Does not prove worker/domain exit or target cleanup. */
int managed_journal_retire(const struct managed_journal *journal, const struct managed_key *key);
#endif
