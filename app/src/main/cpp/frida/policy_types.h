#ifndef AURORA_POLICY_TYPES_H
#define AURORA_POLICY_TYPES_H
/* Fixed Frida libsepol ABI. Editing never loads a kernel policy. */
#include <sepol/policydb/policydb.h>

#define AURORA_POLICY_TYPE "frida_memfd"
int aurora_policy_add_type(policydb_t *db);
int aurora_policy_remove_type(policydb_t *db);

#endif
