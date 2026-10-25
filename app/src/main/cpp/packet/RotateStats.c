#include "RotateStore.h"
#include <inttypes.h>
#include <stdio.h>

int aurora_capture_final(struct aurora_capture_store *store, const char *reason, int status, const struct pcap_stat *kernel) {
    uint64_t finished = aurora_capture_boot_ms(); const struct aurora_capture_config *config = store->config;
    if (finished == UINT64_MAX || store->started == UINT64_MAX || finished < store->started || store->directory < 0) return -1;
    char counters[160], data[1024];
    if (kernel) snprintf(counters, sizeof(counters), "{\"received\":%u,\"dropped\":%u,\"interfaceDropped\":%u}",
        kernel->ps_recv, kernel->ps_drop, kernel->ps_ifdrop);
    else snprintf(counters, sizeof(counters), "{\"received\":null,\"dropped\":null,\"interfaceDropped\":null}");
    int size = snprintf(data, sizeof(data),
        "{\"protocol\":1,\"binding\":{\"runId\":\"%s\",\"owner\":\"%s\",\"identity\":{\"pid\":%u,\"startTicks\":%" PRIu64
        ",\"bootId\":\"%s\",\"epoch\":\"%s\"}},\"ticketSha256\":\"%s\",\"startedBootMs\":%" PRIu64
        ",\"finishedBootMs\":%" PRIu64 ",\"segments\":%u,\"bytes\":%" PRIu64 ",\"packets\":%" PRIu64
        ",\"reason\":\"%s\",\"status\":%d,\"kernel\":%s}\n",
        config->run, config->owner, config->root_pid, config->root_ticks, config->boot, config->epoch, store->ticket,
        store->started, finished, store->segments, store->bytes, store->packets, reason, status, counters);
    return aurora_capture_private_record(store, "capture.final.partial", "capture.final", data, size);
}
