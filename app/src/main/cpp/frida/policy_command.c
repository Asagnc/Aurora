/* Pure plan/verify commands and a separately named explicit activation path. */
#include "policy_edit.h"
#include "policy_io.h"
#if defined(__ANDROID__)
#include "frida-core.h"
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

int aurora_policy_command(int argc, char **argv) {
    if (getuid() != 0 || geteuid() != 0) return 77;
    if (argc == 7) return aurora_policy_activate(argc, argv);
    if (argc != 6) return 64;
    int plan = !strcmp(argv[1], "policy-plan"), verify = !strcmp(argv[1], "policy-verify");
    if (!plan && !verify) return 64;
    alarm(20);
    struct aurora_policy_image before = {0}, other = {0};
    struct aurora_policy_scope scope = {.root_type = argv[4], .app_type = argv[5]};
    int result = 1;
    if (aurora_policy_read(argv[2], &before)) goto done;
    if (plan) {
        if (aurora_policy_prepare(&before, &scope, &other) || aurora_policy_write(argv[3], &other)) goto done;
    } else if (aurora_policy_read(argv[3], &other) || aurora_policy_verify(&before, &other, &scope)) goto done;
    gchar *digest = g_compute_checksum_for_data(G_CHECKSUM_SHA256, other.data, other.size);
    printf("{\"protocol\":1,\"policyQualified\":false,\"kernelWritten\":false,\"inverseVerified\":true,\"imageSha256\":\"%s\",\"imageBytes\":%zu}\n", digest, other.size);
    g_free(digest); result = 0;
done:
    free(before.data); free(other.data);
    alarm(0);
    return result;
}
#else
int aurora_policy_command(int argc, char **argv) { (void)argc; (void)argv; return 77; }
#endif
