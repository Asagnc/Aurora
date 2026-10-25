#ifndef AURORA_FRIDA_SESSION_H
#define AURORA_FRIDA_SESSION_H
#include "frida-core.h"
#include "input.h"
#include "managed_control.h"
#include "spawn_record.h"

struct aurora_frida_session {
    struct frida_attach_input *input;
    FridaDevice *device;
    struct aurora_spawn_record spawned;
    int spawn_directory;
    gboolean resume_requested;
    GMainLoop *loop;
    GCancellable *operation, *cleanup;
    GThread *thread;
    GMutex lock, output_lock;
    GCond changed;
    const char *reason;
    guint64 sequence, bytes;
    gint dropped, failed;
    gboolean loaded, posted, unloaded, detached, closed, manager_ready, attempted;
    int target_fd, supervisor_fd;
};
GSource *aurora_managed_watch(struct aurora_frida_session *run, GMainContext *context);
void aurora_frida_stop(struct aurora_frida_session *run, const char *reason);
JsonBuilder *aurora_frida_event(const char *kind);
void aurora_frida_number(JsonBuilder *builder, const char *key, gint64 value);
void aurora_frida_text(JsonBuilder *builder, const char *key, const char *value);
gboolean aurora_frida_emit(struct aurora_frida_session *run, JsonBuilder *builder, gboolean final);
void aurora_frida_message(FridaScript *script, const gchar *message, GBytes *data, gpointer opaque);
gboolean aurora_spawn_prepare(struct aurora_frida_session *run, GError **error);
gboolean aurora_spawn_resume(struct aurora_frida_session *run, GError **error);
void aurora_spawn_cleanup(struct aurora_frida_session *run);
/* Caller owns one Frida initialization/deinitialization around parsing and this session. */
int aurora_frida_attach(const struct frida_attach_input *input, struct aurora_managed_control *control, struct aurora_managed_facts *facts);
#endif
