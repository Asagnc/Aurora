#ifndef AURORA_MANAGED_LAUNCH_INTERNAL_H
#define AURORA_MANAGED_LAUNCH_INTERNAL_H
#include "launch.h"
struct managed_child_channels { int control[2], gate[2]; pid_t parent; };
_Noreturn void managed_launch_child(const struct managed_launch_input *input, const struct managed_child_channels *channels);
#endif
