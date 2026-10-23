#define _GNU_SOURCE
#include "scope_plan_internal.h"
#include "journal_internal.h"
#include <errno.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define PLAN_HEADER 179
#define PLAN_MAX (PLAN_HEADER + 95 + 8191 + 8)

static void put(unsigned char *bytes, uint64_t value, unsigned count) {
    for (unsigned index = 0; index < count; index++) bytes[index] = (unsigned char)(value >> (index * 8));
}

static uint64_t get(const unsigned char *bytes, unsigned count) {
    uint64_t value = 0;
    for (unsigned index = 0; index < count; index++) value |= (uint64_t)bytes[index] << (index * 8);
    return value;
}

static uint64_t checksum(const unsigned char *bytes, size_t count) {
    uint64_t value = UINT64_C(14695981039346656037);
    for (size_t index = 0; index < count; index++) value = (value ^ bytes[index]) * UINT64_C(1099511628211);
    return value;
}

int managed_plan_store(const struct managed_journal *journal, const struct managed_key *key, const struct task_scope *scope) {
    unsigned char bytes[PLAN_MAX] = {0};
    struct stat parent; uint64_t ticks; char boot[40];
    size_t name = strnlen(scope->name, sizeof(scope->name)), path = strnlen(scope->parent_path, sizeof(scope->parent_path));
    if (!name || name >= sizeof(scope->name) || !path || path >= sizeof(scope->parent_path) ||
        !task_scope_name_valid(scope->name) || scope->parent_path[0] != '/') { errno = EINVAL; return -1; }
    if (managed_key_bytes(key, bytes) || fstat(scope->parent, &parent) ||
        scope_process_start(getpid(), &ticks) || scope_boot_id(boot)) return -1;
    memcpy(bytes + 107, "ASP1", 4); put(bytes + 111, (uint32_t)getpid(), 4); put(bytes + 115, ticks, 8);
    memcpy(bytes + 123, boot, 36); put(bytes + 159, (uint64_t)parent.st_dev, 8); put(bytes + 167, (uint64_t)parent.st_ino, 8);
    put(bytes + 175, name, 2); put(bytes + 177, path, 2);
    memcpy(bytes + PLAN_HEADER, scope->name, name); memcpy(bytes + PLAN_HEADER + name, scope->parent_path, path);
    size_t size = PLAN_HEADER + name + path;
    put(bytes + size, checksum(bytes, size), 8);
    return managed_record_write(journal->directory, "managed.scope-plan", bytes, size + 8, 0);
}

int managed_plan_read(const struct managed_journal *journal, const struct managed_key *key, struct scope_record *record) {
    unsigned char bytes[PLAN_MAX], expected[MANAGED_KEY_BYTES];
    if (managed_key_bytes(key, expected)) return -1;
    int size = managed_record_read(journal->directory, "managed.scope-plan", bytes, sizeof(bytes));
    if (size <= 0) return size;
    if (size < PLAN_HEADER + 8 || memcmp(bytes, expected, sizeof(expected)) || memcmp(bytes + 107, "ASP1", 4)) goto invalid;
    size_t name = (size_t)get(bytes + 175, 2), path = (size_t)get(bytes + 177, 2);
    if (!name || name > 95 || !path || path > 8191 || (size_t)size != PLAN_HEADER + name + path + 8 ||
        get(bytes + size - 8, 8) != checksum(bytes, (size_t)size - 8) ||
        memchr(bytes + PLAN_HEADER, 0, name + path)) goto invalid;
    memset(record, 0, sizeof(*record));
    record->guard_pid = (uint32_t)get(bytes + 111, 4); record->guard_start = get(bytes + 115, 8);
    memcpy(record->boot, bytes + 123, 36);
    record->identity.parent_dev = get(bytes + 159, 8); record->identity.parent_ino = get(bytes + 167, 8);
    memcpy(record->name, bytes + PLAN_HEADER, name); memcpy(record->parent, bytes + PLAN_HEADER + name, path);
    char prefix[48]; snprintf(prefix, sizeof(prefix), "aurora-linux-%u-", record->guard_pid);
    if (!record->guard_pid || record->guard_pid > INT_MAX || !record->guard_start || !record->identity.parent_ino ||
        !managed_uuid(record->boot) || record->parent[0] != '/' || !task_scope_name_valid(record->name) ||
        strncmp(record->name, prefix, strlen(prefix))) goto invalid;
    return 1;
invalid:
    errno = EPROTO; return -1;
}
