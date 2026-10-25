#ifndef AURORA_ROTATE_WORKER_H
#define AURORA_ROTATE_WORKER_H
#include "RotateStore.h"
pcap_t *aurora_capture_source(const struct aurora_capture_config *config);
/* Returns a fixed reason. Failure and cancellation are separate from sealed-prefix availability. */
const char *aurora_capture_rotate_loop(struct aurora_capture_store *store, int control, int signals, int timer);
#endif
