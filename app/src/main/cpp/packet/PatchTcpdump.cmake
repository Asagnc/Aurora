# Apply only to a disposable derived source tree; fail on upstream drift.
file(READ "${SOURCE_DIR}/tcpdump.c" source)
function(replace_checked needle replacement expected)
    string(REPLACE "${needle}" "" removed "${source}")
    string(LENGTH "${source}" before)
    string(LENGTH "${removed}" after)
    string(LENGTH "${needle}" width)
    math(EXPR count "(${before} - ${after}) / ${width}")
    if(NOT count EQUAL expected)
        message(FATAL_ERROR "tcpdump write-boundary patch no longer applies: ${count}/${expected}")
    endif()
    string(REPLACE "${needle}" "${replacement}" patched "${source}")
    set(source "${patched}" PARENT_SCOPE)
endfunction()
# APK executables run under Android's app seccomp policy: even no-op setgid/setuid is forbidden.
# Only skip the offline reset for an already unprivileged app identity; preserve set-ID/Root handling.
replace_checked("if (setgid(getgid()) != 0 || setuid(getuid()) != 0 )"
    "#if defined(__ANDROID__)\n\t\tif (getuid() < 10000 || getuid() != geteuid() || getgid() != getegid())\n#endif\n\t\tif (setgid(getgid()) != 0 || setuid(getuid()) != 0 )" 1)
replace_checked("static pcap_dumper_t *pdd = NULL;"
    "static pcap_dumper_t *pdd = NULL;\n#include \"aurora-dump-write.h\"" 1)
replace_checked("exit_tcpdump(int status)\n{\n\tnd_cleanup();"
    "exit_tcpdump(int status)\n{\n\tif (pdd != NULL && aurora_close_dump(pdd) < 0) {\n\t\tfprintf(stderr, \"Aurora: capture write/flush/sync failed\\n\");\n\t\tstatus = S_ERR_HOST_PROGRAM;\n\t}\n\tnd_cleanup();" 1)
replace_checked("pcap_dump_close(dump_info->pdd);"
    "if (aurora_close_dump(dump_info->pdd) < 0)\n\t\t\t\terror(\"capture write/flush/sync failed\");" 2)
foreach(open IN ITEMS "pcap_dump_fopen(dump_info->pd, fp)"
        "pcap_dump_open(dump_info->pd, dump_info->CurrentFileName)")
    replace_checked("dump_info->pdd = ${open};" "dump_info->pdd = ${open};\n\t\t\tpdd = dump_info->pdd;" 2)
endforeach()
replace_checked("pcap_dump((u_char *)dump_info->pdd, h, sp);"
    "pcap_dump((u_char *)dump_info->pdd, h, sp);\n\tif (ferror(pcap_dump_file(dump_info->pdd)))\n\t\terror(\"capture write failed\");" 2)
replace_checked("pcap_dump_flush(dump_info->pdd);"
    "if (pcap_dump_flush(dump_info->pdd) < 0)\n\t\terror(\"capture flush failed\");" 2)
file(WRITE "${SOURCE_DIR}/tcpdump.c" "${source}")
configure_file("${CMAKE_CURRENT_LIST_DIR}/DumpWrite.h" "${SOURCE_DIR}/aurora-dump-write.h" COPYONLY)
