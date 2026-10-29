/* Packaged Root worker. Managed FD/ticket control owns every attach/spawn run. */
#include "session.h"
#include "fd_channel.h"
#include "policy_edit.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static void text(JsonBuilder *builder, const char *key, const char *value) {
    json_builder_set_member_name(builder, key);
    json_builder_add_string_value(builder, value);
}

static void number(JsonBuilder *builder, const char *key, gint64 value) {
    json_builder_set_member_name(builder, key);
    json_builder_add_int_value(builder, value);
}

static void emit(JsonBuilder *builder) {
    JsonGenerator *generator = json_generator_new();
    JsonNode *root = json_builder_get_root(builder);
    json_generator_set_root(generator, root);
    gchar *wire = json_generator_to_data(generator, NULL);
    puts(wire);
    g_free(wire);
    json_node_free(root);
    g_object_unref(generator);
}

static void processes(JsonBuilder *builder, FridaProcessList *list) {
    const gint count = frida_process_list_size(list);
    const gint limit = MIN(count, 512);
    number(builder, "total", count);
    number(builder, "returned", limit);
    json_builder_set_member_name(builder, "processes");
    json_builder_begin_array(builder);
    for (gint index = 0; index < limit; index++) {
        FridaProcess *process = frida_process_list_get(list, index);
        const gchar *name = frida_process_get_name(process);
        gchar *valid = g_utf8_make_valid(name, MIN(strlen(name), (size_t) 1024));
        json_builder_begin_object(builder);
        number(builder, "pid", frida_process_get_pid(process));
        text(builder, "name", valid);
        json_builder_end_object(builder);
        g_free(valid);
        frida_unref(process);
    }
    json_builder_end_array(builder);
}

static int finish_control(struct aurora_managed_control *control, const struct aurora_managed_facts *facts, int status) {
    if (aurora_managed_report(control, facts, status)) status = 74;
    aurora_managed_close(control);
    return status;
}

static int attach(int argc, char **argv) {
    if (getuid() != 0) return 77;
    struct aurora_managed_control control;
    if (aurora_managed_open(&control)) return 64;
    struct aurora_managed_facts facts = {.flags = AURORA_FACT_NO_SESSION};
    if (control.fd >= 0 && strcmp(argv[1], "attach-channel")) return finish_control(&control, &facts, 64);
    int64_t started = aurora_fd_now();
    if (started < 0) { aurora_managed_close(&control); return 70; }
    alarm(20); frida_init();
    struct frida_attach_input input = {0};
    int invalid = !strcmp(argv[1], "attach-channel") ? frida_channel_read(argc, argv, &input, control.fd) : frida_input_read(argc, argv, &input);
    if (control.fd >= 0 && (strcmp(argv[1], "attach-channel") || memcmp(control.ticket, input.ticket, 32))) invalid = 1;
    int status = 64;
    int64_t elapsed = aurora_fd_now() - started;
    if (!invalid && elapsed >= 0 && elapsed < input.duration_ms) {
        // Runtime setup and FD handoff consume the operation budget, never its cleanup reserve.
        input.duration_ms -= (unsigned) elapsed;
        status = aurora_frida_attach(&input, &control, &facts);
    }
    free(input.script); free(input.payload); free(input.spawn_program);
    frida_deinit();
    status = finish_control(&control, &facts, status); alarm(0);
    return status;
}

int main(int argc, char **argv) {
    if (argc >= 2 && !strncmp(argv[1], "policy-", 7)) return aurora_policy_command(argc, argv);
    if (argc == 2 && strcmp(argv[1], "--version") == 0) {
        puts(frida_version_string());
        return 0;
    }
    if (argc >= 2 && (!strcmp(argv[1], "attach") || !strcmp(argv[1], "attach-channel") || !strcmp(argv[1], "spawn-file"))) return attach(argc, argv);
    if (argc != 2 || strcmp(argv[1], "probe") != 0) return 64;
    if (getuid() != 0) return 77;
    /* Probe only enumerates. Attach uses its separate bounded path above. */
    alarm(20);
    frida_init();
    GError *error = NULL;
    FridaDeviceManager *manager = frida_device_manager_new();
    FridaDeviceList *ready = frida_device_manager_enumerate_devices_sync(manager, NULL, &error);
    FridaDevice *device = ready ? frida_device_manager_get_device_by_type_sync(manager,
        FRIDA_DEVICE_TYPE_LOCAL, 0, NULL, &error) : NULL;
    gboolean manager_ready = ready != NULL;
    if (ready) frida_unref(ready);
    FridaProcessQueryOptions *options = frida_process_query_options_new();
    frida_process_query_options_set_scope(options, FRIDA_SCOPE_MINIMAL);
    FridaProcessList *list = device == NULL ? NULL :
        frida_device_enumerate_processes_sync(device, options, NULL, &error);
    JsonBuilder *builder = json_builder_new();
    json_builder_begin_object(builder);
    number(builder, "protocol", 1);
    text(builder, "frida", frida_version_string());
    text(builder, "operation", "probe");
    if (list != NULL) processes(builder, list);
    else text(builder, "error", error != NULL ? error->message : "local_device_unavailable");
    json_builder_end_object(builder);
    emit(builder);
    const int status = list != NULL ? 0 : 1;
    g_clear_error(&error);
    g_clear_object(&builder);
    g_clear_pointer(&list, frida_unref);
    g_clear_object(&options);
    g_clear_pointer(&device, frida_unref);
    if (!manager_ready) _exit(1); // No unsafe close/finalizer while backend startup is unconfirmed.
    frida_device_manager_close_sync(manager, NULL, NULL);
    g_clear_pointer(&manager, frida_unref);
    frida_deinit();
    return status;
}
