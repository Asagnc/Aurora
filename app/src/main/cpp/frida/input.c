#define _GNU_SOURCE
#include "input.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static int integer(const char *text, uint64_t maximum, uint64_t *value) {
    if (!text || !*text || strspn(text, "0123456789") != strlen(text)) return -1;
    errno = 0;
    char *end;
    unsigned long long parsed = strtoull(text, &end, 10);
    if (errno || *end || !parsed || parsed > maximum) return -1;
    *value = parsed;
    return 0;
}

static char *read_script(const char *path) {
    if (path[0] != '/') return NULL;
    int file = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (file < 0) return NULL;
    struct stat before, after;
    char *text = NULL;
    if (fstat(file, &before) || !S_ISREG(before.st_mode) || before.st_size < 1 || before.st_size > 1048576) goto done;
    text = malloc((size_t) before.st_size + 1);
    if (!text) goto done;
    size_t used = 0;
    while (used < (size_t) before.st_size) {
        ssize_t count = read(file, text + used, (size_t) before.st_size - used);
        if (count < 0 && errno == EINTR) continue;
        if (count <= 0) goto invalid;
        used += (size_t) count;
    }
    text[used] = 0;
    if (memchr(text, 0, used) || fstat(file, &after) || before.st_size != after.st_size ||
        before.st_mtim.tv_sec != after.st_mtim.tv_sec || before.st_mtim.tv_nsec != after.st_mtim.tv_nsec) goto invalid;
    goto done;
invalid:
    free(text); text = NULL;
done:
    close(file);
    return text;
}

int frida_input_read(int argc, char **argv, struct frida_attach_input *input) {
    uint64_t pid, ticks, duration;
    memset(input, 0, sizeof(*input));
    if ((argc != 6 && argc != 7) || strcmp(argv[1], "attach") || integer(argv[2], INT_MAX, &pid) ||
        integer(argv[3], UINT64_MAX, &ticks) || integer(argv[5], 250000, &duration)) return -1;
    char *script = read_script(argv[4]);
    if (!script) return -1;
    char *payload = argc == 7 ? read_script(argv[6]) : NULL;
    if (argc == 7 && !payload) { free(script); return -1; }
    input->payload = payload;
    input->pid = (pid_t) pid;
    input->start_ticks = ticks;
    input->duration_ms = (unsigned) duration;
    input->script = script;
    return 0;
}

int frida_target_matches(const struct frida_attach_input *input) {
    char path[64], buffer[65537];
    snprintf(path, sizeof(path), "/proc/%ld/stat", (long) input->pid);
    int file = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (file < 0) return 0;
    ssize_t count;
    do { count = read(file, buffer, sizeof(buffer) - 1); } while (count < 0 && errno == EINTR);
    close(file);
    if (count <= 0 || count >= (ssize_t) sizeof(buffer) - 1) return 0;
    buffer[count] = 0;
    char *end = strrchr(buffer, ')');
    if (!end) return 0;
    char *state, *token = strtok_r(end + 1, " \t\n", &state);
    for (int index = 0; index < 19 && token; index++) token = strtok_r(NULL, " \t\n", &state);
    uint64_t ticks;
    return token && integer(token, UINT64_MAX, &ticks) == 0 && ticks == input->start_ticks;
}
