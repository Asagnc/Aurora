#ifndef AURORA_PACKET_WORKER_H
#define AURORA_PACKET_WORKER_H
#include <pcap/pcap.h>
#include <stdint.h>
struct capture {
    pcap_t *pcap;
    pcap_dumper_t *dump;
    uint64_t bytes, packets, budget, count, device, inode;
    const char *reason;
    int failed;
};
void capture_packet(unsigned char *, const struct pcap_pkthdr *, const unsigned char *);
int capture_loop(struct capture *, int, int);
int capture_close(struct capture *);
#endif
