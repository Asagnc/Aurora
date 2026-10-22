#define _GNU_SOURCE
#include "scope_state.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

#define RECORD_MAGIC 0x41555343

/* Corruption detector, not an authentication MAC. Root ownership provides the
 * trust boundary; privileged Root programs are not sandboxed by this journal. */
static uint64_t checksum(const struct scope_record *record) {
    const unsigned char *bytes = (const unsigned char *)record;
    uint64_t result = UINT64_C(14695981039346656037);
    for (size_t i = 0; i < sizeof(*record) - sizeof(record->checksum); ++i)
        result = (result ^ bytes[i]) * UINT64_C(1099511628211);
    return result;
}

static int owned(int file, int directory) {
    struct stat info;
    if (fstat(file, &info) < 0) return -1;
    if (info.st_uid != 0 || (info.st_mode & 077) ||
        (directory ? !S_ISDIR(info.st_mode) : (!S_ISREG(info.st_mode) || info.st_nlink != 1))) {
        errno = EPERM; return -1;
    }
    return 0;
}

int scope_state_open(const char *absolute) {
    char parent[8192];
    if (strlen(absolute) >= sizeof(parent) || *absolute != '/') { errno = EINVAL; return -1; }
    strcpy(parent, absolute);
    char *leaf = strrchr(parent, '/');
    if (!leaf || !leaf[1] || !strcmp(leaf + 1, ".") || !strcmp(leaf + 1, "..")) { errno = EINVAL; return -1; }
    *leaf++ = 0;
    int base = scope_open_directory(*parent ? parent : "/");
    if (base < 0) return -1;
    if (mkdirat(base, leaf, 0700) < 0 && errno != EEXIST) { int saved = errno; close(base); errno = saved; return -1; }
    if (fsync(base) < 0) { int saved = errno; close(base); errno = saved; return -1; }
    int directory = openat(base, leaf, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    int saved = errno; close(base); errno = saved;
    if (directory >= 0 && owned(directory, 1) < 0) { saved = errno; close(directory); errno = saved; return -1; }
    return directory;
}

int scope_state_forget(int directory, const char *name) {
    char file[112];
    if (!task_scope_name_valid(name)) { errno = EINVAL; return -1; }
    snprintf(file, sizeof(file), "%s.state", name);
    if (unlinkat(directory, file, 0) < 0) return -1;
    return fsync(directory);
}

int scope_state_save(int directory, const struct task_scope *scope) {
    struct scope_record record = {.magic = RECORD_MAGIC, .version = 1,
        .guard_pid = (uint32_t)getpid(), .bytes = sizeof(record)};
    if (scope_process_start(getpid(), &record.guard_start) < 0 || scope_boot_id(record.boot) < 0 ||
        task_scope_identity(scope, &record.identity) < 0) return -1;
    strcpy(record.name, scope->name); strcpy(record.parent, scope->parent_path);
    record.checksum = checksum(&record);
    char temporary[112], published[112];
    snprintf(temporary, sizeof(temporary), "%s.tmp", scope->name);
    snprintf(published, sizeof(published), "%s.state", scope->name);
    int file = openat(directory, temporary, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (file < 0) return -1;
    size_t offset = 0;
    while (offset < sizeof(record)) {
        ssize_t written = write(file, (const char *)&record + offset, sizeof(record) - offset);
        if (written < 0 && errno == EINTR) continue;
        if (written <= 0) { int saved = errno ? errno : EIO; close(file); errno = saved; return -1; }
        offset += (size_t)written;
    }
    int flushed = fsync(file), saved = errno; close(file); errno = saved;
    if (flushed < 0 || renameat(directory, temporary, directory, published) < 0 || fsync(directory) < 0) return -1;
    return 0;
}

static int read_record(int directory, const char *name, struct scope_record *record) {
    int file = openat(directory, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
    if (file < 0) return -1;
    struct stat info;
    if (owned(file, 0) < 0 || fstat(file, &info) < 0 || info.st_size != (off_t)sizeof(*record)) {
        int saved = errno ? errno : EINVAL; close(file); errno = saved; return -1;
    }
    size_t offset = 0;
    while (offset < sizeof(*record)) {
        ssize_t count = read(file, (char *)record + offset, sizeof(*record) - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { int saved = errno ? errno : EINVAL; close(file); errno = saved; return -1; }
        offset += (size_t)count;
    }
    close(file);
    if (record->checksum != checksum(record) || record->magic != RECORD_MAGIC || record->version != 1 || record->bytes != sizeof(*record) ||
        !record->guard_pid || record->guard_pid > 2147483647U || !record->guard_start ||
        !memchr(record->name, 0, sizeof(record->name)) || !memchr(record->parent, 0, sizeof(record->parent)) ||
        !memchr(record->boot, 0, sizeof(record->boot)) || strlen(record->boot) != 36 ||
        !task_scope_name_valid(record->name) || record->parent[0] != '/') { errno = EINVAL; return -1; }
    char expected[112]; snprintf(expected, sizeof(expected), "%s.state", record->name);
    if (strcmp(expected, name)) { errno = EINVAL; return -1; }
    return 0;
}

static int recover_record(int directory, const char *name, const char *boot) {
    struct scope_record record;
    if (read_record(directory, name, &record) < 0) return -1;
    if (strcmp(record.boot, boot)) return scope_state_forget(directory, record.name);
    int exited = scope_guard_exited(&record);
    if (exited < 0) return -1;
    if (!exited) { errno = EBUSY; return -1; }
    struct task_scope scope;
    int reopened = task_scope_reopen(&scope, record.parent, record.name, &record.identity);
    if (reopened < 0) return -1;
    if (!reopened) {
        if (task_scope_kill(&scope) < 0 || task_scope_wait_empty(&scope, 5000) < 0) {
            int saved = errno; task_scope_close(&scope); errno = saved; return -1;
        }
        if (task_scope_remove(&scope) < 0) return -1;
    }
    return scope_state_forget(directory, record.name);
}

int scope_state_recover(int directory) {
    int lock = openat(directory, ".recovery.lock", O_RDWR | O_CREAT | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (lock < 0) return -1;
    if (owned(lock, 0) < 0 || flock(lock, LOCK_EX | LOCK_NB) < 0) { int saved = errno; close(lock); errno = saved; return -1; }
    char boot[40];
    if (scope_boot_id(boot) < 0) { int saved = errno; close(lock); errno = saved; return -1; }
    /* A fresh descriptor avoids sharing the iteration offset with later calls. */
    int fresh = openat(directory, ".", O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    DIR *entries = fresh < 0 ? NULL : fdopendir(fresh);
    if (!entries) { int saved = errno; if (fresh >= 0) close(fresh); close(lock); errno = saved; return -1; }
    int failed = 0, count = 0;
    for (;;) {
        errno = 0;
        struct dirent *entry = readdir(entries);
        if (!entry) { if (errno) failed = errno; break; }
        size_t length = strlen(entry->d_name);
        if (length < 6 || strcmp(entry->d_name + length - 6, ".state")) continue;
        if (++count > 1024) { failed = E2BIG; break; }
        if (recover_record(directory, entry->d_name, boot) < 0) failed = errno;
    }
    closedir(entries); close(lock);
    if (failed) { errno = failed; return -1; }
    return 0;
}
