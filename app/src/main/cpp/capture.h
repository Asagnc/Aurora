#pragma once
#include <stdbool.h>
#include <stdint.h>
typedef struct {
    int input;
    int output;
    uint64_t total;
    uint64_t stored;
    bool complete;
} capture_stream;
int collect_command(int child, int signals, capture_stream streams[2], bool *stopped);
