#ifndef AURORA_SPAWN_RECORD_H
#define AURORA_SPAWN_RECORD_H
#include <stdint.h>
/* Immutable phase files, each bound to the intent; no overwrite or automatic spawn replay. */
struct aurora_spawn_record { unsigned char ticket[32]; uint32_t pid; uint64_t ticks; };
int aurora_spawn_store(int directory, unsigned phase, const struct aurora_spawn_record *record);
int aurora_spawn_read(int directory, unsigned phase, struct aurora_spawn_record *record);
int aurora_spawn_terminate(const struct aurora_spawn_record *record, int timeout_ms);
#endif
