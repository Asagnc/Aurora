#define _GNU_SOURCE
#include "Worker.h"
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/signalfd.h>
#include <sys/timerfd.h>
#include <unistd.h>

static int number(const char *text, uint64_t low, uint64_t high, uint64_t *out) {
    if (!text[0]) return -1;
    for (const char *p = text; *p; p++) if (*p < '0' || *p > '9') return -1;
    errno = 0;
    char *end;
    unsigned long long value = strtoull(text, &end, 10);
    if (errno || *end || value < low || value > high) return -1;
    *out = value;
    return 0;
}

static pcap_t *open_capture(const char *device, const char *filter) {
    char error[PCAP_ERRBUF_SIZE];
    pcap_t *pcap = pcap_create(device, error);
    if (!pcap) return NULL;
    if (pcap_set_snaplen(pcap, 65535) || pcap_set_promisc(pcap, 0) ||
        pcap_set_timeout(pcap, 100) || pcap_set_buffer_size(pcap, 4 * 1024 * 1024) ||
        pcap_set_immediate_mode(pcap, 0) || pcap_activate(pcap) < 0) goto failed;
    if (pcap_get_selectable_fd(pcap) < 0 || pcap_get_required_select_timeout(pcap) != NULL ||
        pcap_setnonblock(pcap, 1, error) < 0) goto failed;
    if (filter[0]) {
        struct bpf_program program;
        if (pcap_compile(pcap, &program, filter, 1, PCAP_NETMASK_UNKNOWN) < 0) goto failed;
        int result = pcap_setfilter(pcap, &program);
        pcap_freecode(&program);
        if (result < 0) goto failed;
    }
    return pcap;
failed:
    fprintf(stderr, "capture setup: %s\n", pcap_geterr(pcap));
    pcap_close(pcap);
    return NULL;
}

static int output_open(struct capture *capture) {
    int fd = open("capture.pcap", O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (fd < 0) return -1;
    FILE *file = fdopen(fd, "wb");
    if (!file) { close(fd); return -1; }
    capture->dump = pcap_dump_fopen(capture->pcap, file);
    if (!capture->dump) {
        /* libpcap closes FILE on header-write failure, but retains it for an
         * unsupported link type. This worker is single-threaded: no fd reuse. */
        if (fcntl(fd, F_GETFD) >= 0) fclose(file);
        return -1;
    }
    capture->bytes = 24;
    return 0;
}

int main(int argc, char **argv) {
    struct capture capture = {0};
    uint64_t duration;
    if (argc != 6 || getuid() != 0 || strlen(argv[1]) > 64 || strlen(argv[5]) > 4096 ||
        number(argv[2], 1, 270, &duration) ||
        number(argv[3], 24, 536870912, &capture.budget) ||
        number(argv[4], 1, 10000000, &capture.count)) return 64;
    sigset_t mask;
    sigemptyset(&mask);
    sigaddset(&mask, SIGTERM);
    sigaddset(&mask, SIGINT);
    if (sigprocmask(SIG_BLOCK, &mask, NULL)) return 70;
    int signals = signalfd(-1, &mask, SFD_CLOEXEC | SFD_NONBLOCK);
    int timer = timerfd_create(CLOCK_BOOTTIME, TFD_CLOEXEC | TFD_NONBLOCK);
    struct itimerspec deadline = { .it_value = { .tv_sec = (time_t)duration } };
    int status = 1;
    if (signals < 0 || timer < 0 || timerfd_settime(timer, 0, &deadline, NULL)) goto finish;
    capture.pcap = open_capture(argv[1], argv[5]);
    if (!capture.pcap || output_open(&capture)) goto finish;
    if (capture.budget == 24) capture.reason = "byte_budget";
    if (capture_loop(&capture, signals, timer)) {
        capture.reason = "capture_error";
        capture.failed = 1;
    }
    status = capture.failed ? 1 : 0;
finish:
    if (capture_close(&capture)) { capture.reason = "write_error"; status = 1; }
    struct pcap_stat stats;
    int known = capture.pcap && pcap_stats(capture.pcap, &stats) == 0;
    printf("{\"protocol\":1,\"reason\":\"%s\",\"packets\":%llu,\"bytes\":%llu,",
           capture.reason ? capture.reason : "setup_error",
           (unsigned long long)capture.packets, (unsigned long long)capture.bytes);
    if (known) printf("\"received\":%u,\"dropped\":%u}", stats.ps_recv, stats.ps_drop);
    else printf("\"received\":null,\"dropped\":null}");
    putchar('\n');
    if (capture.pcap) pcap_close(capture.pcap);
    if (signals >= 0) close(signals);
    if (timer >= 0) close(timer);
    return capture.reason && strcmp(capture.reason, "cancelled") == 0 ? 2 : status;
}
