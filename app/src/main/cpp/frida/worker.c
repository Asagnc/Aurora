/* Build/ABI qualification only: not yet packaged or registered as an Agent tool. */
#include "session.h"
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

int main(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "--version") == 0) {
        puts(frida_version_string());
        return 0;
    }
    if (argc >= 2 && strcmp(argv[1], "attach") == 0) {
        if (getuid() != 0) return 77;
        struct frida_attach_input input;
        if (frida_input_read(argc, argv, &input)) return 64;
        int status = aurora_frida_attach(&input);
        free(input.script);
        return status;
    }
    if (argc != 2 || strcmp(argv[1], "probe") != 0) return 64;
    if (getuid() != 0) return 77;
    /* No attach/spawn operations exist in this qualification stage. */
    alarm(20);
    frida_init();
    GError *error = NULL;
    FridaDeviceManager *manager = frida_device_manager_new();
    FridaDevice *device = frida_device_manager_get_device_by_type_sync(manager,
        FRIDA_DEVICE_TYPE_LOCAL, 10000, NULL, &error);
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
    frida_device_manager_close_sync(manager, NULL, NULL);
    g_clear_pointer(&manager, frida_unref);
    frida_deinit();
    return status;
}
