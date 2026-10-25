#define _GNU_SOURCE
#include "journal.h"
#include "../frida/spawn_record.h"
#include "../linux/scope_state.h"
#include <errno.h>
#include <string.h>

static int nibble(char value) { return value <= '9' ? value - '0' : value - 'a' + 10; }
int managed_spawn_recover(const struct managed_journal *journal, const struct managed_key *key,
    const struct managed_admission *admission, unsigned phase) {
    struct aurora_spawn_record records[5] = {0}; int present[5];
    unsigned char digest[32];
    for (unsigned i = 0; i < 32; i++) digest[i] = (unsigned char)(nibble(key->ticket[i * 2]) * 16 + nibble(key->ticket[i * 2 + 1]));
    for (unsigned i = 0; i < 5; i++) {
        present[i] = aurora_spawn_read(journal->directory, i + 1, &records[i]);
        if (present[i] < 0 || (present[i] && memcmp(records[i].ticket, digest, 32))) return -1;
    }
    if (!present[0]) {
        if (present[1] || present[2] || present[3] || present[4]) { errno = EINVAL; return -1; }
        return 0;
    }
    if (!(phase & MANAGED_READY) || !(phase & MANAGED_RELEASED)) { errno = EINVAL; return -1; }
    if (((present[2] || present[3] || present[4]) && !present[1]) || (present[2] && present[3]) ||
        (present[4] && !present[2])) { errno = EINVAL; return -1; }
    for (unsigned i = 2; i < 5; i++) if (present[i] &&
        (records[i].pid != records[1].pid || records[i].ticks != records[1].ticks)) { errno = EINVAL; return -1; }
    if (!present[1] || present[2] || present[3]) return 0; /* Unknown capture or resume intent: never guess or replay. */
    char boot[40];
    if (scope_boot_id(boot)) return -1;
    if (strcmp(boot, admission->boot)) return 0;
    if (aurora_spawn_terminate(&records[1], 2000)) return -1;
    return aurora_spawn_store(journal->directory, 4, &records[1]);
}
