/* Aurora's checked dump boundary; included after tcpdump's current pdd. */
#pragma once
#include <sys/stat.h>
#include <unistd.h>

static int aurora_close_dump(pcap_dumper_t *dump) {
    FILE *stream = pcap_dump_file(dump);
    int failed = 0;
    struct stat info;
    /* The existing flush signal must not use a closing or freed dumper. */
    if (pdd == dump) pdd = NULL;
    if (pcap_dump_flush(dump) < 0 || ferror(stream)) failed = 1;
    if (!failed && (fstat(fileno(stream), &info) < 0 ||
        (S_ISREG(info.st_mode) && fsync(fileno(stream)) < 0))) failed = 1;
    pcap_dump_close(dump);
    return failed ? -1 : 0;
}
