#define _GNU_SOURCE
#include "scope_gc.h"
#include "scope_state.h"
#include "guest_run.h"
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <linux/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static int owned_directory(int fd) {
    struct stat info;
    if (fstat(fd, &info) < 0) return -1;
    if (!S_ISDIR(info.st_mode) || info.st_uid != 0 || (info.st_mode & 077)) { errno = EPERM; return -1; }
    return 0;
}

static int same_mount(int left, int right) {
    struct statx a, b;
    if (syscall(SYS_statx, left, "", AT_EMPTY_PATH, STATX_MNT_ID, &a) < 0 ||
        syscall(SYS_statx, right, "", AT_EMPTY_PATH, STATX_MNT_ID, &b) < 0 ||
        !(a.stx_mask & b.stx_mask & STATX_MNT_ID) || a.stx_mnt_id != b.stx_mnt_id) { errno = EXDEV; return -1; }
    return 0;
}

static int uuid(const char *name) {
    if (strlen(name) != 36) return 0;
    for (int i = 0; i < 36; i++) {
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (name[i] != '-') return 0; }
        else if (!((name[i] >= '0' && name[i] <= '9') || (name[i] >= 'a' && name[i] <= 'f'))) return 0;
    }
    return 1;
}

static int known(const char *name, int journals) {
    const char *files[] = {"ticket.json", "retired", "stopped", "run.json", "completed.json", ".run.lock"};
    for (unsigned i = 0; i < sizeof(files) / sizeof(files[0]); i++) if (!strcmp(name, files[i])) return 1;
    size_t size = strlen(name);
    return journals && size > 6 && !strcmp(name + size - 6, ".state");
}

/* Preflight the entire small flat directory before removing a single file. */
static int inspect(int state, int journals, int deleting) {
    struct statx base;
    if (syscall(SYS_statx, state, "", AT_EMPTY_PATH, STATX_MNT_ID, &base) < 0 || !(base.stx_mask & STATX_MNT_ID)) return -1;
    int copy = openat(state, ".", O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (copy < 0) return -1;
    DIR *entries = fdopendir(copy);
    if (!entries) { close(copy); return -1; }
    struct dirent *entry;
    int result = 0, count = 0;
    errno = 0;
    while ((entry = readdir(entries))) {
        if (!strcmp(entry->d_name, ".") || !strcmp(entry->d_name, "..")) continue;
        struct stat info;
        struct statx item;
        if (++count > 32 || !known(entry->d_name, journals) ||
            fstatat(state, entry->d_name, &info, AT_SYMLINK_NOFOLLOW) < 0 || !S_ISREG(info.st_mode) ||
            info.st_uid != 0 || (info.st_mode & 022) || info.st_nlink != 1 || info.st_size > 262144 ||
            syscall(SYS_statx, state, entry->d_name, AT_SYMLINK_NOFOLLOW, STATX_MNT_ID, &item) < 0 ||
            !(item.stx_mask & STATX_MNT_ID) || item.stx_mnt_id != base.stx_mnt_id) {
            errno = EPERM; result = -1; break;
        }
        if (deleting && unlinkat(state, entry->d_name, 0) < 0) { result = -1; break; }
        errno = 0;
    }
    if (errno && result == 0) result = -1;
    int saved = errno; closedir(entries); errno = saved;
    return result;
}

static int stopped(int state) {
    struct stat info;
    if (fstatat(state, "stopped", &info, AT_SYMLINK_NOFOLLOW) < 0 || !S_ISREG(info.st_mode) ||
        info.st_uid != 0 || info.st_nlink != 1 || info.st_size != 0 || (info.st_mode & 022)) return -1;
    return fstatat(state, "ticket.json", &info, AT_SYMLINK_NOFOLLOW);
}

/* Root Java verifies old epoch and exact ticket/SQL exclusion before admission.
 * Keep native flock from scope proof through rename/unlink. Quarantine makes
 * every interrupted deletion resumable without interpreting an empty run as new. */
int scope_gc(const char *absolute, const char *trash) {
    char parent[8192];
    if (geteuid() != 0 || strlen(absolute) >= sizeof(parent) || absolute[0] != '/') { errno = EINVAL; return -1; }
    strcpy(parent, absolute);
    char *name = strrchr(parent, '/');
    if (!name || !uuid(name + 1)) { errno = EINVAL; return -1; }
    *name++ = 0;
    int source = scope_open_directory(parent), state = -1, lock = -1, target = -1, gc_lock = -1, result = -1;
    if (source < 0 || owned_directory(source) < 0) goto done;
    target = trash ? scope_open_directory(trash) : dup(source);
    if (target < 0 || owned_directory(target) < 0) goto done;
    /* This parent inode is never removed: serialize collectors even during the
     * final unlink of a per-run lock and rmdir, across old/new service processes. */
    gc_lock = guest_run_lock(target);
    if (gc_lock < 0 || same_mount(target, gc_lock) < 0) goto done;
    state = openat(source, name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (state < 0 || owned_directory(state) < 0 || same_mount(source, state) < 0 || inspect(state, trash != NULL, 0) < 0) goto done;
    if (trash && stopped(state) < 0) goto done;
    lock = guest_run_lock(state);
    if (lock < 0 || scope_state_recover(state) < 0 || inspect(state, 0, 0) < 0) goto done;
    if (trash) {
        struct stat existing;
        if (fstatat(target, name, &existing, AT_SYMLINK_NOFOLLOW) == 0 || errno != ENOENT) { errno = EEXIST; goto done; }
        if (renameat(source, name, target, name) < 0 || fsync(source) < 0 || fsync(target) < 0) goto done;
    }
    if (inspect(state, 0, 1) < 0 || fsync(state) < 0 ||
        unlinkat(target, name, AT_REMOVEDIR) < 0 || fsync(target) < 0) goto done;
    result = 0;
done:;
    int saved = errno;
    if (gc_lock >= 0) close(gc_lock);
    if (target >= 0) close(target);
    if (lock >= 0) close(lock);
    if (state >= 0) close(state);
    if (source >= 0) close(source);
    errno = saved; return result;
}
