#define _GNU_SOURCE
#include "spawn_record.h"
#include "input.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

static void filename(char name[32], unsigned phase) { snprintf(name, 32, "spawn.%u", phase); }
static void encode(unsigned char bytes[64], unsigned phase, const struct aurora_spawn_record *record) {
    memset(bytes, 0, 64); memcpy(bytes, "ASP2", 4); bytes[4] = (unsigned char)phase;
    memcpy(bytes + 8, record->ticket, 32);
    for (unsigned i = 0; i < 4; i++) bytes[40 + i] = (unsigned char)(record->pid >> (8 * i));
    for (unsigned i = 0; i < 8; i++) bytes[44 + i] = (unsigned char)(record->ticks >> (8 * i));
}
int aurora_spawn_store(int directory, unsigned phase, const struct aurora_spawn_record *record) {
    if (phase < 1 || phase > 5 || (phase == 1 ? record->pid || record->ticks : !record->pid || !record->ticks)) { errno = EINVAL; return -1; }
    char name[32]; filename(name, phase);
    int fd = openat(directory, name, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd < 0) return -1;
    unsigned char bytes[64]; encode(bytes, phase, record);
    size_t used = 0; int result = -1;
    while (used < sizeof(bytes)) {
        ssize_t count = write(fd, bytes + used, sizeof(bytes) - used);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) goto done;
        used += (size_t)count;
    }
    if (!fsync(fd) && !fsync(directory)) result = 0;
done:
    close(fd); return result; /* Partial records stay visible and block unsafe recovery. */
}
int aurora_spawn_read(int directory, unsigned phase, struct aurora_spawn_record *record) {
    char name[32]; filename(name, phase);
    int fd = openat(directory, name, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (fd < 0) return errno == ENOENT ? 0 : -1;
    struct stat info; unsigned char bytes[65]; size_t used = 0; int result = -1;
    if (fstat(fd, &info) || !S_ISREG(info.st_mode) || info.st_uid != geteuid() ||
        info.st_nlink != 1 || (info.st_mode & 077) || info.st_size != 64) goto done;
    while (used < sizeof(bytes)) {
        ssize_t count = read(fd, bytes + used, sizeof(bytes) - used);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) goto done;
        if (!count) break;
        used += (size_t)count;
    }
    if (used != 64 || memcmp(bytes, "ASP2", 4) || bytes[4] != phase) goto done;
    memset(record, 0, sizeof(*record)); memcpy(record->ticket, bytes + 8, 32);
    for (unsigned i = 0; i < 4; i++) record->pid |= (uint32_t)bytes[40 + i] << (8 * i);
    for (unsigned i = 0; i < 8; i++) record->ticks |= (uint64_t)bytes[44 + i] << (8 * i);
    unsigned char canonical[64]; encode(canonical, phase, record);
    if (memcmp(canonical, bytes, 64) || (phase == 1 ? record->pid || record->ticks : !record->pid || !record->ticks)) goto done;
    result = 1;
done:
    close(fd); if (result < 0) errno = EINVAL; return result;
}
int aurora_spawn_terminate(const struct aurora_spawn_record *record, int timeout_ms) {
    int fd = (int)syscall(SYS_pidfd_open, record->pid, 0);
    if (fd < 0) return errno == ESRCH ? 0 : -1;
    struct pollfd event = {.fd = fd, .events = POLLIN};
    uint64_t ticks; int result = -1;
    if (poll(&event, 1, 0) == 1) result = 0;
    else if (!frida_process_start((pid_t)record->pid, &ticks)) {
        if (ticks != record->ticks) result = 0; /* Exact original exited; never signal replacement. */
        else if (!syscall(SYS_pidfd_send_signal, fd, SIGKILL, NULL, 0) || errno == ESRCH) {
            int ready; do { ready = poll(&event, 1, timeout_ms); } while (ready < 0 && errno == EINTR);
            if (ready == 1 && event.revents & (POLLIN | POLLHUP)) result = 0;
        }
    }
    close(fd); return result;
}
