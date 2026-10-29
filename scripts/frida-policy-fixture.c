/* Offline editor/round-trip qualification. No selinuxfs paths or kernel writes.
 * Only two exact source types, one new object type and fixed file permissions.
 */
#include "frida-policy-symbols.h"
#include "frida-policy-types.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LIMIT (64U * 1024U * 1024U)
static const char *const app_permissions[] = {"open", "read", "write", "getattr", "execute", "map", NULL};
static const char *const root_permissions[] = {"open", "read", "write", "getattr", "execute", "map", "relabelto", NULL};

static int parse(policydb_t *db, void *data, size_t size) {
    policy_file_t file;
    policy_file_init(&file);
    file.type = PF_USE_MEMORY;
    file.data = data;
    file.len = size;
    if (policydb_init(db)) return -1;
    return policydb_read(db, &file, 0);
}

static int read_image(const char *name, void **data, size_t *size) {
    FILE *file = fopen(name, "rb");
    if (!file) return -1;
    *data = malloc(LIMIT + 1);
    if (!*data) { fclose(file); return -1; }
    *size = fread(*data, 1, LIMIT + 1, file);
    int invalid = ferror(file) || !*size || *size > LIMIT;
    fclose(file);
    return invalid ? -1 : 0;
}

static uint32_t permission_mask(policydb_t *db, const char *const *permissions) {
    class_datum_t *c = hashtab_search(db->p_classes.table, "file");
    if (!c) return 0;
    uint32_t mask = 0;
    for (unsigned i = 0; permissions[i]; i++) {
        perm_datum_t *p = hashtab_search(c->permissions.table, permissions[i]);
        if (!p && c->comdatum) p = hashtab_search(c->comdatum->permissions.table, permissions[i]);
        if (!p || !p->s.value || p->s.value > 32) return 0;
        mask |= 1U << (p->s.value - 1);
    }
    return mask;
}

static int rule(policydb_t *db, const char *source, const char *const *permissions) {
    type_datum_t *s = hashtab_search(db->p_types.table, source);
    type_datum_t *t = hashtab_search(db->p_types.table, AURORA_POLICY_TYPE);
    class_datum_t *c = hashtab_search(db->p_classes.table, "file");
    if (!s || s->flavor != TYPE_TYPE || !t || !c) return -1;
    avtab_key_t key = {s->s.value, t->s.value, c->s.value, AVTAB_ALLOWED};
    avtab_datum_t datum = {.data = permission_mask(db, permissions)};
    if (!datum.data) return -1;
    return avtab_insert(&db->te_avtab, &key, &datum);
}

static int strip_rules(policydb_t *db) {
    uint32_t id = db->p_types.nprim;
    type_datum_t *app = hashtab_search(db->p_types.table, "untrusted_app");
    type_datum_t *root = hashtab_search(db->p_types.table, "magisk");
    class_datum_t *file = hashtab_search(db->p_classes.table, "file");
    if (!app || !root || !file) return -1;
    unsigned removed = 0;
    for (uint32_t i = 0; i < db->te_avtab.nslot; i++) {
        avtab_ptr_t *cursor = &db->te_avtab.htable[i];
        while (*cursor) {
            avtab_ptr_t n = *cursor;
            if (n->key.target_type != id) { cursor = &n->next; continue; }
            if (n->key.specified != AVTAB_ALLOWED || n->datum.xperms) return -1;
            uint32_t mask = n->key.source_type == app->s.value ? permission_mask(db, app_permissions) :
                n->key.source_type == root->s.value ? permission_mask(db, root_permissions) : 0;
            if (n->key.target_class != file->s.value || !mask || n->datum.data != mask) return -1;
            *cursor = n->next;
            free(n);
            db->te_avtab.nel--;
            removed++;
        }
    }
    return removed == 2 ? 0 : -1;
}

static int same_image(policydb_t *db, const void *expected, size_t expected_size) {
    void *image = NULL;
    size_t size = 0;
    if (policydb_to_image(NULL, db, &image, &size)) return -1;
    int same = size == expected_size && !memcmp(image, expected, size);
    if (!same) {
        size_t differences = 0, first = 0;
        for (size_t i = 0; i < size && i < expected_size; i++) {
            if (((unsigned char *) image)[i] == ((const unsigned char *) expected)[i]) continue;
            if (!differences) first = i;
            differences++;
        }
        fprintf(stderr, "canonical mismatch: actual=%zu expected=%zu differences=%zu first=%zu\n",
            size, expected_size, differences, first);
    }
    free(image);
    return same ? 0 : -1;
}

static int qualify(policydb_t *db, const char *output) {
    void *before = NULL, *after = NULL;
    size_t before_size = 0, after_size = 0;
    if (policydb_to_image(NULL, db, &before, &before_size)) return 2;
    policydb_t baseline;
    if (parse(&baseline, before, before_size)) return 2;
    free(before);
    before = NULL;
    if (policydb_to_image(NULL, &baseline, &before, &before_size)) return 2;
    policydb_destroy(&baseline);
    if (aurora_policy_add_type(db) || rule(db, "untrusted_app", app_permissions) ||
        rule(db, "magisk", root_permissions)) return 3;
    if (policydb_to_image(NULL, db, &after, &after_size) || after_size > LIMIT) return 4;
    policydb_t checked;
    if (parse(&checked, after, after_size)) return 5;
    /* Undo only the explicitly listed additions on the REPARSED image. Exact
     * canonical equality checks every other serialized policy section, including
     * conditional rules, constraints, permissive bits and type attributes. */
    if (strip_rules(&checked) || aurora_policy_remove_type(&checked) ||
        same_image(&checked, before, before_size)) return 6;
    policydb_destroy(&checked);
    FILE *file = fopen(output, "wbx");
    if (!file) return 7;
    int failed = fwrite(after, 1, after_size, file) != after_size;
    if (fclose(file)) failed = 1;
    printf("{\"offlineRoundTrip\":true,\"inverseCanonicalEquality\":true,\"addedTypes\":1,\"addedAllowKeys\":2,\"beforeBytes\":%zu,\"afterBytes\":%zu}\n", before_size, after_size);
    free(before); free(after);
    return failed ? 8 : 0;
}

int main(int argc, char **argv) {
    if (argc != 3 && (argc != 4 || strcmp(argv[1], "verify-loaded"))) return 64;
    void *image = NULL;
    size_t size = 0;
    if (read_image(argv[argc == 4 ? 2 : 1], &image, &size)) return 65;
    policydb_t db;
    if (parse(&db, image, size)) return 66;
    if (argc == 4) {
        void *expected = NULL, *current = NULL;
        size_t expected_size = 0, current_size = 0;
        policydb_t loaded;
        if (policydb_to_image(NULL, &db, &expected, &expected_size) ||
            read_image(argv[3], &current, &current_size) || parse(&loaded, current, current_size)) return 67;
        if (strip_rules(&loaded) || aurora_policy_remove_type(&loaded) || same_image(&loaded, expected, expected_size)) return 68;
        puts("{\"loadedInverseEquality\":true,\"addedTypes\":1,\"addedAllowKeys\":2}");
        return 0;
    }
    int status = qualify(&db, argv[2]);
    policydb_destroy(&db);
    free(image);
    return status;
}
