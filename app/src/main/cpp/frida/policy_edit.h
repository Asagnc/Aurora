#ifndef AURORA_POLICY_EDIT_H
#define AURORA_POLICY_EDIT_H
#include <stddef.h>

#define AURORA_POLICY_LIMIT (64U * 1024U * 1024U)
struct aurora_policy_image { void *data; size_t size; };
struct aurora_policy_scope { const char *root_type; const char *app_type; };
/* Pure memory transformations. No policy loader, filesystem or global toggles. */
int aurora_policy_prepare(const struct aurora_policy_image *before, const struct aurora_policy_scope *scope,
    struct aurora_policy_image *candidate);
int aurora_policy_verify(const struct aurora_policy_image *before, const struct aurora_policy_image *loaded,
    const struct aurora_policy_scope *scope);
int aurora_policy_command(int argc, char **argv);
#endif
