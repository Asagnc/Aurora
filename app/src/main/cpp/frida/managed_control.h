#ifndef AURORA_FRIDA_MANAGED_CONTROL_H
#define AURORA_FRIDA_MANAGED_CONTROL_H
#include <stdint.h>

#define AURORA_MANAGED_FACT_BYTES 52
#define AURORA_FACT_FINAL 1
#define AURORA_FACT_ATTACH_ATTEMPTED 2
#define AURORA_FACT_LOADED 4
#define AURORA_FACT_UNLOADED 8
#define AURORA_FACT_DETACHED 16
#define AURORA_FACT_MANAGER_CLOSED 32
#define AURORA_FACT_SUCCESS 64
#define AURORA_FACT_NO_SESSION (AURORA_FACT_FINAL | AURORA_FACT_UNLOADED | AURORA_FACT_DETACHED | AURORA_FACT_MANAGER_CLOSED)
struct aurora_managed_control { int fd; unsigned char ticket[32]; };
struct aurora_managed_facts { unsigned flags; };
struct aurora_managed_receipt { unsigned flags, status; };
struct aurora_managed_worker { uint32_t pid; uint64_t ticks; };
/* Valid worker facts are still NOT proof of worker exit or cgroup removal. */
int aurora_managed_decode(const unsigned char frame[AURORA_MANAGED_FACT_BYTES], const unsigned char ticket[32],
    const struct aurora_managed_worker *worker, struct aurora_managed_receipt *receipt);
/* Capture facts deliberately contain no Frida attach/unload/detach/manager bits. */
int aurora_capture_decode(const unsigned char frame[AURORA_MANAGED_FACT_BYTES], const unsigned char ticket[32],
    const struct aurora_managed_worker *worker, struct aurora_managed_receipt *receipt);
/* Only the native parent may deliberately inherit this private stream across exec.
 * Bootstrap restores CLOEXEC before Frida can launch helpers. No Java FD inheritance assumption. */
int aurora_managed_open(struct aurora_managed_control *control);
int aurora_managed_report(struct aurora_managed_control *control, const struct aurora_managed_facts *facts, int status);
void aurora_managed_close(struct aurora_managed_control *control);
#endif
