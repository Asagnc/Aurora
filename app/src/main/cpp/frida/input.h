#ifndef AURORA_FRIDA_INPUT_H
#define AURORA_FRIDA_INPUT_H
#include <stdint.h>
#include <sys/types.h>

struct frida_attach_input {
    pid_t pid;
    uint64_t start_ticks;
    unsigned duration_ms;
    char *script;
};
int frida_input_read(int argc, char **argv, struct frida_attach_input *input);
int frida_target_matches(const struct frida_attach_input *input);
#endif
