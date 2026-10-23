#ifndef AURORA_MANAGED_COMPLETION_H
#define AURORA_MANAGED_COMPLETION_H
#include "journal.h"
#include "../frida/managed_receive.h"

/* CLEAR is an exited/replaced worker or absent task domain, not injection
 * cleanup. NOT_CREATED is distinct; UNKNOWN never silently becomes CLEAR. */
enum managed_resource { MANAGED_UNKNOWN, MANAGED_CLEAR, MANAGED_NOT_CREATED };
enum managed_finish_reason { MANAGED_FINISHED, MANAGED_DURATION, MANAGED_OWNER_LOST,
    MANAGED_PROTOCOL_ERROR, MANAGED_RECOVERED, MANAGED_PREPARE_FAILED };
struct managed_stopped {
    unsigned phase, worker_state, domain_state, reason, error;
    int exit_code; // -1 if absent or unavailable to a recovery process.
};
/* Caller holds the journal's task lock. Worker report must have exact validated
 * EOF, not just 52 bytes. Stores before teardown so a later guard crash need
 * not discard already-proven unload facts. Read: 0 absent, 1 valid, -1 unsafe. */
int managed_worker_store(const struct managed_journal *journal, const struct managed_key *key,
    const struct aurora_managed_receiver *receiver);
int managed_worker_read(const struct managed_journal *journal, const struct managed_key *key,
    struct aurora_managed_receipt *receipt);
/* Only after actual resource verification and durable retirement. No mutation
 * of an existing contradictory/partial record. Exact retry re-fsyncs. These
 * records alone do not authorize clearing the App's injection intent. */
int managed_stopped_store(const struct managed_journal *journal, const struct managed_key *key,
    const struct managed_stopped *stopped);
int managed_stopped_read(const struct managed_journal *journal, const struct managed_key *key,
    struct managed_stopped *stopped);
#endif
