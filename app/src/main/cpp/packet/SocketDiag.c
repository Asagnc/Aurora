#define _GNU_SOURCE
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/inet_diag.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <linux/sock_diag.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

struct query { int family, protocol; struct inet_diag_sockid id; };
struct snapshot { struct inet_diag_msg matches[32]; size_t count, scanned; int done; };
struct observation { int64_t times[3]; unsigned overflow_uid; };

static int port(const char *text, __be16 *value) {
    if (!*text || strlen(text) > 5) return -1;
    unsigned long number = 0;
    for (const char *p = text; *p; p++) {
        if (*p < '0' || *p > '9') return -1;
        number = number * 10 + (unsigned)(*p - '0');
    }
    if (number > 65535) return -1;
    *value = htons((uint16_t)number); return 0;
}

static int arguments(int argc, char **argv, struct query *q) {
    if (argc != 7) return -1;
    q->family = !strcmp(argv[1], "ipv4") ? AF_INET : !strcmp(argv[1], "ipv6") ? AF_INET6 : 0;
    q->protocol = !strcmp(argv[2], "tcp") ? IPPROTO_TCP : !strcmp(argv[2], "udp") ? IPPROTO_UDP : 0;
    return !q->family || !q->protocol || inet_pton(q->family, argv[3], q->id.idiag_src) != 1 ||
        inet_pton(q->family, argv[5], q->id.idiag_dst) != 1 || port(argv[4], &q->id.idiag_sport) ||
        port(argv[6], &q->id.idiag_dport) || !q->id.idiag_sport ? -1 : 0;
}

static int matches(const struct inet_diag_msg *m, const struct query *q) {
    size_t bytes = q->family == AF_INET ? 4 : 16;
    return m->idiag_family == q->family && m->id.idiag_sport == q->id.idiag_sport &&
        m->id.idiag_dport == q->id.idiag_dport && !memcmp(m->id.idiag_src, q->id.idiag_src, bytes) &&
        !memcmp(m->id.idiag_dst, q->id.idiag_dst, bytes);
}

/* Reject a partial/interrupted dump as a whole; never publish a unique UID from an incomplete scan. */
static int consume(const void *data, size_t size, const struct query *q, struct snapshot *out) {
    const unsigned char *cursor = data;
    while (size) {
        struct nlmsghdr header;
        if (size < sizeof(header)) return -1;
        memcpy(&header, cursor, sizeof(header));
        if (header.nlmsg_len < sizeof(header) || header.nlmsg_len > size || header.nlmsg_seq != 1 ||
            header.nlmsg_flags & NLM_F_DUMP_INTR || out->done) return -1;
        size_t payload = header.nlmsg_len - NLMSG_HDRLEN;
        const unsigned char *body = cursor + NLMSG_HDRLEN;
        if (header.nlmsg_type == NLMSG_DONE) {
            int status;
            if (payload < sizeof(status)) return -1;
            memcpy(&status, body, sizeof(status));
            if (status) return -1;
            out->done = 1;
        } else if (header.nlmsg_type == SOCK_DIAG_BY_FAMILY) {
            struct inet_diag_msg item;
            if (payload < sizeof(item) || ++out->scanned > 100000) return -1;
            memcpy(&item, body, sizeof(item));
            size_t offset = NLMSG_ALIGN(sizeof(item));
            while (offset < payload) {
                struct rtattr attr;
                if (payload - offset < sizeof(attr)) return -1;
                memcpy(&attr, body + offset, sizeof(attr));
                if (attr.rta_len < sizeof(attr) || RTA_ALIGN(attr.rta_len) > payload - offset) return -1;
                offset += RTA_ALIGN(attr.rta_len);
            }
            if (offset != payload) return -1;
            if (matches(&item, q)) {
                if (out->count == 32) return -1;
                out->matches[out->count++] = item;
            }
        } else return -1;
        size_t step = NLMSG_ALIGN(header.nlmsg_len);
        if (step > size) return -1;
        cursor += step; size -= step;
    }
    return 0;
}

static int64_t millis(clockid_t clock) {
    struct timespec value;
    if (clock_gettime(clock, &value)) return -1;
    return (int64_t)value.tv_sec * 1000 + value.tv_nsec / 1000000;
}

static int collect(int fd, const struct query *q, struct snapshot *out, int64_t deadline) {
    size_t total = 0;
    while (!out->done) {
        int64_t now = millis(CLOCK_BOOTTIME);
        if (now < 0 || now >= deadline) return -1;
        struct pollfd ready = {.fd = fd, .events = POLLIN};
        int result = poll(&ready, 1, (int)(deadline - now));
        if (result < 0 && errno == EINTR) continue;
        if (result <= 0 || ready.revents != POLLIN) return -1;
        union { struct nlmsghdr alignment; unsigned char bytes[65536]; } buffer;
        struct sockaddr_nl sender = {0};
        struct iovec vector = {.iov_base = buffer.bytes, .iov_len = sizeof(buffer.bytes)};
        struct msghdr message = {.msg_name = &sender, .msg_namelen = sizeof(sender), .msg_iov = &vector, .msg_iovlen = 1};
        ssize_t count = recvmsg(fd, &message, MSG_DONTWAIT);
        if (count < 0 && (errno == EINTR || errno == EAGAIN)) continue;
        if (count <= 0 || message.msg_flags & (MSG_TRUNC | MSG_CTRUNC) || message.msg_namelen != sizeof(sender) ||
            sender.nl_family != AF_NETLINK || sender.nl_pid || sender.nl_groups) return -1;
        total += (size_t)count;
        if (total > 16777216 || consume(buffer.bytes, (size_t)count, q, out)) return -1;
    }
    return 0;
}

static int identity(const char *path, struct stat *result) { return stat(path, result) || !result->st_ino ? -1 : 0; }

static int boot_id(char *text) {
    int fd = open("/proc/sys/kernel/random/boot_id", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    ssize_t count = read(fd, text, 37); close(fd);
    if (count != 37 || text[36] != '\n') return -1;
    text[36] = 0;
    for (int i = 0; i < 36; i++) {
        if (i == 8 || i == 13 || i == 18 || i == 23) { if (text[i] != '-') return -1; }
        else if (!(text[i] >= '0' && text[i] <= '9') && !(text[i] >= 'a' && text[i] <= 'f')) return -1;
    }
    return 0;
}

static void publish(const struct query *q, const struct snapshot *out, const struct stat *namespaces,
                    const char *boot, const struct observation *observed) {
    const int64_t *times = observed->times;
    char local[INET6_ADDRSTRLEN], remote[INET6_ADDRSTRLEN];
    inet_ntop(q->family, q->id.idiag_src, local, sizeof(local)); inet_ntop(q->family, q->id.idiag_dst, remote, sizeof(remote));
    const char *status = out->count > 1 ? "multiple" : "unknown";
    if (out->count == 1) {
        const struct inet_diag_msg *m = &out->matches[0];
        if (q->id.idiag_dport && m->idiag_inode && m->idiag_uid != observed->overflow_uid && m->idiag_state != 6 &&
            (m->id.idiag_cookie[0] != INET_DIAG_NOCOOKIE || m->id.idiag_cookie[1] != INET_DIAG_NOCOOKIE)) status = "exact_socket_uid";
    }
    printf("{\"protocol\":1,\"scope\":\"current_socket_snapshot\",\"status\":\"%s\",\"bootId\":\"%s\","
        "\"startedBootMs\":%lld,\"finishedBootMs\":%lld,\"observedUnixMs\":%lld,"
        "\"netNamespace\":\"%llu:%llu\",\"userNamespace\":\"%llu:%llu\","
        "\"overflowUid\":%u,\"family\":\"%s\",\"transport\":\"%s\",\"localAddress\":\"%s\",\"localPort\":%u,"
        "\"remoteAddress\":\"%s\",\"remotePort\":%u,\"matches\":[", status, boot,
        (long long)times[0], (long long)times[1], (long long)times[2],
        (unsigned long long)namespaces[0].st_dev, (unsigned long long)namespaces[0].st_ino,
        (unsigned long long)namespaces[1].st_dev, (unsigned long long)namespaces[1].st_ino,
        observed->overflow_uid, q->family == AF_INET ? "ipv4" : "ipv6", q->protocol == IPPROTO_TCP ? "tcp" : "udp", local,
        ntohs(q->id.idiag_sport), remote, ntohs(q->id.idiag_dport));
    for (size_t i = 0; i < out->count; i++) {
        const struct inet_diag_msg *m = &out->matches[i];
        printf("%s{\"uid\":%u,\"inode\":%u,\"cookie\":\"%08x%08x\",\"state\":%u,\"interfaceIndex\":%u}",
            i ? "," : "", m->idiag_uid, m->idiag_inode, m->id.idiag_cookie[1], m->id.idiag_cookie[0], m->idiag_state, m->id.idiag_if);
    }
    puts("]}");
}

#ifndef AURORA_SOCKET_DIAG_TEST
int main(int argc, char **argv) {
    struct query q = {0};
    if (arguments(argc, argv, &q)) return 64;
    if (getuid() || geteuid()) { puts("{\"error\":\"root_required\"}"); return 1; }
    struct stat namespaces[2], after[2]; char boot[38] = {0}; unsigned overflow_uid;
    FILE *overflow = fopen("/proc/sys/kernel/overflowuid", "re");
    if (!overflow) goto failed;
    int parsed = fscanf(overflow, "%u", &overflow_uid); fclose(overflow);
    if (parsed != 1) goto failed;
    struct observation observed = {.times = {millis(CLOCK_BOOTTIME), 0, 0}, .overflow_uid = overflow_uid};
    int64_t *times = observed.times;
    if (times[0] < 0 || identity("/proc/self/ns/net", &namespaces[0]) || identity("/proc/self/ns/user", &namespaces[1]) || boot_id(boot)) goto failed;
    int fd = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC | SOCK_NONBLOCK, NETLINK_SOCK_DIAG);
    if (fd < 0) goto failed;
    struct sockaddr_nl kernel = {.nl_family = AF_NETLINK};
    struct { struct nlmsghdr header; struct inet_diag_req_v2 request; } message = {
        .header = {.nlmsg_len = sizeof(message), .nlmsg_type = SOCK_DIAG_BY_FAMILY, .nlmsg_flags = NLM_F_REQUEST | NLM_F_DUMP, .nlmsg_seq = 1},
        .request = {.sdiag_family = (unsigned char)q.family, .sdiag_protocol = (unsigned char)q.protocol, .idiag_states = UINT32_MAX}};
    message.request.id = q.id;
    message.request.id.idiag_cookie[0] = message.request.id.idiag_cookie[1] = INET_DIAG_NOCOOKIE;
    struct snapshot out = {0};
    int result = sendto(fd, &message, sizeof(message), 0, (struct sockaddr *)&kernel, sizeof(kernel)) == (ssize_t)sizeof(message) ?
        collect(fd, &q, &out, times[0] + 3000) : -1;
    close(fd);
    times[1] = millis(CLOCK_BOOTTIME); times[2] = millis(CLOCK_REALTIME);
    if (result || times[1] < times[0] || times[1] > times[0] + 3000 || times[2] < 0 ||
        identity("/proc/self/ns/net", &after[0]) || identity("/proc/self/ns/user", &after[1])) goto failed;
    for (int i = 0; i < 2; i++) if (namespaces[i].st_dev != after[i].st_dev || namespaces[i].st_ino != after[i].st_ino) goto failed;
    publish(&q, &out, namespaces, boot, &observed); return ferror(stdout) ? 1 : 0;
failed:
    puts("{\"error\":\"socket_snapshot_unavailable\"}"); return 1;
}
#endif
