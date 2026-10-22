#define _GNU_SOURCE
#include "task_scope.h"
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <unistd.h>

int task_scope_name_valid(const char *name) {
    if (strncmp(name, "aurora-linux-", 13)) return 0;
    const char *pid = name + 13;
    size_t digits = strspn(pid, "0123456789");
    if (!digits || digits > 10 || pid[digits] != '-') return 0;
    const char *random = pid + digits + 1;
    return strlen(random) == 32 && strspn(random, "0123456789abcdef") == 32;
}

int task_scope_identity(const struct task_scope *scope, struct scope_identity *identity) {
    struct stat parent, child;
    if (fstat(scope->parent, &parent) < 0 || fstat(scope->directory, &child) < 0) return -1;
    *identity = (struct scope_identity){parent.st_dev, parent.st_ino, child.st_dev, child.st_ino};
    return 0;
}

/* Only recovery of a Root-owned journal may call this. No unverified name is
 * sufficient to kill a group; both pinned parent and scope identities must match. */
int task_scope_reopen(struct task_scope *scope, const char *parent, const char *name, const struct scope_identity *expected) {
    *scope = (struct task_scope){.parent = -1, .directory = -1, .processes = -1, .events = -1, .kill = -1};
    if (!task_scope_name_valid(name) || strlen(parent) >= sizeof(scope->parent_path)) { errno = EINVAL; return -1; }
    strcpy(scope->name, name); strcpy(scope->parent_path, parent);
    scope->parent = scope_open_directory(parent);
    struct stat info; struct statfs fs;
    if (scope->parent < 0 || fstat(scope->parent, &info) < 0 || fstatfs(scope->parent, &fs) < 0) goto failed;
    if (fs.f_type != 0x63677270 || (uint64_t)info.st_dev != expected->parent_dev ||
        (uint64_t)info.st_ino != expected->parent_ino) { errno = ESTALE; goto failed; }
    scope->directory = openat(scope->parent, name, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (scope->directory < 0 && errno == ENOENT) { task_scope_close(scope); return 1; }
    if (scope->directory < 0 || fstat(scope->directory, &info) < 0) goto failed;
    if ((uint64_t)info.st_dev != expected->scope_dev || (uint64_t)info.st_ino != expected->scope_ino) { errno = ESTALE; goto failed; }
    scope->events = openat(scope->directory, "cgroup.events", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    scope->kill = openat(scope->directory, "cgroup.kill", O_WRONLY | O_CLOEXEC | O_NOFOLLOW);
    if (scope->events < 0 || scope->kill < 0) goto failed;
    return 0;
failed: {
    int saved = errno; task_scope_close(scope); errno = saved; return -1;
} }
