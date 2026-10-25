#ifndef AURORA_FRIDA_INPUT_H
#define AURORA_FRIDA_INPUT_H
#include <stdint.h>
#include <sys/types.h>

struct frida_attach_input {
    pid_t pid;
    uint64_t start_ticks;
    unsigned duration_ms;
    char *script;
    char *payload;
    char *spawn_program;
    unsigned char ticket[32];
};
int frida_input_read(int argc, char **argv, struct frida_attach_input *input);
int frida_channel_read(int argc, char **argv, struct frida_attach_input *input, int cancel_fd);
int frida_process_start(pid_t pid, uint64_t *ticks);
int frida_target_matches(const struct frida_attach_input *input);
#endif
