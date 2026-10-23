#include "session.h"
#include <errno.h>
#include <sys/socket.h>

static gboolean supervisor_ready(GIOChannel *channel, GIOCondition condition, gpointer opaque) {
    (void) condition;
    struct aurora_frida_session *run = opaque;
    unsigned char command;
    ssize_t count = recv(g_io_channel_unix_get_fd(channel), &command, 1, MSG_DONTWAIT);
    if (count < 0 && (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK)) return G_SOURCE_CONTINUE;
    if (count == 1 && (command == 1 || command == 2)) aurora_frida_stop(run, command == 1 ? "duration_elapsed" : "cancelled");
    else {
        g_atomic_int_set(&run->failed, 1);
        aurora_frida_stop(run, count == 0 ? "supervisor_gone" : "supervisor_protocol");
    }
    return G_SOURCE_REMOVE;
}

GSource *aurora_managed_watch(struct aurora_frida_session *run, GMainContext *context) {
    if (run->supervisor_fd < 0) return NULL;
    GIOChannel *channel = g_io_channel_unix_new(run->supervisor_fd);
    GSource *source = g_io_create_watch(channel, G_IO_IN | G_IO_HUP | G_IO_ERR);
    g_source_set_callback(source, G_SOURCE_FUNC(supervisor_ready), run, NULL);
    g_source_attach(source, context);
    g_io_channel_unref(channel); // Source retains its channel; control owns the raw descriptor.
    return source;
}
