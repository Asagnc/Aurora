#ifndef AURORA_ROTATE_STORE_H
#define AURORA_ROTATE_STORE_H
#include "RotateConfig.h"
#include <pcap/pcap.h>
#include <sys/stat.h>

struct aurora_capture_seal {
    unsigned ordinal;
    uint64_t bytes, packets, truncated, opened, sealed;
    struct stat physical;
    uint32_t link_type;
    char digest[65];
};
struct aurora_capture_store {
    const struct aurora_capture_config *config;
    pcap_t *pcap;
    pcap_dumper_t *dump;
    int directory, failed;
    unsigned segments;
    uint64_t started, bytes, packets;
    char ticket[65];
    struct aurora_capture_seal current;
};
uint64_t aurora_capture_boot_ms(void);
int aurora_capture_store_open(struct aurora_capture_store *store);
/* 0: accepted, 1: a declared budget stopped before this packet, -1: write/seal failure. */
int aurora_capture_store_packet(struct aurora_capture_store *store, const struct pcap_pkthdr *header, const unsigned char *data);
int aurora_capture_store_seal(struct aurora_capture_store *store);
void aurora_capture_store_abort(struct aurora_capture_store *store);
int aurora_capture_register(struct aurora_capture_store *store);
#endif
