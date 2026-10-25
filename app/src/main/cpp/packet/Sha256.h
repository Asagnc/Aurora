#ifndef AURORA_CAPTURE_SHA256_H
#define AURORA_CAPTURE_SHA256_H
#include <stddef.h>
#include <stdint.h>

struct aurora_sha256 { uint32_t state[8]; uint64_t bytes; unsigned used; unsigned char block[64]; };
void aurora_sha256_init(struct aurora_sha256 *state);
void aurora_sha256_update(struct aurora_sha256 *state, const unsigned char *data, size_t size);
void aurora_sha256_finish(struct aurora_sha256 *state, unsigned char digest[32]);
#endif
