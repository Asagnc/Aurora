/* Explicit, boot-scoped activation. Never called by ordinary Frida tasks. */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include "policy_io.h"
#if defined(__ANDROID__)
#include "frida-core.h"
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

static int exact_boot(const char *expected) {
    char value[64] = {0};
    int fd = open("/proc/sys/kernel/random/boot_id", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return 0;
    ssize_t count = read(fd, value, sizeof(value) - 1);
    close(fd);
    if (count <= 0) return 0;
    value[strcspn(value, "\r\n")] = 0;
    return strlen(expected) == 36 && !strcmp(value, expected);
}

static int exact_root(const char *type) {
    char context[4096] = {0};
    int fd = open("/proc/self/attr/current", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return 0;
    ssize_t count = read(fd, context, sizeof(context) - 1);
    close(fd);
    if (count <= 0 || strncmp(context, "u:r:", 4)) return 0;
    char *end = strchr(context + 4, ':');
    return end && (size_t)(end - context - 4) == strlen(type) && !memcmp(context + 4, type, strlen(type));
}

static int label_probe(void *library, const char *app_type) {
    int (*set_label)(int, const char *) = dlsym(library, "fsetfilecon");
    int (*get_label)(int, char **) = dlsym(library, "fgetfilecon");
    void (*free_label)(char *) = dlsym(library, "freecon");
    int (*check)(const char *, const char *, const char *, const char *, void *) = dlsym(library, "selinux_check_access");
    if (!set_label || !get_label || !free_label || !check || strlen(app_type) > 128) return -1;
    char app_context[160];
    snprintf(app_context, sizeof(app_context), "u:r:%s:s0", app_type);
    const char *target = "u:object_r:frida_memfd:s0";
    int fd = memfd_create("aurora-policy-probe", MFD_CLOEXEC);
    if (fd < 0) return -1;
    char *actual = NULL;
    int result = set_label(fd, target);
    if (!result && (get_label(fd, &actual) < 0 || !actual || strcmp(actual, target))) result = -1;
    if (actual) free_label(actual);
    close(fd);
    const char *names[] = {"open", "read", "write", "getattr", "execute", "map"};
    for (unsigned i = 0; !result && i < G_N_ELEMENTS(names); i++) {
        if (check(app_context, target, "file", names[i], NULL)) result = -1;
    }
    return result;
}

static int equal(const struct aurora_policy_image *a, const struct aurora_policy_image *b) {
    return a->size == b->size && !memcmp(a->data, b->data, a->size);
}

static int replace_current(const struct aurora_policy_image *before, const struct aurora_policy_image *candidate,
    const char *boot, int (*enforcing)(void)) {
    struct aurora_policy_image current = {0};
    int result = aurora_policy_read("/sys/fs/selinux/policy", &current);
    if (!result && (!equal(before, &current) || !exact_boot(boot) || enforcing() != 1)) result = -1;
    free(current.data);
    if (result) return -1;
    /* Cross-manager competition cannot be made atomic: selinuxfs has no CAS.
     * Do not roll back an entire policy if this one write or its audit fails. */
    int fd = open("/sys/fs/selinux/load", O_WRONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return -1;
    ssize_t count;
    do { count = write(fd, candidate->data, candidate->size); } while (count < 0 && errno == EINTR);
    close(fd);
    return count == (ssize_t)candidate->size ? 0 : -1;
}

int aurora_policy_activate(int argc, char **argv) {
    if (getuid() || geteuid()) return 77;
    /* Root-only qualification entry exercises the same one-write/readback path
     * on an already owned exact delta. Binder and Agent never select reload. */
    int reload = argc == 7 && !strcmp(argv[1], "policy-qualify-reload");
    int activate = argc == 7 && (!strcmp(argv[1], "policy-activate") || reload);
    if (argc != 7 || (!activate && strcmp(argv[1], "policy-attest"))) return 64;
    alarm(20);
    void *library = dlopen("/system/lib64/libselinux.so", RTLD_NOW | RTLD_LOCAL);
    int (*enforcing)(void) = library ? dlsym(library, "security_getenforce") : NULL;
    struct aurora_policy_image before = {0}, candidate = {0}, current = {0}, after = {0};
    struct aurora_policy_scope scope = {argv[5], argv[6]};
    int result = 1, attempted = 0, lock = -1;
    if (!enforcing || enforcing() != 1 || !exact_boot(argv[4]) || !exact_root(scope.root_type)) goto done;
    lock = open(argv[2], O_RDONLY | O_NONBLOCK | O_NOFOLLOW | O_CLOEXEC);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB)) goto done;
    if (aurora_policy_read(argv[2], &before) || aurora_policy_read(argv[3], &candidate) ||
        aurora_policy_verify(&before, &candidate, &scope) || aurora_policy_read("/sys/fs/selinux/policy", &current)) goto done;
    if (activate && equal(&before, &current)) {
        attempted = 1;
        if (reload || replace_current(&current, &candidate, argv[4], enforcing)) goto done;
    } else if (aurora_policy_verify(&before, &current, &scope)) goto done;
    else if (reload) {
        attempted = 1;
        if (replace_current(&current, &candidate, argv[4], enforcing)) goto done;
    }
    if (aurora_policy_read("/sys/fs/selinux/policy", &after) || aurora_policy_verify(&before, &after, &scope) ||
        enforcing() != 1 || !exact_boot(argv[4]) || !exact_root(scope.root_type) || label_probe(library, scope.app_type)) goto done;
    gchar *digest = g_compute_checksum_for_data(G_CHECKSUM_SHA256, after.data, after.size);
    printf("{\"protocol\":1,\"policyQualified\":true,\"kernelWriteAttempted\":%s,\"enforcing\":true,"
        "\"inverseVerified\":true,\"labelProbe\":true,\"imageSha256\":\"%s\"}\n", attempted ? "true" : "false", digest);
    g_free(digest); result = 0;
done:
    if (result) fprintf(stderr, "Policy qualification failed; kernelWriteAttempted=%d; no rollback performed\n", attempted);
    if (lock >= 0) close(lock);
    free(before.data); free(candidate.data); free(current.data); free(after.data);
    if (library) dlclose(library);
    alarm(0);
    return result;
}
#else
int aurora_policy_activate(int argc, char **argv) { (void)argc; (void)argv; return 77; }
#endif
