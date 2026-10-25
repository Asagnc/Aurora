#include "RotateWorker.h"

pcap_t *aurora_capture_source(const struct aurora_capture_config *config) {
    char error[PCAP_ERRBUF_SIZE];
    pcap_t *source = pcap_create(config->device, error);
    if (!source) return NULL;
    if (pcap_set_snaplen(source, 262144) || pcap_set_promisc(source, 0) || pcap_set_timeout(source, 100) ||
        pcap_set_buffer_size(source, 4 * 1024 * 1024) || pcap_set_immediate_mode(source, 0) || pcap_activate(source) < 0 ||
        pcap_get_selectable_fd(source) < 0 || pcap_get_required_select_timeout(source) != NULL || pcap_setnonblock(source, 1, error) < 0) goto failed;
    if (*config->filter) {
        struct bpf_program program;
        if (pcap_compile(source, &program, config->filter, 1, PCAP_NETMASK_UNKNOWN) < 0) goto failed;
        int result = pcap_setfilter(source, &program); pcap_freecode(&program);
        if (result < 0) goto failed;
    }
    return source;
failed:
    pcap_close(source); return NULL;
}
