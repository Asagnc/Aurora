/* Type/constraint mechanics follow Frida 17.24.3 lib/selinux/patch.c.
 * This qualification fixture uses its already pinned libsepol, without the
 * server's permissive fallback or broad domain rules. Corresponding upstream
 * sources and licenses are in tools/frida-sources/catalog.json.
 */
#include "frida-policy-symbols.h"
#include "frida-policy-types.h"
#include <stdlib.h>
#include <string.h>

static int constraints(policydb_t *db, uint32_t id, uint32_t attribute, int value) {
    for (uint32_t i = 0; i < db->p_classes.nprim; i++) {
        for (constraint_node_t *n = db->class_val_to_struct[i]->constraints; n; n = n->next) {
            for (constraint_expr_t *e = n->expr; e; e = e->next) {
                if (e->type_names && ebitmap_get_bit(&e->type_names->types, attribute)) {
                    if (ebitmap_set_bit(&e->names, id, value)) return -1;
                }
            }
        }
    }
    return 0;
}

static int attribute(policydb_t *db, uint32_t id, const char *name, int value) {
    type_datum_t *a = hashtab_search(db->p_types.table, name);
    if (!a || a->flavor != TYPE_ATTRIB) return -1;
    uint32_t index = a->s.value - 1;
    if (ebitmap_set_bit(&a->types, id, value) ||
        ebitmap_set_bit(&db->type_attr_map[id], index, value) ||
        ebitmap_set_bit(&db->attr_type_map[index], id, value)) return -1;
    return constraints(db, id, index, value);
}

int aurora_policy_add_type(policydb_t *db) {
    /* Existing dedicated types need a separate audited upgrade path. */
    if (hashtab_search(db->p_types.table, AURORA_POLICY_TYPE) || db->p_types.nprim >= 65534) return -1;
    uint32_t id = db->p_types.nprim;
    type_datum_t *type = calloc(1, sizeof(*type));
    char *name = strdup(AURORA_POLICY_TYPE);
    if (!type || !name) { free(type); free(name); return -1; }
    type_datum_init(type);
    type->s.value = id + 1;
    type->primary = 1;
    type->flavor = TYPE_TYPE;
    if (hashtab_insert(db->p_types.table, name, type)) { free(type); free(name); return -1; }
    ebitmap_t *types = realloc(db->type_attr_map, (id + 1) * sizeof(*types));
    if (!types) return -1;
    db->type_attr_map = types;
    ebitmap_init(&types[id]);
    ebitmap_t *attrs = realloc(db->attr_type_map, (id + 1) * sizeof(*attrs));
    if (!attrs) return -1;
    db->attr_type_map = attrs;
    ebitmap_init(&attrs[id]);
    db->p_types.nprim++;
    if (policydb_index_others(NULL, db, 0) || ebitmap_set_bit(&types[id], id, 1)) return -1;
    return attribute(db, id, "file_type", 1) || attribute(db, id, "mlstrustedobject", 1) ? -1 : 0;
}

static void destroy_type(char *name, void *datum, void *unused) {
    (void) unused;
    type_datum_destroy(datum);
    free(datum);
    free(name);
}

int aurora_policy_remove_type(policydb_t *db) {
    type_datum_t *type = hashtab_search(db->p_types.table, AURORA_POLICY_TYPE);
    if (!type || type->s.value != db->p_types.nprim) return -1;
    uint32_t id = type->s.value - 1;
    if (attribute(db, id, "file_type", 0) || attribute(db, id, "mlstrustedobject", 0)) return -1;
    ebitmap_destroy(&db->type_attr_map[id]);
    ebitmap_destroy(&db->attr_type_map[id]);
    if (hashtab_remove(db->p_types.table, AURORA_POLICY_TYPE, destroy_type, NULL)) return -1;
    db->p_types.nprim--;
    return policydb_index_others(NULL, db, 0);
}
