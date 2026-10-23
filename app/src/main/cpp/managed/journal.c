#define _GNU_SOURCE
#include "journal_internal.h"
#include <errno.h>
#include <string.h>

static int key_record(const struct managed_journal *journal, const char *name, const unsigned char *expected) {
    unsigned char bytes[MANAGED_KEY_BYTES];
    int size = managed_record_read(journal->directory, name, bytes, sizeof(bytes));
    if (size <= 0) return size;
    if (size != (int)sizeof(bytes) || memcmp(bytes, expected, sizeof(bytes))) { errno = EPROTO; return -1; }
    return 1;
}

int managed_journal_status(const struct managed_journal *journal, const struct managed_key *key,
    struct managed_admission *admission) {
    unsigned char expected[MANAGED_KEY_BYTES], ready[MANAGED_READY_BYTES];
    if (admission) *admission = (struct managed_admission){0};
    if (managed_key_bytes(key, expected)) return -1;
    int intent = key_record(journal, "managed.intent", expected);
    if (intent < 0) return -1;
    int retired = key_record(journal, "managed.retired", expected);
    if (retired < 0) return -1;
    int released = key_record(journal, "managed.released", expected);
    if (released < 0) return -1;
    int size = managed_record_read(journal->directory, "managed.ready", ready, sizeof(ready));
    if (size < 0) return -1;
    struct managed_admission value = {0};
    if (size && (size != (int)sizeof(ready) || managed_admission_decode(key, ready, &value))) { errno = EPROTO; return -1; }
    if ((size && !intent) || (released && !size)) { errno = EPROTO; return -1; }
    if (admission) *admission = value;
    return (intent ? MANAGED_INTENT : 0) | (size ? MANAGED_READY : 0) |
        (released ? MANAGED_RELEASED : 0) | (retired ? MANAGED_RETIRED : 0);
}

int managed_journal_begin(const struct managed_journal *journal, const struct managed_key *key) {
    int phase = managed_journal_status(journal, key, NULL);
    if (phase < 0) return -1;
    if (phase) { errno = phase & MANAGED_RETIRED ? ECANCELED : EEXIST; return -1; }
    unsigned char extra[MANAGED_MAX_RECORD_BYTES];
    int worker = managed_record_read(journal->directory, "managed.worker", extra, sizeof(extra));
    if (worker < 0) return -1;
    int stopped = managed_record_read(journal->directory, "managed.stopped", extra, sizeof(extra));
    if (stopped < 0) return -1;
    int plan = managed_record_read(journal->directory, "managed.scope-plan", extra, sizeof(extra));
    if (plan < 0) return -1;
    if (worker || stopped || plan) { errno = EPROTO; return -1; }
    unsigned char bytes[MANAGED_KEY_BYTES];
    if (managed_key_bytes(key, bytes)) return -1;
    return managed_record_write(journal->directory, "managed.intent", bytes, sizeof(bytes), 0);
}

int managed_journal_ready(const struct managed_journal *journal, const struct managed_key *key,
    const struct managed_admission *admission) {
    int phase = managed_journal_status(journal, key, NULL);
    if (phase < 0) return -1;
    if (phase & MANAGED_RETIRED) { errno = ECANCELED; return -1; }
    if (phase != MANAGED_INTENT) { errno = phase ? EEXIST : EPROTO; return -1; }
    unsigned char bytes[MANAGED_READY_BYTES];
    if (managed_admission_encode(key, admission, bytes)) return -1;
    return managed_record_write(journal->directory, "managed.ready", bytes, sizeof(bytes), 0);
}

int managed_journal_release(const struct managed_journal *journal, const struct managed_key *key) {
    int phase = managed_journal_status(journal, key, NULL);
    if (phase < 0) return -1;
    if (phase & MANAGED_RETIRED) { errno = ECANCELED; return -1; }
    if (phase != (MANAGED_INTENT | MANAGED_READY)) { errno = phase & MANAGED_RELEASED ? EEXIST : EPROTO; return -1; }
    unsigned char bytes[MANAGED_KEY_BYTES];
    if (managed_key_bytes(key, bytes)) return -1;
    return managed_record_write(journal->directory, "managed.released", bytes, sizeof(bytes), 0);
}

int managed_journal_retire(const struct managed_journal *journal, const struct managed_key *key) {
    if (managed_journal_status(journal, key, NULL) < 0) return -1;
    unsigned char bytes[MANAGED_KEY_BYTES];
    if (managed_key_bytes(key, bytes)) return -1;
    return managed_record_write(journal->directory, "managed.retired", bytes, sizeof(bytes), 1);
}
