#define _GNU_SOURCE
#include "task_scope.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/random.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <time.h>
#include <unistd.h>

#define CGROUP2_MAGIC 0x63677270

long long task_scope_now(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now) < 0) return -1;
    return (long long)now.tv_sec * 1000 + now.tv_nsec / 1000000;
}

/* Walk from a pinned root descriptor; reject symlinks and dot components. */
int scope_open_directory(const char *absolute) {
    if (*absolute != '/') { errno = EINVAL; return -1; }
    char copy[8192];
    if (strlen(absolute) >= sizeof(copy)) { errno = ENAMETOOLONG; return -1; }
    strcpy(copy, absolute);
    int directory = open("/", O_DIRECTORY | O_CLOEXEC);
    char *state, *part = strtok_r(copy, "/", &state);
    while (directory >= 0 && part) {
        if (!strcmp(part, ".") || !strcmp(part, "..")) { close(directory); errno = EINVAL; return -1; }
        int next = openat(directory, part, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
        int saved = errno; close(directory); directory = next; errno = saved;
        part = strtok_r(NULL, "/", &state);
    }
    return directory;
}

static int unescape(char *value) {
    char *read = value, *write = value;
    while (*read) {
        unsigned char byte = (unsigned char)*read++;
        if (byte == '\\') {
            if (strlen(read) < 3 || strspn(read, "01234567") < 3) { errno = EINVAL; return -1; }
            byte = (unsigned char)((read[0] - '0') * 64 + (read[1] - '0') * 8 + read[2] - '0');
            read += 3;
        }
        if (byte < 32 || byte == 127 || byte == '\\') { errno = EINVAL; return -1; }
        *write++ = (char)byte;
    }
    *write = 0;
    return 0;
}

static int self_group(char *path, size_t capacity) {
    FILE *file = fopen("/proc/self/cgroup", "re");
    if (!file) return -1;
    char line[8192]; int found = 0;
    while (fgets(line, sizeof(line), file)) {
        if (!strchr(line, '\n')) { errno = EOVERFLOW; break; }
        if (strncmp(line, "0::", 3)) continue;
        line[strcspn(line, "\n")] = 0;
        if (strlen(line + 3) >= capacity || line[3] != '/') { errno = EINVAL; break; }
        strcpy(path, line + 3); found = 1; break;
    }
    int saved = found ? 0 : (ferror(file) ? EIO : ENOENT);
    fclose(file); errno = saved;
    return found ? 0 : -1;
}

static int current_group(char *path, size_t capacity) {
    char own[8192];
    if (self_group(own, sizeof(own)) < 0) return -1;
    FILE *file = fopen("/proc/self/mountinfo", "re");
    if (!file) return -1;
    char line[16384]; int directory = -1;
    while (fgets(line, sizeof(line), file)) {
        if (!strchr(line, '\n')) break;
        char *separator = strstr(line, " - cgroup2 ");
        if (!separator) continue;
        *separator = 0;
        char *state, *token = strtok_r(line, " ", &state), *root = NULL, *mount = NULL;
        for (int i = 0; token; ++i, token = strtok_r(NULL, " ", &state)) {
            if (i == 3) root = token;
            if (i == 4) { mount = token; break; }
        }
        if (!root || !mount || unescape(root) < 0 || unescape(mount) < 0) continue;
        size_t length = strlen(root);
        const char *relative = own;
        if (strcmp(root, "/")) {
            if (strncmp(own, root, length) || (own[length] && own[length] != '/')) continue;
            relative += length;
        }
        char full[16384];
        int count = snprintf(full, sizeof(full), "%s%s", mount, relative);
        if (count < 0 || (size_t)count >= sizeof(full)) continue;
        if (strlen(full) >= capacity) continue;
        directory = scope_open_directory(full);
        struct statfs fs;
        if (directory >= 0 && fstatfs(directory, &fs) == 0 && fs.f_type == CGROUP2_MAGIC) { strcpy(path, full); break; }
        if (directory >= 0) close(directory);
        directory = -1;
    }
    fclose(file);
    if (directory < 0) errno = ENODEV;
    return directory;
}

void task_scope_close(struct task_scope *scope) {
    int *files[] = {&scope->kill, &scope->events, &scope->processes, &scope->directory, &scope->parent};
    for (unsigned i = 0; i < sizeof(files) / sizeof(*files); ++i) {
        if (*files[i] >= 0) close(*files[i]);
        *files[i] = -1;
    }
}

int task_scope_create(struct task_scope *scope) {
    *scope = (struct task_scope){.parent = -1, .directory = -1, .processes = -1, .events = -1, .kill = -1};
    scope->parent = current_group(scope->parent_path, sizeof(scope->parent_path));
    if (scope->parent < 0) return -1;
    unsigned char random[16];
    ssize_t bytes;
    do { bytes = getrandom(random, sizeof(random), 0); } while (bytes < 0 && errno == EINTR);
    if (bytes != (ssize_t)sizeof(random)) { int saved = errno ? errno : EIO; task_scope_close(scope); errno = saved; return -1; }
    char hex[33];
    for (unsigned i = 0; i < sizeof(random); ++i) snprintf(hex + i * 2, 3, "%02x", random[i]);
    snprintf(scope->name, sizeof(scope->name), "aurora-linux-%ld-%s", (long)getpid(), hex);
    if (mkdirat(scope->parent, scope->name, 0700) < 0) { int saved = errno; task_scope_close(scope); errno = saved; return -1; }
    scope->directory = openat(scope->parent, scope->name, O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (scope->directory >= 0) {
        scope->processes = openat(scope->directory, "cgroup.procs", O_WRONLY | O_CLOEXEC | O_NOFOLLOW);
        scope->events = openat(scope->directory, "cgroup.events", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        scope->kill = openat(scope->directory, "cgroup.kill", O_WRONLY | O_CLOEXEC | O_NOFOLLOW);
    }
    if (scope->directory < 0 || scope->processes < 0 || scope->events < 0 || scope->kill < 0 || task_scope_populated(scope) != 0) {
        int saved = errno ? errno : EBUSY;
        unlinkat(scope->parent, scope->name, AT_REMOVEDIR); task_scope_close(scope); errno = saved; return -1;
    }
    return 0;
}

static int write_value(int descriptor, const char *value) {
    ssize_t written;
    do { written = write(descriptor, value, strlen(value)); } while (written < 0 && errno == EINTR);
    if (written == (ssize_t)strlen(value)) return 0;
    if (written >= 0) errno = EIO;
    return -1;
}

int task_scope_enter(const struct task_scope *scope) { return write_value(scope->processes, "0"); }
int task_scope_kill(const struct task_scope *scope) { return write_value(scope->kill, "1"); }

int task_scope_populated(const struct task_scope *scope) {
    char value[512]; ssize_t size;
    do { size = pread(scope->events, value, sizeof(value) - 1, 0); } while (size < 0 && errno == EINTR);
    if (size <= 0) { if (size == 0) errno = EIO; return -1; }
    value[size] = 0;
    char *field = value;
    while (field && *field) {
        if (!strncmp(field, "populated 0\n", 12)) return 0;
        if (!strncmp(field, "populated 1\n", 12)) return 1;
        field = strchr(field, '\n'); if (field) ++field;
    }
    errno = EINVAL; return -1;
}

int task_scope_wait_empty(const struct task_scope *scope, int timeout_ms) {
    long long deadline = task_scope_now() + timeout_ms;
    for (;;) {
        int populated = task_scope_populated(scope);
        if (populated <= 0) return populated;
        long long left = deadline - task_scope_now();
        if (left <= 0) { errno = ETIMEDOUT; return -1; }
        struct pollfd event = {.fd = scope->events, .events = POLLPRI | POLLERR};
        int result = poll(&event, 1, (int)left);
        if (result < 0 && errno != EINTR) return -1;
        if (event.revents & POLLNVAL) { errno = EBADF; return -1; }
    }
}

int task_scope_remove(struct task_scope *scope) {
    struct stat pinned, named;
    int result = -1;
    if (task_scope_populated(scope) == 0 && fstat(scope->directory, &pinned) == 0 &&
        fstatat(scope->parent, scope->name, &named, AT_SYMLINK_NOFOLLOW) == 0 &&
        pinned.st_dev == named.st_dev && pinned.st_ino == named.st_ino) {
        result = unlinkat(scope->parent, scope->name, AT_REMOVEDIR);
    } else if (!errno) errno = EBUSY;
    int saved = errno; task_scope_close(scope); errno = saved;
    return result;
}
