#ifndef AURORA_TASK_SCOPE_H
#define AURORA_TASK_SCOPE_H

struct task_scope {
    int parent, directory, processes, events, kill;
    char name[96];
};

/* No caller-provided paths or existing-group attachment. Only this process's
 * newly created child scope can be admitted to, killed, or removed. */
int task_scope_create(struct task_scope *scope);
int task_scope_enter(const struct task_scope *scope);
int task_scope_populated(const struct task_scope *scope);
int task_scope_kill(const struct task_scope *scope);
int task_scope_wait_empty(const struct task_scope *scope, int timeout_ms);
int task_scope_remove(struct task_scope *scope);
long long task_scope_now(void);

#endif
