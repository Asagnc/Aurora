#ifndef AURORA_POLICY_IO_H
#define AURORA_POLICY_IO_H
#include "policy_edit.h"
int aurora_policy_read(const char *path, struct aurora_policy_image *image);
int aurora_policy_write(const char *path, const struct aurora_policy_image *image);
int aurora_policy_activate(int argc, char **argv);
#endif
