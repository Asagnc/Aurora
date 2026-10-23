#include "guard.h"
#include <errno.h>
#include <stdio.h>

static int outcome(const struct managed_journal *journal, const struct managed_key *key, int print) {
    struct managed_stopped stopped;
    if (managed_stopped_read(journal, key, &stopped) != 1) { if (!errno) errno = EPROTO; return -1; }
    struct aurora_managed_receipt receipt;
    int report = managed_worker_read(journal, key, &receipt);
    if (report < 0) return -1;
    int resources = stopped.worker_state != MANAGED_UNKNOWN && stopped.domain_state != MANAGED_UNKNOWN;
    int success = resources && report == 1 && (receipt.flags & AURORA_FACT_SUCCESS) && stopped.exit_code == 0 &&
        stopped.reason != MANAGED_OWNER_LOST && stopped.reason != MANAGED_PROTOCOL_ERROR && !stopped.error;
    if (print) printf("{\"protocol\":1,\"retired\":true,\"success\":%s,\"resourcesClear\":%s,\"workerState\":%u,\"domainState\":%u,\"exitCode\":%d,\"workerReport\":%s,\"workerFlags\":%u,\"reason\":%u,\"error\":%u}\n",
        success ? "true" : "false", resources ? "true" : "false", stopped.worker_state, stopped.domain_state,
        stopped.exit_code, report == 1 ? "true" : "false", receipt.flags, stopped.reason, stopped.error);
    return success ? 0 : 1;
}

int managed_guard_status(const struct managed_journal *journal, const struct managed_key *key) { return outcome(journal, key, 0); }
int managed_guard_summary(const struct managed_journal *journal, const struct managed_key *key) { return outcome(journal, key, 1); }
