#define _GNU_SOURCE
#include "RotateStore.h"
#include "Sha256.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

uint64_t aurora_capture_boot_ms(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_BOOTTIME, &now)) return UINT64_MAX;
    return (uint64_t)now.tv_sec * 1000 + (unsigned)now.tv_nsec / 1000000;
}

static void name(char output[32], unsigned ordinal, const char *suffix) {
    snprintf(output, 32, "capture-%03u.%s", ordinal, suffix);
}

int aurora_capture_store_open(struct aurora_capture_store *store) {
    struct stat directory;
    if (store->failed || store->dump || store->segments >= store->config->segments ||
        store->bytes > store->config->bytes - 24 || fstat(store->directory, &directory) ||
        !S_ISDIR(directory.st_mode) || directory.st_uid || (directory.st_mode & 0777) != 0700) return -1;
    char path[32]; name(path, store->segments, "partial");
    int fd = openat(store->directory, path, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (fd < 0) return -1;
    FILE *file = fdopen(fd, "wb");
    if (!file) { close(fd); return -1; }
    store->dump = pcap_dump_fopen(store->pcap, file);
    if (!store->dump) {
        /* libpcap owns/closed FILE only after trying to write its global header. */
        if (fcntl(fd, F_GETFD) >= 0) fclose(file);
        store->failed = 1; return -1;
    }
    store->current = (struct aurora_capture_seal){.ordinal = store->segments, .bytes = 24, .opened = aurora_capture_boot_ms()};
    if (store->current.opened == UINT64_MAX) { store->failed = 1; return -1; }
    return 0;
}

static int hash_closed(struct aurora_capture_store *store, const char *path) {
    int fd = openat(store->directory, path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) return -1;
    struct aurora_sha256 hash; aurora_sha256_init(&hash);
    unsigned char buffer[65536], digest[32]; uint64_t size = 0; int result = -1; struct stat before, after;
    if (fstat(fd, &before) || before.st_dev != store->current.physical.st_dev || before.st_ino != store->current.physical.st_ino ||
        before.st_size != (off_t)store->current.bytes || before.st_nlink != 1 || before.st_uid || (before.st_mode & 0777) != 0400) goto finish;
    for (;;) {
        ssize_t count = read(fd, buffer, sizeof(buffer));
        if (count < 0 && errno == EINTR) continue;
        if (count < 0 || (uint64_t)count > store->current.bytes - size) goto finish;
        if (!count) break;
        if (!size) {
            if (count < 24) goto finish;
            uint32_t magic; memcpy(&magic, buffer, 4);
            if (magic != 0xa1b2c3d4) goto finish;
            memcpy(&store->current.link_type, buffer + 20, 4);
        }
        size += (uint64_t)count; aurora_sha256_update(&hash, buffer, (size_t)count);
    }
    if (size != store->current.bytes || fstat(fd, &after) || before.st_size != after.st_size ||
        before.st_mtim.tv_sec != after.st_mtim.tv_sec || before.st_mtim.tv_nsec != after.st_mtim.tv_nsec ||
        before.st_ctim.tv_sec != after.st_ctim.tv_sec || before.st_ctim.tv_nsec != after.st_ctim.tv_nsec) goto finish;
    aurora_sha256_finish(&hash, digest);
    for (unsigned i = 0; i < 32; i++) snprintf(store->current.digest + 2 * i, 3, "%02x", digest[i]);
    result = 0;
finish:
    close(fd); return result;
}

int aurora_capture_store_seal(struct aurora_capture_store *store) {
    if (store->failed || !store->dump) return -1;
    FILE *file = pcap_dump_file(store->dump); int fd = fileno(file); struct stat *info = &store->current.physical;
    int failed = pcap_dump_flush(store->dump) < 0 || ferror(file) || fstat(fd, info) || !S_ISREG(info->st_mode) ||
        info->st_nlink != 1 || info->st_uid || info->st_size != (off_t)store->current.bytes || fchmod(fd, 0400) || fsync(fd);
    pcap_dump_close(store->dump); store->dump = NULL;
    char partial[32], sealed[32]; name(partial, store->segments, "partial"); name(sealed, store->segments, "pcap");
    if (failed || syscall(SYS_renameat2, store->directory, partial, store->directory, sealed, RENAME_NOREPLACE) ||
        fsync(store->directory) || hash_closed(store, sealed)) goto failed;
    store->current.sealed = aurora_capture_boot_ms();
    if (store->current.sealed == UINT64_MAX || store->current.sealed < store->current.opened || aurora_capture_register(store)) goto failed;
    store->bytes += store->current.bytes; store->packets += store->current.packets; store->segments++;
    return 0;
failed:
    store->failed = 1; return -1;
}

int aurora_capture_store_packet(struct aurora_capture_store *store, const struct pcap_pkthdr *header, const unsigned char *data) {
    if (store->failed || !store->dump || header->caplen > 262144 || header->caplen > header->len ||
        header->ts.tv_sec < 0 || (uint64_t)header->ts.tv_sec > UINT32_MAX ||
        header->ts.tv_usec < 0 || header->ts.tv_usec >= 1000000) { store->failed = 1; return -1; }
    uint64_t size = 16ULL + header->caplen;
    if (store->packets + store->current.packets >= store->config->packets || size > store->config->segment_bytes - 24) return 1;
    if (size > store->config->segment_bytes - store->current.bytes) {
        if (aurora_capture_store_seal(store)) return -1;
        if (store->segments == store->config->segments || size + 24 > store->config->bytes - store->bytes) return 1;
        if (aurora_capture_store_open(store)) { store->failed = 1; return -1; }
    }
    if (size > store->config->bytes - store->bytes - store->current.bytes) return 1;
    pcap_dump((unsigned char *)store->dump, header, data);
    if (ferror(pcap_dump_file(store->dump))) { store->failed = 1; return -1; }
    store->current.bytes += size; store->current.packets++;
    if (header->caplen < header->len) store->current.truncated++;
    return 0;
}

void aurora_capture_store_abort(struct aurora_capture_store *store) {
    if (store->dump) { pcap_dump_close(store->dump); store->dump = NULL; }
    store->failed = 1;
}
