#include "session.h"
#include <stdio.h>
#include <string.h>

void aurora_frida_text(JsonBuilder *builder, const char *key, const char *value) {
    json_builder_set_member_name(builder, key);
    json_builder_add_string_value(builder, value);
}

void aurora_frida_number(JsonBuilder *builder, const char *key, gint64 value) {
    json_builder_set_member_name(builder, key);
    json_builder_add_int_value(builder, value);
}

JsonBuilder *aurora_frida_event(const char *kind) {
    JsonBuilder *builder = json_builder_new();
    json_builder_begin_object(builder);
    aurora_frida_number(builder, "protocol", 1);
    aurora_frida_text(builder, "kind", kind);
    return builder;
}

gboolean aurora_frida_emit(struct aurora_frida_session *run, JsonBuilder *builder, gboolean final) {
    g_mutex_lock(&run->output_lock);
    aurora_frida_number(builder, "sequence", ++run->sequence);
    json_builder_end_object(builder);
    JsonNode *node = json_builder_get_root(builder);
    JsonGenerator *generator = json_generator_new();
    json_generator_set_root(generator, node);
    gsize length;
    gchar *wire = json_generator_to_data(generator, &length);
    gboolean bounded = final || (length <= 131072 && run->bytes + length + 1 <= 4 * 1024 * 1024 - 8192);
    gboolean written = FALSE;
    if (bounded) {
        written = fwrite(wire, 1, length, stdout) == length && fputc('\n', stdout) != EOF && fflush(stdout) == 0;
        run->bytes += length + 1;
    }
    g_free(wire); g_object_unref(generator); json_node_free(node); g_object_unref(builder);
    g_mutex_unlock(&run->output_lock);
    if (!bounded) g_atomic_int_inc(&run->dropped);
    if (!written) {
        g_atomic_int_set(&run->failed, 1);
        aurora_frida_stop(run, bounded ? "output_failed" : "output_budget");
    }
    return written;
}

void aurora_frida_message(FridaScript *script, const gchar *message, GBytes *data, gpointer opaque) {
    (void) script;
    struct aurora_frida_session *run = opaque;
    gsize binary_size = data ? g_bytes_get_size(data) : 0;
    if (strlen(message) > 65536 || binary_size > 16384) {
        g_atomic_int_inc(&run->dropped);
        g_atomic_int_set(&run->failed, 1);
        aurora_frida_stop(run, "message_budget");
        return;
    }
    JsonParser *parser = json_parser_new();
    gboolean parsed = json_parser_load_from_data(parser, message, -1, NULL);
    JsonNode *node = parsed ? json_parser_get_root(parser) : NULL;
    JsonObject *object = node && JSON_NODE_HOLDS_OBJECT(node) ? json_node_get_object(node) : NULL;
    JsonNode *type = object ? json_object_get_member(object, "type") : NULL;
    gboolean error = type && JSON_NODE_HOLDS_VALUE(type) && json_node_get_value_type(type) == G_TYPE_STRING &&
        g_strcmp0(json_node_get_string(type), "error") == 0;
    JsonBuilder *builder = aurora_frida_event("message");
    aurora_frida_text(builder, "message", message);
    if (binary_size) {
        gchar *encoded = g_base64_encode(g_bytes_get_data(data, NULL), binary_size);
        aurora_frida_text(builder, "dataBase64", encoded);
        g_free(encoded);
    }
    aurora_frida_emit(run, builder, FALSE);
    g_object_unref(parser);
    if (error) { g_atomic_int_set(&run->failed, 1); aurora_frida_stop(run, "script_error"); }
}
