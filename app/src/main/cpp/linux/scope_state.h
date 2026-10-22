#ifndef AURORA_SCOPE_STATE_H
#define AURORA_SCOPE_STATE_H
#include "task_scope.h"
#include <sys/types.h>

/* Fixed little-endian 64-bit Android/host format. Corrupt/unknown formats are
 * retained, never interpreted as a pathname-only deletion instruction. */
struct scope_record {
    uint32_t magic, version, guard_pid, bytes;
    uint64_t guard_start;
    struct scope_identity identity;
    char boot[40], name[96], parent[8192];
    uint64_t checksum;
};
_Static_assert(sizeof(struct scope_record) == 8392, "scope record format changed");
int scope_process_start(pid_t pid, uint64_t *start);
int scope_boot_id(char boot[40]);
int scope_guard_exited(const struct scope_record *record);
int scope_state_open(const char *absolute);
int scope_state_save(int directory, const struct task_scope *scope);
int scope_state_forget(int directory, const char *name);
int scope_state_recover(int directory);
#endif
