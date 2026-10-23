#include "managed_control.h"
#include <errno.h>
#include <string.h>

static uint64_t little(const unsigned char *input, unsigned bytes) {
    uint64_t value = 0;
    for (unsigned index = 0; index < bytes; index++) value |= (uint64_t) input[index] << (index * 8);
    return value;
}

int aurora_managed_decode(const unsigned char frame[AURORA_MANAGED_FACT_BYTES], const unsigned char ticket[32],
    const struct aurora_managed_worker *worker, struct aurora_managed_receipt *receipt) {
    unsigned flags = frame[5], status = frame[6];
    const unsigned clean = AURORA_FACT_UNLOADED | AURORA_FACT_DETACHED | AURORA_FACT_MANAGER_CLOSED;
    int success = (flags & AURORA_FACT_SUCCESS) != 0;
    if (memcmp(frame, "AEX1", 4) || frame[4] != 1 || frame[7] || flags > 127 || !(flags & AURORA_FACT_FINAL) ||
        memcmp(frame + 8, ticket, 32) || !worker->pid || !worker->ticks ||
        little(frame + 40, 4) != worker->pid || little(frame + 44, 8) != worker->ticks ||
        ((flags & AURORA_FACT_LOADED) && !(flags & AURORA_FACT_ATTACH_ATTEMPTED)) ||
        (success && (status || (flags & clean) != clean || !(flags & AURORA_FACT_LOADED))) ||
        (!success && !status)) { errno = EPROTO; return -1; }
    *receipt = (struct aurora_managed_receipt){flags, status};
    return 0;
}
