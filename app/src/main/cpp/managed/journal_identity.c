#define _GNU_SOURCE
#include "journal_internal.h"
#include <errno.h>
#include <limits.h>
#include <string.h>

static int hex(char value) { return (value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'); }

int managed_uuid(const char *text) {
    if (strnlen(text, 37) != 36) return 0;
    for (unsigned index = 0; index < 36; index++) {
        if (index == 8 || index == 13 || index == 18 || index == 23) {
            if (text[index] != '-') return 0;
        } else if (!hex(text[index])) return 0;
    }
    return 1;
}

int managed_key_init(struct managed_key *key, const char *run_id, const char *ticket) {
    if (!managed_uuid(run_id) || strnlen(ticket, 65) != 64) { errno = EINVAL; return -1; }
    for (unsigned index = 0; index < 64; index++) if (!hex(ticket[index])) { errno = EINVAL; return -1; }
    memcpy(key->run_id, run_id, 37); memcpy(key->ticket, ticket, 65);
    return 0;
}

int managed_key_bytes(const struct managed_key *key, unsigned char bytes[MANAGED_KEY_BYTES]) {
    struct managed_key checked;
    if (managed_key_init(&checked, key->run_id, key->ticket)) return -1;
    memcpy(bytes, "ARI1\n", 5); memcpy(bytes + 5, key->run_id, 36);
    bytes[41] = '\n'; memcpy(bytes + 42, key->ticket, 64); bytes[106] = '\n';
    return 0;
}

static int admission_valid(const struct managed_admission *value) {
    return value->guard_pid > 0 && value->guard_pid <= INT_MAX && value->worker_pid > 0 &&
        value->worker_pid <= INT_MAX && value->worker_pid != value->guard_pid &&
        value->guard_ticks > 0 && value->worker_ticks > 0 && managed_uuid(value->boot);
}

static void put(unsigned char *bytes, uint64_t value, unsigned count) {
    for (unsigned index = 0; index < count; index++) bytes[index] = (unsigned char)(value >> (index * 8));
}

static uint64_t get(const unsigned char *bytes, unsigned count) {
    uint64_t value = 0;
    for (unsigned index = 0; index < count; index++) value |= (uint64_t)bytes[index] << (index * 8);
    return value;
}

int managed_admission_encode(const struct managed_key *key, const struct managed_admission *admission,
    unsigned char bytes[MANAGED_READY_BYTES]) {
    unsigned char checked[MANAGED_KEY_BYTES];
    if (managed_key_bytes(key, checked)) return -1;
    if (!admission_valid(admission)) { errno = EINVAL; return -1; }
    memcpy(bytes, "ARW1", 4); memcpy(bytes + 4, key->run_id, 36); memcpy(bytes + 40, key->ticket, 64);
    put(bytes + 104, admission->guard_pid, 4); put(bytes + 108, admission->guard_ticks, 8);
    put(bytes + 116, admission->worker_pid, 4); put(bytes + 120, admission->worker_ticks, 8);
    memcpy(bytes + 128, admission->boot, 36);
    return 0;
}

int managed_admission_decode(const struct managed_key *key, const unsigned char bytes[MANAGED_READY_BYTES],
    struct managed_admission *admission) {
    if (memcmp(bytes, "ARW1", 4) || memcmp(bytes + 4, key->run_id, 36) || memcmp(bytes + 40, key->ticket, 64)) {
        errno = EPROTO; return -1;
    }
    struct managed_admission value = {.guard_pid = (uint32_t)get(bytes + 104, 4), .guard_ticks = get(bytes + 108, 8),
        .worker_pid = (uint32_t)get(bytes + 116, 4), .worker_ticks = get(bytes + 120, 8)};
    memcpy(value.boot, bytes + 128, 36);
    if (!admission_valid(&value)) { errno = EPROTO; return -1; }
    *admission = value; return 0;
}
