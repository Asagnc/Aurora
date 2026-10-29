/* Explicit, temporary device qualification only; never packaged in Aurora.
 * No permissive switch, persistent module or whole-policy rollback.
 */
#include "frida-core.h"
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#define LIMIT (64U * 1024U * 1024U)
struct policy_image { unsigned char *data; size_t size; char *sha256; };

static int read_image(const char *path, struct policy_image *image) {
    FILE *file = fopen(path, "rb");
    if (!file) return -1;
    image->data = malloc(LIMIT + 1);
    if (!image->data) { fclose(file); return -1; }
    image->size = fread(image->data, 1, LIMIT + 1, file);
    int failed = ferror(file) || !image->size || image->size > LIMIT;
    fclose(file);
    if (failed) return -1;
    image->sha256 = g_compute_checksum_for_data(G_CHECKSUM_SHA256, image->data, image->size);
    return image->sha256 ? 0 : -1;
}

static int exact_boot(const char *expected) {
    char boot[64] = {0};
    FILE *file = fopen("/proc/sys/kernel/random/boot_id", "r");
    if (!file) return 0;
    char *result = fgets(boot, sizeof(boot), file);
    fclose(file);
    boot[strcspn(boot, "\r\n")] = 0;
    return result && strlen(expected) == 36 && !strcmp(boot, expected);
}

static int label_probe(void *library) {
    int (*set_label)(int, const char *) = dlsym(library, "fsetfilecon");
    int (*check)(const char *, const char *, const char *, const char *, void *) = dlsym(library, "selinux_check_access");
    if (!set_label || !check) return -1;
    int fd = memfd_create("aurora-owned-policy-qualification", MFD_CLOEXEC);
    if (fd < 0) return -1;
    int labeled = set_label(fd, "u:object_r:frida_memfd:s0");
    close(fd);
    if (labeled) return -1;
    const char *permissions[] = {"open", "read", "write", "getattr", "execute", "map"};
    for (unsigned i = 0; i < G_N_ELEMENTS(permissions); i++) {
        if (check("u:r:untrusted_app:s0", "u:object_r:frida_memfd:s0", "file", permissions[i], NULL)) return -1;
    }
    errno = 0;
    int generic = check("u:r:untrusted_app:s0", "u:object_r:tmpfs:s0", "file", "execute", NULL);
    return generic == -1 && errno == EACCES ? 0 : -1;
}

static int load(const char *path, const char *before_sha, const char *candidate_sha, const char *boot) {
    struct policy_image candidate = {0}, before = {0}, after = {0};
    void *library = dlopen("/system/lib64/libselinux.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return 2;
    int (*enforcing)(void) = dlsym(library, "security_getenforce");
    if (!enforcing || enforcing() != 1 || !exact_boot(boot)) return 3;
    if (read_image(path, &candidate) || strcmp(candidate.sha256, candidate_sha)) return 4;
    if (read_image("/sys/fs/selinux/policy", &before) || strcmp(before.sha256, before_sha)) return 5;
    /* Best-effort identity/competition check immediately before the one load.
     * selinuxfs offers no cross-manager compare-and-swap operation. */
    if (!exact_boot(boot) || enforcing() != 1) return 6;
    int fd = open("/sys/fs/selinux/load", O_WRONLY | O_CLOEXEC);
    if (fd < 0) return 7;
    ssize_t count;
    do { count = write(fd, candidate.data, candidate.size); } while (count < 0 && errno == EINTR);
    int write_error = errno;
    close(fd);
    int readable = !read_image("/sys/fs/selinux/policy", &after);
    int still_enforcing = enforcing();
    int exact = readable && !strcmp(after.sha256, candidate.sha256);
    int probe = count == (ssize_t) candidate.size && exact && still_enforcing == 1 ? label_probe(library) : -1;
    printf("{\"writeBytes\":%zd,\"writeErrno\":%d,\"enforcing\":%d,\"exactCandidate\":%s,\"labelProbe\":%s}\n",
        count, count < 0 ? write_error : 0, still_enforcing, exact ? "true" : "false", probe == 0 ? "true" : "false");
    free(candidate.data); free(before.data); free(after.data);
    g_free(candidate.sha256); g_free(before.sha256); g_free(after.sha256);
    dlclose(library);
    return probe == 0 ? 0 : 8;
}

int main(int argc, char **argv) {
    if (argc == 2 && !strcmp(argv[1], "probe-current") && getuid() == 0) {
        void *library = dlopen("/system/lib64/libselinux.so", RTLD_NOW | RTLD_LOCAL);
        if (!library) return 2;
        int (*enforcing)(void) = dlsym(library, "security_getenforce");
        int mode = enforcing ? enforcing() : -1;
        int probe = mode == 1 ? label_probe(library) : -1;
        printf("{\"enforcing\":%d,\"labelProbe\":%s}\n", mode, probe == 0 ? "true" : "false");
        dlclose(library);
        return probe == 0 ? 0 : 8;
    }
    if (argc != 6 || strcmp(argv[1], "apply-qualified-candidate") || getuid() != 0) return 64;
    alarm(20);
    return load(argv[2], argv[3], argv[4], argv[5]);
}
