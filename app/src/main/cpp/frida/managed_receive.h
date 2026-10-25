#ifndef AURORA_MANAGED_RECEIVE_H
#define AURORA_MANAGED_RECEIVE_H
#include "managed_control.h"
#include <stddef.h>

struct aurora_managed_receiver {
    unsigned char frame[AURORA_MANAGED_FACT_BYTES];
    size_t used;
    int state, error;
    int capture; // Immutable caller-selected protocol, never selected from untrusted frame magic.
    struct aurora_managed_receipt receipt;
};
/* Zero-initialize once per admitted worker. Caller owns the socket and must
 * bind its peer/worker identity before use. Does not wait, close or infer exit.
 * Returns 0 for pending, 1 for exact validated EOF, -1 for sticky failure.
 * Poll only while pending; POLLHUP must still be drained through this function.
 * Caller enforces the absolute task deadline and independently proves domain exit. */
int aurora_managed_receive(struct aurora_managed_receiver *receiver, int socket,
    const unsigned char ticket[32], const struct aurora_managed_worker *worker);
#endif
