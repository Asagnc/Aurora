#define _GNU_SOURCE
#include "frida-core.h"
#include "input.h"
#include "fd_channel.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <poll.h>
#include <stddef.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

struct snapshot_spec { size_t bytes; const char *sha; };
struct channel_request {
    struct aurora_fd_offer offer;
    struct snapshot_spec script, bridge;
    struct frida_attach_input input;
};

static JsonObject *object(JsonObject *parent, const char *key) {
    JsonNode *node = json_object_get_member(parent, key);
    return node && JSON_NODE_HOLDS_OBJECT(node) ? json_node_get_object(node) : NULL;
}
static const char *text(JsonObject *parent, const char *key) {
    JsonNode *node = json_object_get_member(parent, key);
    return node && JSON_NODE_HOLDS_VALUE(node) && json_node_get_value_type(node) == G_TYPE_STRING ? json_node_get_string(node) : NULL;
}
static int64_t integer(JsonObject *parent, const char *key, int64_t missing) {
    JsonNode *node = json_object_get_member(parent, key);
    if (!node) return missing;
    return JSON_NODE_HOLDS_VALUE(node) && json_node_get_value_type(node) == G_TYPE_INT64 ? json_node_get_int(node) : -1;
}
static int matches(JsonObject *parent, const char *key, const char *expected) {
    const char *actual = text(parent, key);
    return !json_object_has_member(parent, key) || (actual && !strcmp(actual, expected));
}
static int hex(const char *text, unsigned char output[32]) {
    if (!text || strlen(text) != 64 || strspn(text, "0123456789abcdef") != 64) return -1;
    for (unsigned index = 0; index < 32; index++) {
        int high = g_ascii_xdigit_value(text[index * 2]), low = g_ascii_xdigit_value(text[index * 2 + 1]);
        output[index] = (unsigned char) (high * 16 + low);
    }
    return 0;
}
static int snapshot(JsonObject *parent, struct snapshot_spec *spec) {
    if (!parent) return -1;
    int64_t size = integer(parent, "bytes", -1);
    unsigned char ignored[32];
    spec->sha = text(parent, "sha256");
    if (size < 1 || size > 1048576 || hex(spec->sha, ignored)) return -1;
    spec->bytes = (size_t) size; return 0;
}

/* Root validates the full immutable ticket; native independently bounds all execution fields. */
static int request(JsonObject *root, struct channel_request *out) {
    JsonObject *identity = object(root, "identity"), *target = object(root, "target");
    if (!identity || !target || integer(root, "protocol", 1) != 1 || !matches(root, "kind", "frida") ||
        !matches(root, "action", "attach")) return -1;
    int64_t peer_pid = integer(identity, "pid", -1), peer_ticks = integer(identity, "startTicks", -1);
    int64_t pid = integer(target, "pid", -1), ticks = integer(target, "startTicks", -1);
    int64_t duration = integer(root, "observeMs", 60000);
    if (peer_pid < 1 || peer_pid > INT_MAX || peer_ticks < 1 || pid < 1 || pid > INT_MAX || ticks < 1 ||
        duration < 1 || duration > 250000 || snapshot(object(root, "script"), &out->script)) return -1;
    out->offer.peer_pid = (pid_t) peer_pid; out->offer.peer_ticks = (uint64_t) peer_ticks;
    out->offer.peer_uid = 0; out->offer.count = 1;
    JsonNode *bridge = json_object_get_member(root, "bridge");
    if (bridge && !JSON_NODE_HOLDS_NULL(bridge)) {
        if (snapshot(object(root, "bridge"), &out->bridge)) return -1;
        out->offer.count = 2;
    }
    out->input.pid = (pid_t) pid; out->input.start_ticks = (uint64_t) ticks;
    out->input.duration_ms = (unsigned) duration; return 0;
}

static int connect_channel(const char *name, struct aurora_fd_channel *channel) {
    if (strlen(name) != 42 || strncmp(name, "aurora-fd-", 10) || strspn(name + 10, "0123456789abcdef") != 32) return -1;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    memcpy(address.sun_path + 1, name, strlen(name));
    channel->socket = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (channel->socket < 0) return -1;
    socklen_t size = (socklen_t) (offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name));
    if (!connect(channel->socket, (struct sockaddr *) &address, size)) return 0;
    if (errno != EINPROGRESS || aurora_fd_wait(channel, POLLOUT)) return -1;
    int error; socklen_t length = sizeof(error);
    if (getsockopt(channel->socket, SOL_SOCKET, SO_ERROR, &error, &length)) return -1;
    if (error) { errno = error; return -1; }
    return 0;
}

static char *read_snapshot(int fd, const struct snapshot_spec *spec) {
    struct stat info;
    if (fstat(fd, &info) || info.st_size != (off_t) spec->bytes) return NULL;
    char *bytes = malloc(spec->bytes + 1);
    if (!bytes) return NULL;
    size_t offset = 0;
    while (offset < spec->bytes) {
        ssize_t count = pread(fd, bytes + offset, spec->bytes - offset, (off_t) offset);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) { free(bytes); return NULL; }
        offset += (size_t) count;
    }
    gchar *digest = g_compute_checksum_for_data(G_CHECKSUM_SHA256, (const guchar *) bytes, spec->bytes);
    int valid = digest && !strcmp(digest, spec->sha) && !memchr(bytes, 0, spec->bytes) && g_utf8_validate(bytes, spec->bytes, NULL);
    g_free(digest);
    if (!valid) { free(bytes); return NULL; }
    bytes[spec->bytes] = 0; return bytes;
}

static int capture(const char *name, struct channel_request *request, struct frida_attach_input *input) {
    struct aurora_fd_channel channel = {.socket = -1, .cancel = -1, .peer = -1, .deadline_ms = aurora_fd_now() + 5000};
    int descriptors[2] = {-1, -1}, result = -1;
    if (connect_channel(name, &channel) || aurora_fd_receive(&channel, &request->offer, descriptors)) goto done;
    close(channel.socket); channel.socket = -1; // End control before Frida initialization or script loading.
    char *user = read_snapshot(descriptors[0], &request->script);
    if (!user) goto done;
    char *bridge = request->offer.count == 2 ? read_snapshot(descriptors[1], &request->bridge) : NULL;
    if (request->offer.count == 2 && !bridge) { free(user); goto done; }
    *input = request->input; input->script = bridge ? bridge : user; input->payload = bridge ? user : NULL;
    result = 0;
done:
    if (channel.socket >= 0) close(channel.socket);
    for (unsigned index = 0; index < 2; index++) if (descriptors[index] >= 0) close(descriptors[index]);
    return result;
}

int frida_channel_read(int argc, char **argv, struct frida_attach_input *input) {
    memset(input, 0, sizeof(*input));
    if (argc != 5 || strlen(argv[4]) > 16384 || !g_utf8_validate(argv[4], -1, NULL)) return -1;
    struct channel_request parsed = {0};
    if (hex(argv[3], parsed.offer.nonce)) return -1;
    JsonParser *parser = json_parser_new();
    int result = -1;
    if (!json_parser_load_from_data(parser, argv[4], -1, NULL)) goto done;
    JsonNode *root = json_parser_get_root(parser);
    if (!root || !JSON_NODE_HOLDS_OBJECT(root) || request(json_node_get_object(root), &parsed)) goto done;
    gchar *digest = g_compute_checksum_for_string(G_CHECKSUM_SHA256, argv[4], -1);
    int invalid = hex(digest, parsed.offer.ticket); g_free(digest);
    if (invalid) goto done;
    result = capture(argv[2], &parsed, input);
done:
    g_object_unref(parser); return result;
}
