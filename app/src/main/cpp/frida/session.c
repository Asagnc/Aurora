#define _GNU_SOURCE
#include "session.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <unistd.h>

static volatile sig_atomic_t signal_fd = -1;

void aurora_frida_stop(struct aurora_frida_session *run, const char *reason) {
    g_mutex_lock(&run->lock);
    if (!run->reason) run->reason = reason;
    g_cond_broadcast(&run->changed);
    g_mutex_unlock(&run->lock);
    g_cancellable_cancel(run->operation);
}

static void on_signal(int number) {
    int saved = errno;
    unsigned char value = (unsigned char) number;
    if (signal_fd >= 0) { ssize_t ignored = write(signal_fd, &value, 1); (void) ignored; }
    errno = saved;
}

static gboolean signal_ready(GIOChannel *channel, GIOCondition condition, gpointer opaque) {
    (void) condition;
    char bytes[32];
    int file = g_io_channel_unix_get_fd(channel);
    while (read(file, bytes, sizeof(bytes)) > 0) { }
    aurora_frida_stop(opaque, "cancelled");
    return G_SOURCE_CONTINUE;
}

static gboolean deadline(gpointer opaque) {
    aurora_frida_stop(opaque, "duration_elapsed");
    return G_SOURCE_REMOVE;
}

static gboolean cleanup_deadline(gpointer opaque) {
    g_cancellable_cancel(((struct aurora_frida_session *) opaque)->cleanup);
    return G_SOURCE_REMOVE;
}

static void failure(struct aurora_frida_session *run, const char *stage, GError **error) {
    g_atomic_int_set(&run->failed, 1);
    JsonBuilder *builder = aurora_frida_event("error");
    aurora_frida_text(builder, "stage", stage);
    aurora_frida_text(builder, "detail", *error ? (*error)->message : "operation_failed");
    aurora_frida_emit(run, builder, FALSE);
    g_clear_error(error);
    aurora_frida_stop(run, stage);
}

static void detached(FridaSession *session, FridaSessionDetachReason reason, FridaCrash *crash, gpointer opaque) {
    (void) session; (void) reason; (void) crash;
    aurora_frida_stop(opaque, "target_detached");
}

static gboolean identity_matches(struct aurora_frida_session *run) {
    struct pollfd target = {.fd = run->target_fd, .events = POLLIN};
    return poll(&target, 1, 0) == 0 && frida_target_matches(run->input);
}

static void execute_script(struct aurora_frida_session *run, FridaSession *session, FridaScript **script, GError **error) {
    if (!identity_matches(run)) { failure(run, "target_identity", error); return; }
    FridaScriptOptions *options = frida_script_options_new();
    frida_script_options_set_name(options, "aurora-agent");
    frida_script_options_set_runtime(options, FRIDA_SCRIPT_RUNTIME_QJS);
    *script = frida_session_create_script_sync(session, run->input->script, options, run->operation, error);
    g_object_unref(options);
    if (!*script) { failure(run, "create_script", error); return; }
    g_signal_connect(*script, "message", G_CALLBACK(aurora_frida_message), run);
    frida_script_load_sync(*script, run->operation, error);
    if (*error) { failure(run, "load_script", error); return; }
    run->loaded = TRUE;
    aurora_frida_emit(run, aurora_frida_event("loaded"), FALSE);
    g_mutex_lock(&run->lock);
    while (!run->reason) g_cond_wait(&run->changed, &run->lock);
    g_mutex_unlock(&run->lock);
}

static void cleanup(struct aurora_frida_session *run, FridaScript *script, FridaSession *session, FridaDeviceManager *manager) {
    GSource *timer = g_timeout_source_new(10000);
    g_source_set_callback(timer, cleanup_deadline, run, NULL);
    g_source_attach(timer, NULL);
    GError *error = NULL;
    run->unloaded = script == NULL || frida_script_is_destroyed(script);
    if (script) {
        if (!run->unloaded) {
            frida_script_unload_sync(script, run->cleanup, &error);
            run->unloaded = error == NULL;
            if (error) failure(run, "unload_script", &error);
        }
        g_signal_handlers_disconnect_by_data(script, run);
    }
    run->detached = session == NULL || frida_session_is_detached(session);
    if (session) {
        if (!run->detached) {
            frida_session_detach_sync(session, run->cleanup, &error);
            run->detached = error == NULL;
            if (error) failure(run, "detach", &error);
        }
        g_signal_handlers_disconnect_by_data(session, run);
    }
    frida_device_manager_close_sync(manager, run->cleanup, &error);
    run->closed = error == NULL;
    if (error) failure(run, "close_manager", &error);
    g_source_destroy(timer); g_source_unref(timer);
}

static gpointer execute(gpointer opaque) {
    struct aurora_frida_session *run = opaque;
    GError *error = NULL;
    FridaDeviceManager *manager = frida_device_manager_new();
    FridaDevice *device = frida_device_manager_get_device_by_type_sync(manager, FRIDA_DEVICE_TYPE_LOCAL, 10000, run->operation, &error);
    FridaSession *session = NULL;
    FridaScript *script = NULL;
    if (!device) failure(run, "local_device", &error);
    else if (!identity_matches(run)) failure(run, "target_identity", &error);
    else {
        session = frida_device_attach_sync(device, (guint) run->input->pid, NULL, run->operation, &error);
        if (!session) failure(run, "attach", &error);
        else {
            g_signal_connect(session, "detached", G_CALLBACK(detached), run);
            if (!frida_session_is_detached(session)) execute_script(run, session, &script, &error);
            else aurora_frida_stop(run, "target_detached");
        }
    }
    cleanup(run, script, session, manager);
    g_clear_pointer(&script, frida_unref); g_clear_pointer(&session, frida_unref);
    g_clear_pointer(&device, frida_unref); g_clear_pointer(&manager, frida_unref);
    g_main_loop_quit(run->loop);
    return NULL;
}

static gboolean start(gpointer opaque) {
    struct aurora_frida_session *run = opaque;
    run->thread = g_thread_new("aurora-frida", execute, run);
    return G_SOURCE_REMOVE;
}

static int result(struct aurora_frida_session *run) {
    gboolean clean = run->unloaded && run->detached && run->closed;
    gboolean success = clean && run->loaded && !g_atomic_int_get(&run->failed) &&
        g_strcmp0(run->reason, "duration_elapsed") == 0;
    JsonBuilder *builder = aurora_frida_event("completion");
    aurora_frida_text(builder, "reason", run->reason ? run->reason : "setup_failed");
    aurora_frida_number(builder, "dropped", g_atomic_int_get(&run->dropped));
    const char *keys[] = {"loaded", "unloaded", "detached", "managerClosed", "cleanupConfirmed", "success"};
    gboolean values[] = {run->loaded, run->unloaded, run->detached, run->closed, clean, success};
    for (unsigned index = 0; index < G_N_ELEMENTS(keys); index++) {
        json_builder_set_member_name(builder, keys[index]); json_builder_add_boolean_value(builder, values[index]);
    }
    return aurora_frida_emit(run, builder, TRUE) && success ? 0 : 1;
}

int aurora_frida_attach(const struct frida_attach_input *input) {
    if (!g_utf8_validate(input->script, -1, NULL)) return 65;
    int pipes[2];
    if (pipe2(pipes, O_CLOEXEC | O_NONBLOCK)) return 70;
    int target = (int) syscall(__NR_pidfd_open, input->pid, 0);
    if (target < 0) { close(pipes[0]); close(pipes[1]); return 69; }
    frida_init();
    struct aurora_frida_session run = {.input = input, .target_fd = target};
    g_mutex_init(&run.lock); g_mutex_init(&run.output_lock); g_cond_init(&run.changed);
    run.loop = g_main_loop_new(NULL, FALSE);
    run.operation = g_cancellable_new(); run.cleanup = g_cancellable_new();
    GIOChannel *channel = g_io_channel_unix_new(pipes[0]);
    GSource *signals = g_io_create_watch(channel, G_IO_IN);
    g_source_set_callback(signals, G_SOURCE_FUNC(signal_ready), &run, NULL); g_source_attach(signals, NULL);
    GSource *timer = g_timeout_source_new(input->duration_ms);
    g_source_set_callback(timer, deadline, &run, NULL); g_source_attach(timer, NULL);
    struct sigaction action = {.sa_handler = on_signal}, previous_term, previous_int, previous_pipe;
    sigemptyset(&action.sa_mask); signal_fd = pipes[1];
    sigaction(SIGTERM, &action, &previous_term); sigaction(SIGINT, &action, &previous_int);
    action.sa_handler = SIG_IGN; sigaction(SIGPIPE, &action, &previous_pipe);
    /* Last resort only: no completion proof is emitted if cleanup hangs. */
    alarm((input->duration_ms + 999) / 1000 + 20);
    g_idle_add(start, &run);
    g_main_loop_run(run.loop); g_thread_join(run.thread);
    // Frida callbacks may run on its private context. Drain it before the final record or state destruction.
    frida_deinit();
    int status = result(&run);
    g_source_destroy(timer); g_source_unref(timer); g_source_destroy(signals); g_source_unref(signals);
    signal_fd = -1; sigaction(SIGTERM, &previous_term, NULL); sigaction(SIGINT, &previous_int, NULL);
    sigaction(SIGPIPE, &previous_pipe, NULL);
    g_io_channel_unref(channel); close(pipes[0]); close(pipes[1]); close(target);
    g_clear_object(&run.operation); g_clear_object(&run.cleanup); g_main_loop_unref(run.loop);
    g_mutex_clear(&run.lock); g_mutex_clear(&run.output_lock); g_cond_clear(&run.changed);
    alarm(0);
    return status;
}
