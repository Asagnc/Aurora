#define _GNU_SOURCE
#include "RotateWorker.h"
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/signalfd.h>
#include <sys/timerfd.h>
#include <unistd.h>

static int interrupts(int descriptors[2], unsigned seconds) {
    sigset_t mask; sigemptyset(&mask); sigaddset(&mask, SIGTERM); sigaddset(&mask, SIGINT);
    if (sigprocmask(SIG_BLOCK, &mask, NULL)) return -1;
    descriptors[0] = signalfd(-1, &mask, SFD_CLOEXEC | SFD_NONBLOCK);
    descriptors[1] = timerfd_create(CLOCK_BOOTTIME, TFD_CLOEXEC | TFD_NONBLOCK);
    struct itimerspec deadline = {.it_value = {.tv_sec = seconds}};
    return descriptors[0] < 0 || descriptors[1] < 0 || timerfd_settime(descriptors[1], 0, &deadline, NULL) ? -1 : 0;
}

static int successful(const char *reason) {
    return !strcmp(reason, "duration") || !strcmp(reason, "storage_budget") || !strcmp(reason, "packet_budget");
}

int main(int argc, char **argv) {
    if (getuid()) return 64;
    pid_t parent = getppid();
    if (parent <= 1 || prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != parent) return 70;
    struct aurora_managed_control control = {.fd = -1}; struct aurora_capture_config config;
    if (aurora_managed_open(&control) || control.fd < 0) return 64;
    if (aurora_capture_receive(argc, argv, &control, &config)) {
        aurora_capture_report(&control, 0, 64); aurora_managed_close(&control); return 64;
    }
    int descriptors[2] = {-1, -1}, status = 1; const char *reason = "setup_error";
    struct aurora_capture_store store = {.config = &config, .directory = -1, .started = aurora_capture_boot_ms()};
    for (unsigned i = 0; i < 32; i++) snprintf(store.ticket + 2 * i, 3, "%02x", control.ticket[i]);
    if (store.started == UINT64_MAX || interrupts(descriptors, config.seconds)) goto finish;
    store.directory = open(".", O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    store.pcap = aurora_capture_source(&config);
    if (store.directory < 0 || !store.pcap || aurora_capture_store_open(&store)) goto finish;
    fputs("aurora_capture_rotation_ready\n", stderr); fflush(stderr);
    reason = aurora_capture_rotate_loop(&store, control.fd, descriptors[0], descriptors[1]);
    if (store.dump && !store.failed && aurora_capture_store_seal(&store)) reason = "write_error";
    status = successful(reason) ? 0 : !strcmp(reason, "cancelled") ? 2 : 1;
finish:
    aurora_capture_store_abort(&store);
    struct pcap_stat counters; int known = store.pcap && pcap_stats(store.pcap, &counters) == 0;
    /* Close the packet handle before sending capture completion facts. Guard must separately prove worker/domain exit. */
    if (store.pcap) pcap_close(store.pcap);
    if (store.directory >= 0) close(store.directory);
    for (unsigned i = 0; i < 2; i++) if (descriptors[i] >= 0) close(descriptors[i]);
    printf("{\"protocol\":1,\"reason\":\"%s\",\"segments\":%u,\"bytes\":%llu,\"packets\":%llu,",
        reason, store.segments, (unsigned long long)store.bytes, (unsigned long long)store.packets);
    if (known) printf("\"kernel\":{\"received\":%u,\"dropped\":%u,\"interfaceDropped\":%u}}\n", counters.ps_recv, counters.ps_drop, counters.ps_ifdrop);
    else fputs("\"kernel\":{\"received\":null,\"dropped\":null,\"interfaceDropped\":null}}\n", stdout);
    if (ferror(stdout) || fflush(stdout) || aurora_capture_report(&control, !status, status)) status = 1;
    aurora_managed_close(&control); return status;
}
