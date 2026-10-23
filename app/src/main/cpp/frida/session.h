#ifndef AURORA_FRIDA_SESSION_H
#define AURORA_FRIDA_SESSION_H
#include "frida-core.h"
#include "input.h"

struct aurora_frida_session {
    const struct frida_attach_input *input;
    GMainLoop *loop;
    GCancellable *operation, *cleanup;
    GThread *thread;
    GMutex lock, output_lock;
    GCond changed;
    const char *reason;
    guint64 sequence, bytes;
    gint dropped, failed;
    gboolean loaded, unloaded, detached, closed;
    int target_fd;
};
void aurora_frida_stop(struct aurora_frida_session *run, const char *reason);
JsonBuilder *aurora_frida_event(const char *kind);
void aurora_frida_number(JsonBuilder *builder, const char *key, gint64 value);
void aurora_frida_text(JsonBuilder *builder, const char *key, const char *value);
gboolean aurora_frida_emit(struct aurora_frida_session *run, JsonBuilder *builder, gboolean final);
void aurora_frida_message(FridaScript *script, const gchar *message, GBytes *data, gpointer opaque);
int aurora_frida_attach(const struct frida_attach_input *input);
#endif
