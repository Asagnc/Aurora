#ifndef AURORA_MANAGED_SCOPE_PLAN_INTERNAL_H
#define AURORA_MANAGED_SCOPE_PLAN_INTERNAL_H
#include "scope_plan.h"
#include "../linux/scope_state.h"
int managed_plan_store(const struct managed_journal *journal, const struct managed_key *key, const struct task_scope *scope);
/* 0 absent, 1 valid, -1 unsafe. scope dev/ino are zero: this is ONLY a plan. */
int managed_plan_read(const struct managed_journal *journal, const struct managed_key *key, struct scope_record *record);
#endif
