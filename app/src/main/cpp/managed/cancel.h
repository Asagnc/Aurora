#ifndef AURORA_MANAGED_CANCEL_H
#define AURORA_MANAGED_CANCEL_H
/* Standalone single-thread coordinator only. Returns owned cancellation event
 * endpoint; the signal handler owns its nonblocking peer until close/exit. */
int managed_cancel_open(void);
void managed_cancel_child(void);
void managed_cancel_close(int reader);
#endif
