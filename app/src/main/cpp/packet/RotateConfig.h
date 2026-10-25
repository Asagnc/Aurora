#ifndef AURORA_ROTATE_CONFIG_H
#define AURORA_ROTATE_CONFIG_H
#include <stdint.h>
#include <stddef.h>
#include "../frida/managed_control.h"

struct aurora_capture_config {
    uint64_t segment_bytes, bytes, packets, root_ticks;
    unsigned seconds, segments, root_pid;
    char run[37], owner[37], boot[37], epoch[37], device[65], filter[4097];
};
int aurora_capture_config_decode(const unsigned char *data, size_t size, struct aurora_capture_config *config);
int aurora_capture_config_fd(int fd, struct aurora_capture_config *config);
int aurora_capture_receive(int argc, char **argv, struct aurora_managed_control *control, struct aurora_capture_config *config);
int aurora_capture_report(struct aurora_managed_control *control, int complete, int status);
#endif
