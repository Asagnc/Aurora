#define _GNU_SOURCE
#include "completion.h"
#include "journal_internal.h"
#include <errno.h>
#include <stdlib.h>
#include <string.h>

#define STOPPED_BYTES 123

static void ticket_bytes(const struct managed_key *key, unsigned char ticket[32]) {
    for (unsigned index = 0; index < 32; index++) {
        char byte[3] = {key->ticket[index * 2], key->ticket[index * 2 + 1], 0};
        ticket[index] = (unsigned char)strtoul(byte, NULL, 16);
    }
}

static int decode_worker(const struct managed_journal *journal, const struct managed_key *key,
    const unsigned char *frame, struct aurora_managed_receipt *receipt) {
    struct managed_admission admission;
    int phase = managed_journal_status(journal, key, &admission);
    if (phase < 0) return -1;
    if (!(phase & MANAGED_READY)) { errno = EPROTO; return -1; }
    unsigned char ticket[32]; ticket_bytes(key, ticket);
    struct aurora_managed_worker worker = {.pid = admission.worker_pid, .ticks = admission.worker_ticks};
    return aurora_managed_decode(frame, ticket, &worker, receipt);
}

int managed_worker_read(const struct managed_journal *journal, const struct managed_key *key,
    struct aurora_managed_receipt *receipt) {
    *receipt = (struct aurora_managed_receipt){0};
    unsigned char frame[AURORA_MANAGED_FACT_BYTES];
    int size = managed_record_read(journal->directory, "managed.worker", frame, sizeof(frame));
    if (size <= 0) return size;
    if (size != (int)sizeof(frame)) { errno = EPROTO; return -1; }
    if (decode_worker(journal, key, frame, receipt)) return -1;
    return 1;
}

int managed_worker_store(const struct managed_journal *journal, const struct managed_key *key,
    const struct aurora_managed_receiver *receiver) {
    struct aurora_managed_receipt receipt;
    if (receiver->state != 1) { errno = EPROTO; return -1; }
    if (decode_worker(journal, key, receiver->frame, &receipt)) return -1;
    return managed_record_write(journal->directory, "managed.worker", receiver->frame, sizeof(receiver->frame), 1);
}

static int valid_stopped(const struct managed_stopped *stopped, unsigned phase) {
    if (phase != stopped->phase || !(phase & MANAGED_RETIRED) || stopped->worker_state > MANAGED_NOT_CREATED ||
        stopped->domain_state < MANAGED_CLEAR || stopped->domain_state > MANAGED_NOT_CREATED ||
        stopped->reason > MANAGED_PREPARE_FAILED || stopped->error > 4095 || stopped->exit_code < -1 || stopped->exit_code > 255 ||
        (stopped->worker_state != MANAGED_CLEAR && stopped->exit_code != -1) ||
        ((phase & MANAGED_READY) && stopped->worker_state != MANAGED_CLEAR) ||
        ((phase & MANAGED_READY) && stopped->domain_state != MANAGED_CLEAR) ||
        (!(phase & MANAGED_INTENT) && (stopped->worker_state != MANAGED_NOT_CREATED ||
            stopped->domain_state != MANAGED_NOT_CREATED))) { errno = EPROTO; return -1; }
    return 0;
}

static void put32(unsigned char *bytes, uint32_t value) {
    for (unsigned index = 0; index < 4; index++) bytes[index] = (unsigned char)(value >> (index * 8));
}

static uint32_t get32(const unsigned char *bytes) {
    uint32_t value = 0;
    for (unsigned index = 0; index < 4; index++) value |= (uint32_t)bytes[index] << (index * 8);
    return value;
}

int managed_stopped_store(const struct managed_journal *journal, const struct managed_key *key,
    const struct managed_stopped *stopped) {
    int phase = managed_journal_status(journal, key, NULL);
    if (phase < 0 || valid_stopped(stopped, (unsigned)phase)) return -1;
    struct aurora_managed_receipt receipt;
    if (managed_worker_read(journal, key, &receipt) < 0) return -1;
    unsigned char bytes[STOPPED_BYTES] = {0};
    if (managed_key_bytes(key, bytes)) return -1;
    bytes[107] = (unsigned char)phase; bytes[108] = (unsigned char)stopped->worker_state;
    bytes[109] = (unsigned char)stopped->domain_state;
    put32(bytes + 111, stopped->exit_code < 0 ? UINT32_MAX : (uint32_t)stopped->exit_code);
    put32(bytes + 115, stopped->reason); put32(bytes + 119, stopped->error);
    return managed_record_write(journal->directory, "managed.stopped", bytes, sizeof(bytes), 1);
}

int managed_stopped_read(const struct managed_journal *journal, const struct managed_key *key,
    struct managed_stopped *stopped) {
    *stopped = (struct managed_stopped){.exit_code = -1};
    unsigned char bytes[STOPPED_BYTES], expected[MANAGED_KEY_BYTES];
    int phase = managed_journal_status(journal, key, NULL);
    if (phase < 0 || managed_key_bytes(key, expected)) return -1;
    int size = managed_record_read(journal->directory, "managed.stopped", bytes, sizeof(bytes));
    if (size <= 0) return size;
    if (size != (int)sizeof(bytes) || memcmp(bytes, expected, sizeof(expected)) || bytes[110]) { errno = EPROTO; return -1; }
    uint32_t exit_code = get32(bytes + 111);
    if (exit_code != UINT32_MAX && exit_code > 255) { errno = EPROTO; return -1; }
    struct managed_stopped value = {.phase = bytes[107], .worker_state = bytes[108], .domain_state = bytes[109],
        .exit_code = exit_code == UINT32_MAX ? -1 : (int)exit_code, .reason = get32(bytes + 115), .error = get32(bytes + 119)};
    struct aurora_managed_receipt receipt;
    if (valid_stopped(&value, (unsigned)phase) || managed_worker_read(journal, key, &receipt) < 0) return -1;
    *stopped = value; return 1;
}
