#define _GNU_SOURCE
#include "Worker.h"
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <sys/signalfd.h>
#include <sys/stat.h>
#include <unistd.h>

void capture_packet(unsigned char *opaque, const struct pcap_pkthdr *header,
                    const unsigned char *packet) {
    struct capture *capture = (struct capture *)opaque;
    if (capture->reason) return;
    uint64_t size = 16ULL + header->caplen;
    if (size > capture->budget - capture->bytes) {
        capture->reason = "byte_budget";
        pcap_breakloop(capture->pcap);
        return;
    }
    pcap_dump((unsigned char *)capture->dump, header, packet);
    if (ferror(pcap_dump_file(capture->dump))) {
        capture->reason = "write_error";
        capture->failed = 1;
        pcap_breakloop(capture->pcap);
        return;
    }
    capture->bytes += size;
    capture->packets++;
    if (capture->packets == capture->count) capture->reason = "packet_budget";
    else if (capture->bytes == capture->budget) capture->reason = "byte_budget";
    if (capture->reason) pcap_breakloop(capture->pcap);
}

int capture_close(struct capture *capture) {
    if (!capture->dump) return 0;
    FILE *file = pcap_dump_file(capture->dump);
    struct stat info;
    int failed = pcap_dump_flush(capture->dump) < 0 || ferror(file);
    if (fstat(fileno(file), &info) < 0 || !S_ISREG(info.st_mode) ||
        (uint64_t)info.st_size != capture->bytes) failed = 1;
    if (!failed && fsync(fileno(file)) < 0) failed = 1;
    pcap_dump_close(capture->dump);
    capture->dump = NULL;
    return failed ? -1 : 0;
}

int capture_loop(struct capture *capture, int signal_fd, int timer_fd) {
    int packet_fd = pcap_get_selectable_fd(capture->pcap);
    if (packet_fd < 0) return -1;
    struct pollfd descriptors[] = {
        { signal_fd, POLLIN, 0 }, { timer_fd, POLLIN, 0 }, { packet_fd, POLLIN, 0 }
    };
    int pending = 0;
    while (!capture->reason) {
        int ready = poll(descriptors, 3, pending ? 0 : -1);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) return -1;
        if (descriptors[0].revents & POLLIN) {
            capture->reason = "cancelled";
            break;
        }
        if (descriptors[1].revents & POLLIN) {
            capture->reason = "duration";
            break;
        }
        for (int i = 0; i < 3; i++)
            if (descriptors[i].revents & (POLLERR | POLLHUP | POLLNVAL)) return -1;
        if (!pending && !(descriptors[2].revents & POLLIN)) continue;
        int count = pcap_dispatch(capture->pcap, 64, capture_packet, (unsigned char *)capture);
        if (count < 0 && count != PCAP_ERROR_BREAK) return -1;
        pending = count > 0;
    }
    return 0;
}
