/* Host-only link wrapper: exercise short-write handling without sending a partial frame. */
#include <pcap/pcap.h>
#include <unistd.h>

int __wrap_pcap_inject(pcap_t *pcap, const void *frame, size_t length) {
    static unsigned calls;
    (void)pcap; (void)frame;
    if (++calls != 1) _exit(99);
    return (int)(length / 2);
}
