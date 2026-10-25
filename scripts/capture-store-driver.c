#define _GNU_SOURCE
#include "RotateStore.h"
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/resource.h>
#include <unistd.h>

int __real_fsync(int fd);
static unsigned syncs;
int __wrap_fsync(int fd) {
    const char *stop = getenv("AURORA_TEST_FAIL_FSYNC");
    if (stop && ++syncs == (unsigned)atoi(stop)) return -1;
    return __real_fsync(fd);
}

int main(int argc, char **argv) {
    if (argc != 5 || getuid()) return 64;
    struct aurora_capture_config config = {.segment_bytes = strtoull(argv[2], NULL, 10), .bytes = strtoull(argv[3], NULL, 10),
        .packets = 100, .segments = 2, .seconds = 30, .root_pid = 123, .root_ticks = 456};
    const char *id = "00000000-0000-0000-0000-000000000001";
    strcpy(config.run, id); strcpy(config.owner, id); strcpy(config.boot, id); strcpy(config.epoch, id);
    struct aurora_capture_store store = {.config = &config, .directory = open(".", O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC),
        .pcap = pcap_open_dead(DLT_EN10MB, 262144), .started = aurora_capture_boot_ms()};
    memset(store.ticket, 'a', 64);
    if (!strcmp(argv[4], "short")) {
        signal(SIGXFSZ, SIG_IGN);
        struct rlimit limit = {.rlim_cur = 24, .rlim_max = 24};
        if (setrlimit(RLIMIT_FSIZE, &limit)) return 65;
    }
    int result = aurora_capture_store_open(&store), accepted = 0;
    unsigned char data[60]; memset(data, 0xab, sizeof(data));
    struct pcap_pkthdr header = {.ts = {.tv_sec = 123, .tv_usec = 456}, .caplen = 60, .len = 80};
    for (int i = 0; !result && i < atoi(argv[1]); i++) {
        result = aurora_capture_store_packet(&store, &header, data);
        if (!result) accepted++;
    }
    if (result >= 0 && store.dump && aurora_capture_store_seal(&store)) result = -1;
    aurora_capture_store_abort(&store); pcap_close(store.pcap); close(store.directory);
    printf("{\"result\":%d,\"accepted\":%d,\"segments\":%u,\"bytes\":%llu,\"packets\":%llu}\n",
        result, accepted, store.segments, (unsigned long long)store.bytes, (unsigned long long)store.packets);
    return 0;
}
