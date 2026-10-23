#define _GNU_SOURCE
#include "journal_internal.h"
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <unistd.h>

int managed_owned(int file, int directory) {
    struct stat info;
    if (fstat(file, &info)) return -1;
    if (info.st_uid || (info.st_mode & 077) || (directory ? !S_ISDIR(info.st_mode) :
        (!S_ISREG(info.st_mode) || info.st_nlink != 1))) { errno = EPERM; return -1; }
    return 0;
}

int managed_journal_open(struct managed_journal *journal, int directory) {
    *journal = (struct managed_journal){.directory = -1, .lock = -1};
    if (geteuid()) { errno = EPERM; return -1; }
    if (managed_owned(directory, 1)) return -1;
    int pinned = fcntl(directory, F_DUPFD_CLOEXEC, 3);
    if (pinned < 0) return -1;
    int lock = openat(pinned, ".run.lock", O_RDWR | O_CREAT | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK, 0600);
    if (lock < 0 || managed_owned(lock, 0) || flock(lock, LOCK_EX | LOCK_NB) || fsync(pinned)) {
        int saved = errno; if (lock >= 0) close(lock); close(pinned); errno = saved; return -1;
    }
    *journal = (struct managed_journal){.directory = pinned, .lock = lock};
    return 0;
}

void managed_journal_close(struct managed_journal *journal) {
    if (journal->lock >= 0) close(journal->lock);
    if (journal->directory >= 0) close(journal->directory);
    *journal = (struct managed_journal){.directory = -1, .lock = -1};
}

int managed_record_read(int directory, const char *name, void *buffer, size_t capacity) {
    int file = openat(directory, name, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (file < 0) return errno == ENOENT ? 0 : -1;
    struct stat info;
    if (managed_owned(file, 0) || fstat(file, &info)) { int saved = errno; close(file); errno = saved; return -1; }
    if (info.st_size <= 0 || info.st_size > (off_t)capacity) { close(file); errno = EPROTO; return -1; }
    size_t offset = 0;
    while (offset < (size_t)info.st_size) {
        ssize_t count = read(file, (unsigned char *)buffer + offset, (size_t)info.st_size - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { int saved = count < 0 ? errno : EPROTO; close(file); errno = saved; return -1; }
        offset += (size_t)count;
    }
    unsigned char tail;
    ssize_t extra;
    do { extra = read(file, &tail, 1); } while (extra < 0 && errno == EINTR);
    int saved = errno; close(file);
    if (extra) { errno = extra < 0 ? saved : EPROTO; return -1; }
    return (int)offset;
}

static int sync_existing(int directory, const char *name, const void *bytes, size_t size) {
    unsigned char existing[MANAGED_READY_BYTES];
    int read = managed_record_read(directory, name, existing, sizeof(existing));
    if (read < 0) return -1;
    if (read != (int)size || memcmp(existing, bytes, size)) { errno = EPROTO; return -1; }
    /* A previous identical write may have failed fsync. A retry must not
     * promote merely visible bytes to a durable retirement barrier. */
    int file = openat(directory, name, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (file < 0) return -1;
    int result = managed_owned(file, 0) || fsync(file);
    int saved = errno; close(file); errno = saved;
    return result ? -1 : fsync(directory);
}

int managed_record_write(int directory, const char *name, const void *buffer, size_t size, int idempotent) {
    if (!size || size > MANAGED_READY_BYTES) { errno = EINVAL; return -1; }
    int file = openat(directory, name, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK, 0600);
    if (file < 0) return errno == EEXIST && idempotent ? sync_existing(directory, name, buffer, size) : -1;
    size_t offset = 0;
    while (offset < size) {
        ssize_t count = write(file, (const unsigned char *)buffer + offset, size - offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { int saved = count < 0 ? errno : EIO; close(file); errno = saved; return -1; }
        offset += (size_t)count;
    }
    int synced = fsync(file), saved = errno;
    close(file); errno = saved;
    /* Failed or partial writes stay present and block a new dispatch. */
    return synced ? -1 : fsync(directory);
}
