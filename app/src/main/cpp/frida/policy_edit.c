#include "policy_edit.h"
#if defined(__ANDROID__)
#include "frida-policy-symbols.h"
#include "policy_types.h"
#include <stdlib.h>
#include <string.h>

static const char *const app_permissions[] = {"open", "read", "write", "getattr", "execute", "map", NULL};
static const char *const root_permissions[] = {"open", "read", "write", "getattr", "execute", "map", "relabelto", NULL};

static int parse(policydb_t *db, const struct aurora_policy_image *image) {
    if (!image->data || !image->size || image->size > AURORA_POLICY_LIMIT || policydb_init(db)) return -1;
    policy_file_t file;
    policy_file_init(&file);
    file.type = PF_USE_MEMORY; file.data = image->data; file.len = image->size;
    if (!policydb_read(db, &file, 0)) return 0;
    policydb_destroy(db);
    return -1;
}

static int serialize(policydb_t *db, struct aurora_policy_image *image) {
    if (policydb_to_image(NULL, db, &image->data, &image->size)) return -1;
    return image->size && image->size <= AURORA_POLICY_LIMIT ? 0 : -1;
}

static uint32_t permissions(class_datum_t *file, const char *const *names) {
    uint32_t mask = 0;
    for (unsigned i = 0; names[i]; i++) {
        perm_datum_t *p = hashtab_search(file->permissions.table, names[i]);
        if (!p && file->comdatum) p = hashtab_search(file->comdatum->permissions.table, names[i]);
        if (!p || !p->s.value || p->s.value > 32) return 0;
        mask |= 1U << (p->s.value - 1);
    }
    return mask;
}

static type_datum_t *concrete(policydb_t *db, const char *name) {
    if (!name || !*name || strlen(name) > 128 || strspn(name, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_") != strlen(name)) return NULL;
    type_datum_t *type = hashtab_search(db->p_types.table, name);
    return type && type->flavor == TYPE_TYPE && type->s.value <= 65534 ? type : NULL;
}

static int add_rule(policydb_t *db, const char *source, const char *const *names) {
    type_datum_t *s = concrete(db, source), *t = concrete(db, AURORA_POLICY_TYPE);
    class_datum_t *file = hashtab_search(db->p_classes.table, "file");
    if (!s || !t || !file || file->s.value > 65535) return -1;
    avtab_key_t key = {s->s.value, t->s.value, file->s.value, AVTAB_ALLOWED};
    avtab_datum_t data = {.data = permissions(file, names)};
    return data.data ? avtab_insert(&db->te_avtab, &key, &data) : -1;
}

static int exact_constraints(policydb_t *db, unsigned id, unsigned file, unsigned trusted) {
    for (uint32_t i = 0; i < db->p_classes.nprim; i++) {
        for (constraint_node_t *n = db->class_val_to_struct[i]->constraints; n; n = n->next) {
            for (constraint_expr_t *e = n->expr; e; e = e->next) {
                int expected = e->type_names && (ebitmap_get_bit(&e->type_names->types, file) || ebitmap_get_bit(&e->type_names->types, trusted));
                if (!!ebitmap_get_bit(&e->names, id) != !!expected) return -1;
            }
        }
    }
    return 0;
}

static int exact_type(policydb_t *db) {
    type_datum_t *t = concrete(db, AURORA_POLICY_TYPE);
    type_datum_t *file = hashtab_search(db->p_types.table, "file_type");
    type_datum_t *trusted = hashtab_search(db->p_types.table, "mlstrustedobject");
    if (!t || !file || !trusted || t->s.value != db->p_types.nprim || t->primary != 1 || t->flags || t->bounds ||
        file->flavor != TYPE_ATTRIB || trusted->flavor != TYPE_ATTRIB || ebitmap_get_bit(&db->permissive_map, t->s.value)) return -1;
    unsigned id = t->s.value - 1, f = file->s.value - 1, m = trusted->s.value - 1;
    ebitmap_t *map = &db->type_attr_map[id];
    if (map->highbit > db->p_types.nprim + 63) return -1;
    for (unsigned bit = 0; bit < map->highbit; bit++) {
        if (!!ebitmap_get_bit(map, bit) != (bit == id || bit == f || bit == m)) return -1;
    }
    if (!ebitmap_get_bit(map, id) || !ebitmap_get_bit(map, f) || !ebitmap_get_bit(map, m)) return -1;
    return exact_constraints(db, id, f, m);
}

static int strip_rules(policydb_t *db, const struct aurora_policy_scope *scope) {
    type_datum_t *app = concrete(db, scope->app_type), *root = concrete(db, scope->root_type);
    class_datum_t *file = hashtab_search(db->p_classes.table, "file");
    if (!app || !root || app == root || !file || exact_type(db)) return -1;
    unsigned seen = 0;
    for (uint32_t i = 0; i < db->te_avtab.nslot; i++) {
        avtab_ptr_t *cursor = &db->te_avtab.htable[i];
        while (*cursor) {
            avtab_ptr_t n = *cursor;
            if (n->key.target_type != db->p_types.nprim) { cursor = &n->next; continue; }
            unsigned bit = n->key.source_type == app->s.value ? 1 : n->key.source_type == root->s.value ? 2 : 0;
            uint32_t mask = permissions(file, bit == 1 ? app_permissions : root_permissions);
            if (!bit || (seen & bit) || !mask || n->key.target_class != file->s.value || n->key.specified != AVTAB_ALLOWED ||
                n->datum.xperms || n->datum.data != mask) return -1;
            seen |= bit; *cursor = n->next; free(n); db->te_avtab.nel--;
        }
    }
    return seen == 3 ? 0 : -1;
}

static int equal_image(policydb_t *db, const struct aurora_policy_image *expected) {
    struct aurora_policy_image current = {0};
    int result = serialize(db, &current);
    if (!result && (current.size != expected->size || memcmp(current.data, expected->data, current.size))) result = -1;
    /* The pinned avtab reader inserts same-key xperm nodes before existing
     * nodes, reversing their serialization order on each read. Compare one
     * additional serialization of the unmodified baseline, never a filtered
     * or partially compared current image. Both accepted byte images are
     * derived exclusively from the original policy. */
    if (result && current.data) {
        policydb_t baseline;
        if (!parse(&baseline, expected)) {
            struct aurora_policy_image alternate = {0};
            if (!serialize(&baseline, &alternate) && current.size == alternate.size &&
                !memcmp(current.data, alternate.data, current.size)) result = 0;
            free(alternate.data); policydb_destroy(&baseline);
        }
    }
    free(current.data);
    return result;
}

static int inverse(policydb_t *db, const struct aurora_policy_image *expected, const struct aurora_policy_scope *scope) {
    return strip_rules(db, scope) || aurora_policy_remove_type(db) || equal_image(db, expected) ? -1 : 0;
}

int aurora_policy_verify(const struct aurora_policy_image *before, const struct aurora_policy_image *loaded,
    const struct aurora_policy_scope *scope) {
    policydb_t original, current;
    struct aurora_policy_image expected = {0};
    if (parse(&original, before)) return -1;
    int result = -1;
    if (!serialize(&original, &expected) && !parse(&current, loaded)) {
        result = inverse(&current, &expected, scope);
        policydb_destroy(&current);
    }
    free(expected.data); policydb_destroy(&original);
    return result;
}

int aurora_policy_prepare(const struct aurora_policy_image *before, const struct aurora_policy_scope *scope,
    struct aurora_policy_image *candidate) {
    policydb_t original, baseline, checked;
    struct aurora_policy_image first = {0}, expected = {0};
    if (parse(&original, before)) return -1;
    int result = -1;
    type_datum_t *root = concrete(&original, scope->root_type), *app = concrete(&original, scope->app_type);
    if (!root || !app || root == app || serialize(&original, &first)) goto done;
    if (parse(&baseline, &first)) goto done;
    int failed = serialize(&baseline, &expected);
    policydb_destroy(&baseline);
    if (failed || aurora_policy_add_type(&original) || add_rule(&original, scope->app_type, app_permissions) ||
        add_rule(&original, scope->root_type, root_permissions) || serialize(&original, candidate)) goto done;
    if (!parse(&checked, candidate)) {
        result = inverse(&checked, &expected, scope);
        policydb_destroy(&checked);
    }
done:
    free(first.data); free(expected.data); policydb_destroy(&original);
    if (result) { free(candidate->data); *candidate = (struct aurora_policy_image){0}; }
    return result;
}
#else
int aurora_policy_prepare(const struct aurora_policy_image *before, const struct aurora_policy_scope *scope,
    struct aurora_policy_image *candidate) { (void)before; (void)scope; (void)candidate; return -1; }
int aurora_policy_verify(const struct aurora_policy_image *before, const struct aurora_policy_image *loaded,
    const struct aurora_policy_scope *scope) { (void)before; (void)loaded; (void)scope; return -1; }
#endif
