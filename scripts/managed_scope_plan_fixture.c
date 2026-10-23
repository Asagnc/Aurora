/* Explicit Root host/NDK crash-window qualification, never APK content. */
#define _GNU_SOURCE
#include "scope_plan_internal.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

static int open_journal(const char *path, const char *ticket, struct managed_journal *journal, struct managed_key *key) {
    if (managed_key_init(key, "00000000-0000-0000-0000-000000000002", ticket)) return -1;
    int state = open(path, O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW);
    if (state < 0) return -1;
    int result = managed_journal_open(journal, state), saved = errno;
    close(state); errno = saved; return result;
}

static pid_t populate(const struct managed_journal *journal, const struct task_scope *scope) {
    int ready[2];
    if (pipe2(ready, O_CLOEXEC)) return -1;
    pid_t child = fork();
    if (!child) {
        close(ready[0]);
        struct managed_journal inherited = *journal; managed_journal_close(&inherited);
        if (task_scope_enter(scope) || setsid() < 0 || write(ready[1], "r", 1) != 1) _exit(80);
        close(ready[1]);
        struct task_scope copy = *scope; task_scope_close(&copy);
        close(0); close(1); close(2);
        for (;;) pause(); // Deliberately orphaned, Python Root fixture owns cleanup.
    }
    close(ready[1]);
    struct pollfd event = {.fd = ready[0], .events = POLLIN};
    unsigned char byte;
    int valid = child > 0 && poll(&event, 1, 3000) == 1 && read(ready[0], &byte, 1) == 1 && byte == 'r';
    close(ready[0]);
    if (!valid) { if (child > 0) { kill(child, SIGKILL); waitpid(child, NULL, 0); } return -1; }
    return child;
}

static int create(const char *mode, const struct managed_journal *journal, const struct managed_key *key) {
    struct task_scope scope = {.parent = -1, .directory = -1, .events = -1, .processes = -1, .kill = -1};
    int result = managed_journal_begin(journal, key);
    if (!result && !strcmp(mode, "full")) result = managed_scope_create(journal, key, &scope);
    else if (!result) {
        result = task_scope_reserve(&scope) || managed_plan_store(journal, key, &scope);
        if (!result && strcmp(mode, "before-mkdir")) result = task_scope_materialize(&scope);
    }
    if (result) { task_scope_close(&scope); return -1; }
    pid_t child = 0;
    if (!strcmp(mode, "populated")) {
        child = populate(journal, &scope);
        if (child < 0) { task_scope_close(&scope); return -1; }
    }
    if (!strcmp(mode, "partial")) {
        char name[112]; snprintf(name, sizeof(name), "%s.tmp", scope.name);
        int file = openat(journal->directory, name, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
        if (file < 0 || write(file, "bad", 3) != 3) { if (file >= 0) close(file); task_scope_close(&scope); return -1; }
        close(file);
    }
    printf("%s\n%s\n%ld\n", scope.parent_path, scope.name, (long)child); fflush(stdout);
    if (!strcmp(mode, "live")) {
        struct managed_journal copy = *journal; managed_journal_close(&copy);
        int byte = getchar(); (void)byte;
    }
    task_scope_close(&scope);
    return 0;
}

int main(int argc, char **argv) {
    if (geteuid() || (argc != 3 && argc != 4)) return 64;
    const char *ticket = argc == 4 ? argv[3] : "1111111111111111111111111111111111111111111111111111111111111111";
    struct managed_journal journal; struct managed_key key;
    if (open_journal(argv[2], ticket, &journal, &key)) { printf("error %d\n", errno); return 0; }
    int result;
    if (!strcmp(argv[1], "recover")) {
        result = managed_journal_retire(&journal, &key) || scope_state_recover(journal.directory) || managed_scope_plan_recover(&journal, &key);
        printf("%s %d\n", result ? "error" : "recovered", result ? errno : 0);
    } else if (!strcmp(argv[1], "replay")) {
        struct task_scope scope;
        result = managed_scope_create(&journal, &key, &scope);
        printf("%s %d\n", result ? "error" : "created", result ? errno : 0);
        task_scope_close(&scope);
    } else {
        result = create(argv[1], &journal, &key);
        if (result) printf("error %d\n", errno);
    }
    /* live mode already closed only its copied FD numbers. */
    if (strcmp(argv[1], "live")) managed_journal_close(&journal);
    return 0;
}
