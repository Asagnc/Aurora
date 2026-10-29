/* Offline-only negative tests of the production inverse audit. No policy load. */
#include "frida-policy-symbols.h"
#include "policy_types.h"
#include "policy_edit.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int read_policy(const char *path, policydb_t *db) {
    FILE *file = fopen(path, "rb");
    if (!file) return -1;
    void *data = malloc(AURORA_POLICY_LIMIT + 1);
    if (!data) { fclose(file); return -1; }
    size_t size = fread(data, 1, AURORA_POLICY_LIMIT + 1, file);
    int failed = ferror(file) || !size || size > AURORA_POLICY_LIMIT;
    fclose(file);
    policy_file_t input;
    policy_file_init(&input);
    input.type = PF_USE_MEMORY; input.data = data; input.len = size;
    if (!failed) failed = policydb_init(db) || policydb_read(db, &input, 0);
    free(data);
    return failed ? -1 : 0;
}

static int mutate(policydb_t *db, const char *fault) {
    type_datum_t *t = hashtab_search(db->p_types.table, "frida_memfd");
    type_datum_t *domain = hashtab_search(db->p_types.table, "domain");
    type_datum_t *trusted = hashtab_search(db->p_types.table, "mlstrustedobject");
    type_datum_t *app = hashtab_search(db->p_types.table, "untrusted_app");
    class_datum_t *file = hashtab_search(db->p_classes.table, "file");
    if (!t || !domain || !trusted || !app || !file) return -1;
    if (!strcmp(fault, "none")) return 0;
    if (!strcmp(fault, "extra_attribute")) return ebitmap_set_bit(&db->type_attr_map[t->s.value - 1], domain->s.value - 1, 1);
    if (!strcmp(fault, "missing_attribute")) return ebitmap_set_bit(&db->type_attr_map[t->s.value - 1], trusted->s.value - 1, 0);
    if (!strcmp(fault, "permissive_type")) return ebitmap_set_bit(&db->permissive_map, t->s.value, 1);
    if (!strcmp(fault, "bounds")) { t->bounds = app->s.value; return 0; }
    avtab_key_t key = {app->s.value, t->s.value, file->s.value, AVTAB_ALLOWED};
    avtab_datum_t *rule = avtab_search(&db->te_avtab, &key);
    if (!rule) return -1;
    if (!strcmp(fault, "extra_permission")) {
        perm_datum_t *p = hashtab_search(file->permissions.table, "unlink");
        if (!p && file->comdatum) p = hashtab_search(file->comdatum->permissions.table, "unlink");
        if (!p || p->s.value > 32 || (rule->data & (1U << (p->s.value - 1)))) return -1;
        rule->data |= 1U << (p->s.value - 1);
        return 0;
    }
    if (!strcmp(fault, "broader_source")) {
        key.source_type = domain->s.value;
        avtab_datum_t data = {.data = rule->data};
        return avtab_insert(&db->te_avtab, &key, &data);
    }
    return -1;
}

int main(int argc, char **argv) {
    if (argc != 4) return 64;
    policydb_t before, current;
    if (read_policy(argv[1], &before) || read_policy(argv[2], &current) || mutate(&current, argv[3])) return 65;
    struct aurora_policy_image original = {0}, modified = {0};
    if (policydb_to_image(NULL, &before, &original.data, &original.size) ||
        policydb_to_image(NULL, &current, &modified.data, &modified.size)) return 66;
    /* Both raw kernel images take the same serialization round-trip. The
     * 'none' positive control must pass before any rejection is credited. */
    struct aurora_policy_scope scope = {"magisk", "untrusted_app"};
    int rejected = aurora_policy_verify(&original, &modified, &scope) != 0;
    int expected = strcmp(argv[3], "none") != 0;
    printf("{\"fault\":\"%s\",\"rejected\":%s,\"expected\":%s,\"kernelWritten\":false}\n",
        argv[3], rejected ? "true" : "false", expected ? "true" : "false");
    free(original.data); free(modified.data); policydb_destroy(&before); policydb_destroy(&current);
    return rejected == expected ? 0 : 1;
}
