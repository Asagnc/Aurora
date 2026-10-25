#define _GNU_SOURCE
#include <pcap/pcap.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/memfd.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/prctl.h>
#include <unistd.h>

static int integer(const char *text, uint64_t low, uint64_t high, uint64_t *out) {
    if (!text[0] || strlen(text) > 20) return -1;
    for (const char *p = text; *p; p++) if (*p < '0' || *p > '9') return -1;
    errno = 0; char *end; *out = strtoull(text, &end, 10);
    return errno || *end || *out < low || *out > high ? -1 : 0;
}

static int snapshot(uint64_t size) {
    int fd = (int)syscall(SYS_memfd_create, "aurora-pcap-input", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (fd < 0) return -1;
    unsigned char buffer[65536]; uint64_t copied = 0;
    while (copied < size) {
        size_t wanted = size - copied < sizeof(buffer) ? (size_t)(size - copied) : sizeof(buffer);
        ssize_t count = read(STDIN_FILENO, buffer, wanted);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) goto failed;
        ssize_t offset = 0;
        while (offset < count) {
            ssize_t sent = write(fd, buffer + offset, (size_t)(count - offset));
            if (sent < 0 && errno == EINTR) continue;
            if (sent <= 0) goto failed;
            offset += sent;
        }
        copied += (uint64_t)count;
    }
    unsigned char extra;
    if (read(STDIN_FILENO, &extra, 1) != 0 ||
        fcntl(fd, F_ADD_SEALS, F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_SEAL) || lseek(fd, 0, SEEK_SET)) goto failed;
    return fd;
failed:
    close(fd); return -1;
}

static int classic_pcap(int fd) {
    unsigned char magic[4];
    if (pread(fd, magic, 4, 0) != 4) return 0;
    return !memcmp(magic, "\xd4\xc3\xb2\xa1", 4) || !memcmp(magic, "\xa1\xb2\xc3\xd4", 4) ||
           !memcmp(magic, "\x4d\x3c\xb2\xa1", 4) || !memcmp(magic, "\xa1\xb2\x3c\x4d", 4);
}

int select_pcap(int argc, char **argv) {
    uint64_t source_size, first, count, budget;
    if (argc != 6 || integer(argv[2], 24, 33554432, &source_size) || integer(argv[3], 0, 1000000, &first) ||
        integer(argv[4], 1, 64, &count) || integer(argv[5], 14, 16384, &budget)) return 64;
    pid_t parent = getppid();
    if (parent <= 1 || prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != parent) return 70;
    sigset_t signals; sigemptyset(&signals); sigaddset(&signals, SIGTERM); sigaddset(&signals, SIGINT); sigaddset(&signals, SIGALRM);
    if (sigprocmask(SIG_UNBLOCK, &signals, NULL) || signal(SIGALRM, SIG_DFL) == SIG_ERR ||
        signal(SIGTERM, SIG_DFL) == SIG_ERR || signal(SIGINT, SIG_DFL) == SIG_ERR) return 70;
    alarm(15);
    fputs("aurora_select_ready\n", stderr); fflush(stderr);
    int fd = snapshot(source_size);
    const char *error = "snapshot_failed";
    pcap_t *pcap = NULL;
    unsigned char bytes[16384]; size_t lengths[64] = {0}, used = 0;
    if (fd < 0) goto finish;
    error = "classic_pcap_required";
    if (!classic_pcap(fd)) goto finish;
    char path[64], detail[PCAP_ERRBUF_SIZE];
    snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
    pcap = pcap_open_offline(path, detail);
    error = "pcap_invalid";
    if (!pcap) goto finish;
    error = "ethernet_without_fcs_required";
    unsigned ext = (unsigned)pcap_datalink_ext(pcap);
    if (pcap_datalink(pcap) != DLT_EN10MB || (LT_FCS_LENGTH_PRESENT(ext) && LT_FCS_LENGTH(ext))) goto finish;
    for (uint64_t index = 0; index < first + count; index++) {
        struct pcap_pkthdr *header; const unsigned char *packet;
        int status = pcap_next_ex(pcap, &header, &packet);
        error = status == -2 ? "selection_incomplete" : "pcap_invalid";
        if (status != 1) goto finish;
        if (index < first) continue;
        error = "complete_bounded_frame_required";
        if (header->caplen != header->len || header->caplen < 14 || header->caplen > budget - used) goto finish;
        lengths[index - first] = header->caplen;
        memcpy(bytes + used, packet, header->caplen); used += header->caplen;
    }
    printf("{\"protocol\":1,\"linkType\":1,\"sourceBytes\":%llu,\"firstPacket\":%llu,\"selectedBytes\":%zu,\"frames\":[",
        (unsigned long long)source_size, (unsigned long long)first, used);
    size_t offset = 0;
    for (size_t frame = 0; frame < (size_t)count; frame++) {
        if (frame) putchar(',');
        putchar('"');
        for (size_t i = 0; i < lengths[frame]; i++) printf("%02x", bytes[offset++]);
        putchar('"');
    }
    fputs("]}\n", stdout); error = NULL;
finish:
    if (pcap) pcap_close(pcap);
    if (fd >= 0) close(fd);
    if (error) printf("{\"error\":\"%s\"}\n", error);
    return error ? 1 : 0;
}
