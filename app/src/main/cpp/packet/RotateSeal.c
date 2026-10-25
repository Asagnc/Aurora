#define _GNU_SOURCE
#include "RotateStore.h"
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

static int record(const struct aurora_capture_store *store, char output[1024]) {
    const struct aurora_capture_config *config = store->config;
    const struct aurora_capture_seal *seal = &store->current;
    /* All strings are validated fixed UUID/hex fields, never user expressions. Kernel counters remain unknown here. */
    return snprintf(output, 1024,
        "{\"protocol\":1,\"binding\":{\"runId\":\"%s\",\"owner\":\"%s\",\"identity\":{\"pid\":%u,\"startTicks\":%" PRIu64
        ",\"bootId\":\"%s\",\"epoch\":\"%s\"}},\"ticketSha256\":\"%s\",\"startedBootMs\":%" PRIu64 ",\"segment\":{"
        "\"ordinal\":%u,\"device\":%" PRIu64 ",\"inode\":%" PRIu64 ",\"bytes\":%" PRIu64 ",\"packets\":%" PRIu64
        ",\"snapshotTruncated\":%" PRIu64 ",\"sha256\":\"%s\",\"linkType\":%u,\"openedBootMs\":%" PRIu64
        ",\"sealedBootMs\":%" PRIu64 ",\"kernel\":{\"received\":null,\"dropped\":null,\"interfaceDropped\":null}}}\n",
        config->run, config->owner, config->root_pid, config->root_ticks, config->boot, config->epoch, store->ticket, store->started,
        seal->ordinal, (uint64_t)seal->physical.st_dev, (uint64_t)seal->physical.st_ino, seal->bytes, seal->packets,
        seal->truncated, seal->digest, seal->link_type, seal->opened, seal->sealed);
}

int aurora_capture_register(struct aurora_capture_store *store) {
    char data[1024], temporary[32], final[32];
    if (strlen(store->ticket) != 64 || strspn(store->ticket, "0123456789abcdef") != 64) return -1;
    int size = record(store, data);
    if (size <= 0 || size >= (int)sizeof(data)) return -1;
    snprintf(temporary, sizeof(temporary), "capture-%03u.sealing", store->segments);
    snprintf(final, sizeof(final), "capture-%03u.seal", store->segments);
    int fd = openat(store->directory, temporary, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd < 0) return -1;
    int used = 0, result = -1;
    while (used < size) {
        ssize_t count = write(fd, data + used, (size_t)(size - used));
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) goto finish;
        used += (int)count;
    }
    if (fchmod(fd, 0400) || fsync(fd)) goto finish;
    if (close(fd)) { fd = -1; goto finish; }
    fd = -1;
    if (syscall(SYS_renameat2, store->directory, temporary, store->directory, final, RENAME_NOREPLACE) || fsync(store->directory)) goto finish;
    result = 0;
finish:
    if (fd >= 0) close(fd);
    return result;
}
