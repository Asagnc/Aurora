#include "Worker.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void budget_case(uint64_t budget, uint64_t count, uint64_t expected,
                        const char *reason) {
    struct capture capture = { .budget = budget, .count = count, .bytes = 24 };
    capture.pcap = pcap_open_dead(DLT_EN10MB, 65535);
    assert(capture.pcap);
    char path[] = "/tmp/aurora-packet-boundary-XXXXXX";
    int fd = mkstemp(path);
    assert(fd >= 0);
    FILE *file = fdopen(fd, "wb");
    assert(file);
    capture.dump = pcap_dump_fopen(capture.pcap, file);
    assert(capture.dump);
    unsigned char bytes[42] = {0};
    struct pcap_pkthdr header = { .caplen = 42, .len = 42 };
    capture_packet((unsigned char *)&capture, &header, bytes);
    capture_packet((unsigned char *)&capture, &header, bytes);
    assert(capture.packets == expected);
    assert(capture.bytes == 24 + expected * 58);
    assert(capture.reason && !strcmp(capture.reason, reason));
    assert(!capture_close(&capture));
    pcap_close(capture.pcap);
    assert(!remove(path));
}

int main(void) {
    budget_case(81, 10, 0, "byte_budget");
    budget_case(82, 10, 1, "byte_budget");
    budget_case(140, 10, 2, "byte_budget");
    budget_case(200, 1, 1, "packet_budget");
    struct capture capture = { .bytes = 24 };
    capture.pcap = pcap_open_dead(DLT_EN10MB, 65535);
    FILE *full = fopen("/dev/full", "wb");
    assert(full);
    capture.dump = pcap_dump_fopen(capture.pcap, full);
    assert(capture.dump);
    assert(capture_close(&capture) < 0);
    pcap_close(capture.pcap);
    puts("Worker: four complete-packet budget boundaries and buffered write failure passed");
    return 0;
}
