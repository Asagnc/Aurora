#ifndef AURORA_GUEST_RUN_H
#define AURORA_GUEST_RUN_H
struct guest_run {
    const char *generation, *token, *rootfs, *workspace;
    char **argv;
    int timeout_ms;
};
int guest_run_parse(int argc, char **argv, struct guest_run *run);
int guest_run_begin(int state, const struct guest_run *run);
int guest_run_finish(int state, int exit_code, int interrupted);
int guest_run_exec(const struct guest_run *run);
#endif
