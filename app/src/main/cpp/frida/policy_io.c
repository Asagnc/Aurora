#include "policy_io.h"
#if defined(__ANDROID__)
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

static int stable(const struct stat *a, const struct stat *b) {
    return a->st_dev == b->st_dev && a->st_ino == b->st_ino && a->st_uid == b->st_uid && a->st_gid == b->st_gid &&
        a->st_mode == b->st_mode && a->st_nlink == b->st_nlink && a->st_size == b->st_size &&
        a->st_mtim.tv_sec == b->st_mtim.tv_sec && a->st_mtim.tv_nsec == b->st_mtim.tv_nsec &&
        a->st_ctim.tv_sec == b->st_ctim.tv_sec && a->st_ctim.tv_nsec == b->st_ctim.tv_nsec;
}

int aurora_policy_read(const char *path, struct aurora_policy_image *image) {
    if (path[0] != '/') return -1;
    int fd = open(path, O_RDONLY | O_NONBLOCK | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) return -1;
    struct stat info;
    int failed = fstat(fd, &info) || !S_ISREG(info.st_mode) || info.st_uid || info.st_nlink != 1 || (info.st_mode & 0022);
    image->data = failed ? NULL : malloc(AURORA_POLICY_LIMIT + 1);
    if (!image->data) { close(fd); return -1; }
    while (image->size < AURORA_POLICY_LIMIT + 1) {
        ssize_t count = read(fd, (char *)image->data + image->size, AURORA_POLICY_LIMIT + 1 - image->size);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) { failed = 1; break; }
        if (!count) break;
        image->size += (size_t)count;
    }
    struct stat after;
    if (fstat(fd, &after) || !stable(&info, &after) || image->size != (size_t)info.st_size) failed = 1;
    close(fd);
    return !failed && image->size && image->size <= AURORA_POLICY_LIMIT ? 0 : -1;
}

int aurora_policy_write(const char *path, const struct aurora_policy_image *image) {
    if (path[0] != '/') return -1;
    int fd = open(path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (fd < 0) return -1;
    size_t used = 0;
    while (used < image->size) {
        ssize_t count = write(fd, (char *)image->data + used, image->size - used);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { close(fd); return -1; }
        used += (size_t)count;
    }
    int result = fsync(fd);
    close(fd);
    return result;
}

#endif
