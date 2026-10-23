#ifndef AURORA_MANAGED_SCOPE_PLAN_H
#define AURORA_MANAGED_SCOPE_PLAN_H
#include "journal.h"
#include "../linux/task_scope.h"
/* Caller holds task lock. Record planned name/parent identity before mkdir,
 * then the ordinary full scope inode record before allowing any worker fork.
 * Failure preserves the plan; do not infer absence or forget it blindly. */
int managed_scope_create(const struct managed_journal *journal, const struct managed_key *key, struct task_scope *scope);
/* Only after actual removal of this scope and its full record. */
int managed_scope_plan_forget(const struct managed_journal *journal, const struct managed_key *key);
/* Only after ordinary scope_state_recover and proof of the old plan guard's
 * death. May remove an empty planned group, never kill an unrecorded populated
 * group. Handles the mkdir -> full-record crash gap without scanning cgroups. */
int managed_scope_plan_recover(const struct managed_journal *journal, const struct managed_key *key);
#endif
