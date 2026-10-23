#define _GNU_SOURCE
#include "scope_plan_internal.h"
#include "journal_internal.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <unistd.h>

int managed_scope_create(const struct managed_journal *journal, const struct managed_key *key, struct task_scope *scope) {
    *scope = (struct task_scope){.parent = -1, .directory = -1, .events = -1, .processes = -1, .kill = -1};
    int phase = managed_journal_status(journal, key, NULL);
    if (phase < 0) return -1;
    if (phase != MANAGED_INTENT) { errno = phase & MANAGED_RETIRED ? ECANCELED : EPROTO; return -1; }
    if (task_scope_reserve(scope)) return -1;
    if (managed_plan_store(journal, key, scope) || task_scope_materialize(scope) || scope_state_save(journal->directory, scope)) {
        int saved = errno; task_scope_close(scope); errno = saved; return -1;
    }
    return 0;
}

static int forget(const struct managed_journal *journal) {
    if (unlinkat(journal->directory, "managed.scope-plan", 0)) return -1;
    return fsync(journal->directory);
}

int managed_scope_plan_forget(const struct managed_journal *journal, const struct managed_key *key) {
    struct scope_record record;
    int result = managed_plan_read(journal, key, &record);
    if (result <= 0) return result;
    return forget(journal);
}

static int remove_partial(const struct managed_journal *journal, const struct scope_record *record) {
    char name[112]; snprintf(name, sizeof(name), "%s.tmp", record->name);
    int file = openat(journal->directory, name, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (file < 0) return errno == ENOENT ? 0 : -1;
    int valid = managed_owned(file, 0), saved = errno;
    close(file); errno = saved;
    if (valid || unlinkat(journal->directory, name, 0)) return -1;
    return fsync(journal->directory);
}

static int remove_empty_plan(const struct scope_record *record) {
    struct task_scope scope = {.parent = -1, .directory = -1, .events = -1, .processes = -1, .kill = -1};
    strcpy(scope.name, record->name); strcpy(scope.parent_path, record->parent);
    scope.parent = scope_open_directory(record->parent);
    struct stat parent, child; struct statfs filesystem;
    if (scope.parent < 0 || fstat(scope.parent, &parent) || fstatfs(scope.parent, &filesystem)) goto failed;
    if (filesystem.f_type != 0x63677270 || (uint64_t)parent.st_dev != record->identity.parent_dev ||
        (uint64_t)parent.st_ino != record->identity.parent_ino) { errno = ESTALE; goto failed; }
    scope.directory = openat(scope.parent, record->name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (scope.directory < 0 && errno == ENOENT) { task_scope_close(&scope); return 0; }
    if (scope.directory < 0 || fstat(scope.directory, &child)) goto failed;
    if (child.st_uid || !S_ISDIR(child.st_mode)) { errno = EPERM; goto failed; }
    scope.events = openat(scope.directory, "cgroup.events", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (scope.events < 0) goto failed;
    int populated = task_scope_populated(&scope);
    if (populated < 0) goto failed;
    if (populated) { errno = EBUSY; goto failed; }
    return task_scope_remove(&scope);
failed: {
    int saved = errno; task_scope_close(&scope); errno = saved; return -1;
}
}

int managed_scope_plan_recover(const struct managed_journal *journal, const struct managed_key *key) {
    struct scope_record record;
    int result = managed_plan_read(journal, key, &record);
    if (result <= 0) return result;
    char boot[40];
    if (scope_boot_id(boot)) return -1;
    if (!strcmp(record.boot, boot)) {
        int exited = scope_guard_exited(&record);
        if (exited < 0) return -1;
        if (!exited) { errno = EBUSY; return -1; }
        if (remove_empty_plan(&record)) return -1;
    }
    if (remove_partial(journal, &record)) return -1;
    return forget(journal);
}
