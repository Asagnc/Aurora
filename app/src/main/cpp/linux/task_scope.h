#ifndef AURORA_TASK_SCOPE_H
#define AURORA_TASK_SCOPE_H
#include <stdint.h>

struct scope_identity { uint64_t parent_dev, parent_ino, scope_dev, scope_ino; };

struct task_scope {
    int parent, directory, processes, events, kill;
    char name[96];
    char parent_path[8192];
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
int scope_open_directory(const char *absolute);
int task_scope_identity(const struct task_scope *scope, struct scope_identity *identity);
int task_scope_reopen(struct task_scope *scope, const char *parent, const char *name, const struct scope_identity *identity);
void task_scope_close(struct task_scope *scope);
int task_scope_name_valid(const char *name);

#endif
