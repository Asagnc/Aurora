#define _GNU_SOURCE
#include <pcap/pcap.h>
#include <ctype.h>
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/signalfd.h>
#include <sys/timerfd.h>
#include <unistd.h>

struct options {
    uint64_t seconds, rate, packets, budget;
    unsigned char frame[16384];
    size_t length;
};
struct outcome { uint64_t packets, bytes, partial; const char *reason; int error; };

static int number(const char *text, uint64_t maximum, uint64_t *value) {
    if (!text[0] || strlen(text) > 20) return -1;
    for (const char *p = text; *p; p++) if (*p < '0' || *p > '9') return -1;
    errno = 0; char *end;
    *value = strtoull(text, &end, 10);
    return errno || *end || !*value || *value > maximum ? -1 : 0;
}

static int nibble(unsigned char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}

static int arguments(int argc, char **argv, struct options *options) {
    if (argc != 7 || getuid() != 0 || !argv[1][0] || strlen(argv[1]) > 64 || !strcmp(argv[1], "any")) return -1;
    for (const unsigned char *p = (unsigned char *)argv[1]; *p; p++)
        if (!isalnum(*p) && !strchr("_.:@-", *p)) return -1;
    if (number(argv[2], 30, &options->seconds) || number(argv[3], 1000, &options->rate) ||
        number(argv[4], 4096, &options->packets) || number(argv[5], 16777216, &options->budget)) return -1;
    size_t length = strlen(argv[6]);
    if (length < 28 || length > 32768 || length % 2) return -1;
    options->length = length / 2;
    for (size_t i = 0; i < options->length; i++) {
        int high = nibble((unsigned char)argv[6][i * 2]), low = nibble((unsigned char)argv[6][i * 2 + 1]);
        if (high < 0 || low < 0) return -1;
        options->frame[i] = (unsigned char)(high * 16 + low);
    }
    return 0;
}

static pcap_t *open_device(const char *device) {
    char error[PCAP_ERRBUF_SIZE];
    pcap_t *pcap = pcap_create(device, error);
    if (!pcap) return NULL;
    if (pcap_set_snaplen(pcap, 16384) || pcap_set_promisc(pcap, 0) || pcap_set_timeout(pcap, 1) ||
        pcap_set_buffer_size(pcap, 65536) || pcap_activate(pcap) < 0 || pcap_datalink(pcap) != DLT_EN10MB ||
        pcap_setnonblock(pcap, 1, error)) {
        pcap_close(pcap); return NULL;
    }
    return pcap;
}

static int ready(int signals, int deadline, int pace, int paced) {
    struct pollfd fds[] = {{signals, POLLIN, 0}, {deadline, POLLIN, 0}, {pace, POLLIN, 0}};
    int result;
    do { result = poll(fds, 3, paced ? -1 : 0); } while (result < 0 && errno == EINTR);
    if (result < 0) return -1;
    for (size_t i = 0; i < 3; i++) if (fds[i].revents & (POLLERR | POLLHUP | POLLNVAL)) return -1;
    if (fds[0].revents & POLLIN) return 1;
    if (fds[1].revents & POLLIN) return 2;
    if (paced) {
        uint64_t ticks;
        if (!(fds[2].revents & POLLIN) || read(pace, &ticks, sizeof(ticks)) != (ssize_t)sizeof(ticks)) return -1;
    }
    return 0;
}

static int next_packet(int pace, uint64_t rate) {
    struct timespec now;
    if (clock_gettime(CLOCK_BOOTTIME, &now)) return -1;
    uint64_t nanos = (uint64_t)now.tv_nsec + (1000000000ULL + rate - 1) / rate;
    struct itimerspec time = {.it_value = {.tv_sec = now.tv_sec + (time_t)(nanos / 1000000000ULL),
                                         .tv_nsec = (long)(nanos % 1000000000ULL)}};
    return timerfd_settime(pace, TFD_TIMER_ABSTIME, &time, NULL);
}

static void inject(pcap_t *pcap, const struct options *options, struct outcome *out, const int *fds) {
    while (out->packets < options->packets) {
        int state = ready(fds[0], fds[1], fds[2], out->packets != 0);
        if (state) { out->reason = state == 1 ? "cancelled" : state == 2 ? "duration" : "poll_error"; return; }
        if (options->length > options->budget - out->bytes) { out->reason = "byte_budget"; return; }
        errno = 0;
        int sent = pcap_inject(pcap, options->frame, options->length);
        if (sent < 0) { out->error = errno; out->reason = "inject_error"; return; }
        if ((size_t)sent != options->length) {
            out->partial = (uint64_t)sent; out->bytes += (uint64_t)sent;
            out->reason = "partial_write"; return;
        }
        out->packets++; out->bytes += (uint64_t)sent;
        if (out->packets < options->packets && next_packet(fds[2], options->rate)) { out->reason = "timer_error"; return; }
    }
    out->reason = "packet_budget";
}

int main(int argc, char **argv) {
    struct options options = {0};
    if (arguments(argc, argv, &options)) return 64;
    sigset_t mask; sigemptyset(&mask); sigaddset(&mask, SIGTERM); sigaddset(&mask, SIGINT);
    if (sigprocmask(SIG_BLOCK, &mask, NULL)) return 70;
    int fds[] = {signalfd(-1, &mask, SFD_CLOEXEC | SFD_NONBLOCK),
        timerfd_create(CLOCK_BOOTTIME, TFD_CLOEXEC | TFD_NONBLOCK),
        timerfd_create(CLOCK_BOOTTIME, TFD_CLOEXEC | TFD_NONBLOCK)};
    struct outcome out = {.reason = "setup_error"};
    struct itimerspec limit = {.it_value = {.tv_sec = (time_t)options.seconds}};
    pcap_t *pcap = NULL;
    if (fds[0] < 0 || fds[1] < 0 || fds[2] < 0 || timerfd_settime(fds[1], 0, &limit, NULL)) goto finish;
    pcap = open_device(argv[1]);
    if (!pcap) goto finish;
    fputs("aurora_inject_ready\n", stderr); fflush(stderr);
    inject(pcap, &options, &out, fds);
finish:;
    int complete = out.packets == options.packets && !out.partial && !strcmp(out.reason, "packet_budget");
    printf("{\"protocol\":1,\"reason\":\"%s\",\"frameBytes\":%zu,\"requestedPackets\":%llu,"
           "\"acceptedPackets\":%llu,\"acceptedBytes\":%llu,\"partialBytes\":%llu,\"systemErrno\":%d,\"complete\":%s}\n",
           out.reason, options.length, (unsigned long long)options.packets, (unsigned long long)out.packets,
           (unsigned long long)out.bytes, (unsigned long long)out.partial, out.error, complete ? "true" : "false");
    if (pcap) pcap_close(pcap);
    for (size_t i = 0; i < 3; i++) if (fds[i] >= 0) close(fds[i]);
    return complete ? 0 : !strcmp(out.reason, "cancelled") ? 2 :
        (!strcmp(out.reason, "duration") || !strcmp(out.reason, "byte_budget")) ? 3 : 1;
}
