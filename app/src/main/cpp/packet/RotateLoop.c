#define _GNU_SOURCE
#include "RotateWorker.h"
#include <errno.h>
#include <poll.h>
#include <sys/socket.h>

const char *aurora_capture_rotate_loop(struct aurora_capture_store *store, int control, int signals, int timer) {
    int source = pcap_get_selectable_fd(store->pcap);
    if (source < 0) return "capture_error";
    struct pollfd inputs[] = {{control, POLLIN, 0}, {signals, POLLIN, 0}, {timer, POLLIN, 0}, {source, POLLIN, 0}};
    int pending = 0;
    for (;;) {
        int ready = poll(inputs, 4, pending ? 0 : -1);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0) return "capture_error";
        if (inputs[0].revents & POLLIN) {
            unsigned char command[2]; ssize_t size = recv(control, command, sizeof(command), MSG_DONTWAIT);
            if (size == 1 && command[0] == 1) return "duration";
            if (size == 1 && command[0] == 2) return "cancelled";
            if (!(size < 0 && (errno == EINTR || errno == EAGAIN))) return "supervisor_error";
        }
        if (inputs[1].revents & POLLIN) return "cancelled";
        if (inputs[2].revents & POLLIN) return "duration";
        for (unsigned i = 0; i < 4; i++) if (inputs[i].revents & (POLLERR | POLLHUP | POLLNVAL)) return "capture_error";
        if (!pending && !(inputs[3].revents & POLLIN)) continue;
        struct pcap_pkthdr *header; const unsigned char *data;
        int result = pcap_next_ex(store->pcap, &header, &data);
        if (result < 0) return "capture_error";
        pending = result > 0;
        if (!pending) continue;
        result = aurora_capture_store_packet(store, header, data);
        if (result < 0) return "write_error";
        if (result > 0) return "storage_budget";
        if (store->packets + store->current.packets == store->config->packets) return "packet_budget";
        if (store->bytes + store->current.bytes == store->config->bytes) return "storage_budget";
    }
}
