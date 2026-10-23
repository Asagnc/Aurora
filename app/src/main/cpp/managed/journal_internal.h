#ifndef AURORA_MANAGED_JOURNAL_INTERNAL_H
#define AURORA_MANAGED_JOURNAL_INTERNAL_H
#include "journal.h"
#include <stddef.h>
#define MANAGED_KEY_BYTES 107
#define MANAGED_READY_BYTES 164
int managed_owned(int file, int directory);
int managed_record_read(int directory, const char *name, void *buffer, size_t capacity);
int managed_record_write(int directory, const char *name, const void *buffer, size_t size, int idempotent);
int managed_uuid(const char *text);
int managed_key_bytes(const struct managed_key *key, unsigned char bytes[MANAGED_KEY_BYTES]);
int managed_admission_encode(const struct managed_key *key, const struct managed_admission *admission,
    unsigned char bytes[MANAGED_READY_BYTES]);
int managed_admission_decode(const struct managed_key *key, const unsigned char bytes[MANAGED_READY_BYTES],
    struct managed_admission *admission);
#endif
