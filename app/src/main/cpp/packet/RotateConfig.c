#define _GNU_SOURCE
#include "RotateConfig.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/fs.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static uint64_t number(const unsigned char *data, unsigned count) {
    uint64_t value = 0;
    for (unsigned i = 0; i < count; i++) value |= (uint64_t)data[i] << (8 * i);
    return value;
}

static int uuid(const unsigned char *data, char output[37]) {
    for (unsigned i = 0; i < 36; i++) {
        unsigned char c = data[i];
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (c != '-') return -1; }
        else if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return -1;
    }
    memcpy(output, data, 36); output[36] = 0; return 0;
}

static int utf8(const unsigned char *data, size_t size) {
    for (size_t i = 0; i < size;) {
        unsigned char first = data[i++]; unsigned count; uint32_t value, minimum;
        if (first && first < 0x80) continue;
        if (first >= 0xc2 && first <= 0xdf) { count = 1; value = first & 31; minimum = 0x80; }
        else if (first >= 0xe0 && first <= 0xef) { count = 2; value = first & 15; minimum = 0x800; }
        else if (first >= 0xf0 && first <= 0xf4) { count = 3; value = first & 7; minimum = 0x10000; }
        else return -1;
        if (count > size - i) return -1;
        while (count--) {
            unsigned char next = data[i++];
            if ((next & 0xc0) != 0x80) return -1;
            value = value << 6 | (next & 63);
        }
        if (value < minimum || value > 0x10ffff || (value >= 0xd800 && value <= 0xdfff)) return -1;
    }
    return 0;
}

int aurora_capture_config_decode(const unsigned char *data, size_t size, struct aurora_capture_config *out) {
    if (size < 213 || size > 4372 || memcmp(data, "AURC", 4) || number(data + 4, 4) != 1 ||
        number(data + 8, 4) != 212 || number(data + 44, 4) != 262144) goto invalid;
    struct aurora_capture_config value = {.seconds = (unsigned)number(data + 12, 4), .segment_bytes = number(data + 16, 8),
        .bytes = number(data + 24, 8), .packets = number(data + 32, 8), .segments = (unsigned)number(data + 40, 4),
        .root_pid = (unsigned)number(data + 48, 4), .root_ticks = number(data + 52, 8)};
    if (!value.seconds || value.seconds > 1800 || value.segment_bytes < 40 || value.segment_bytes > 67108864 ||
        value.bytes < value.segment_bytes || value.bytes > 536870912 || !value.packets || value.packets > 10000000 ||
        !value.segments || value.segments > 64 || !value.root_pid || value.root_pid > INT_MAX ||
        !value.root_ticks || value.root_ticks > INT64_MAX) goto invalid;
    if (uuid(data + 60, value.run) || uuid(data + 96, value.owner) || uuid(data + 132, value.boot) || uuid(data + 168, value.epoch)) goto invalid;
    uint64_t device = number(data + 204, 4), filter = number(data + 208, 4);
    if (!device || device > 64 || filter > 4096 || 212 + device + filter != size || utf8(data + 212 + device, (size_t)filter)) goto invalid;
    memcpy(value.device, data + 212, (size_t)device);
    if (strspn(value.device, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.:@-") != device) goto invalid;
    memcpy(value.filter, data + 212 + device, (size_t)filter);
    *out = value; return 0;
invalid:
    errno = EINVAL; return -1;
}

int aurora_capture_config_fd(int fd, struct aurora_capture_config *config) {
    struct stat before, after; int seals = fcntl(fd, F_GET_SEALS);
    const int required = F_SEAL_SEAL | F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK;
    if (seals < 0 || (seals & required) != required || fstat(fd, &before) || !S_ISREG(before.st_mode) ||
        before.st_size < 213 || before.st_size > 4372) { errno = EINVAL; return -1; }
    unsigned char data[4372]; size_t used = 0;
    while (used < (size_t)before.st_size) {
        ssize_t count = pread(fd, data + used, (size_t)before.st_size - used, (off_t)used);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) return -1;
        used += (size_t)count;
    }
    if (fstat(fd, &after) || before.st_size != after.st_size || before.st_ino != after.st_ino || before.st_dev != after.st_dev ||
        fcntl(fd, F_GET_SEALS) != seals) { errno = ESTALE; return -1; }
    return aurora_capture_config_decode(data, used, config);
}

int aurora_capture_config_store(int fd) {
    struct aurora_capture_config config; struct stat info, directory;
    if (geteuid() || aurora_capture_config_fd(fd, &config) || fstat(fd, &info)) return -1;
    int root = open(".", O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (root < 0) return -1;
    int output = -1, result = -1;
    if (fstat(root, &directory) || !S_ISDIR(directory.st_mode) || directory.st_uid || (directory.st_mode & 0777) != 0700) goto finish;
    output = openat(root, "capture.config.partial", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (output < 0) goto finish;
    unsigned char data[4372]; size_t used = 0;
    while (used < (size_t)info.st_size) {
        ssize_t count = pread(fd, data + used, (size_t)info.st_size - used, (off_t)used);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) goto finish;
        used += (size_t)count;
    }
    used = 0;
    while (used < (size_t)info.st_size) {
        ssize_t count = write(output, data + used, (size_t)info.st_size - used);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) goto finish;
        used += (size_t)count;
    }
    if (fchmod(output, 0400) || fsync(output)) goto finish;
    if (close(output)) { output = -1; goto finish; }
    output = -1;
    if (syscall(SYS_renameat2, root, "capture.config.partial", root, "capture.config", RENAME_NOREPLACE) || fsync(root)) goto finish;
    result = 0;
finish:
    if (output >= 0) close(output);
    close(root); return result;
}
