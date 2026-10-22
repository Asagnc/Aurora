# Resolve only the just-built target-ABI library, never host pkg-config output.
include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(PCAP DEFAULT_MSG PCAP_INCLUDE_DIR PCAP_LIBRARY)
if(NOT EXISTS "${PCAP_LIBRARY}" OR NOT EXISTS "${PCAP_INCLUDE_DIR}/pcap.h")
    message(FATAL_ERROR "Pinned target libpcap was not installed")
endif()
set(PCAP_INCLUDE_DIRS "${PCAP_INCLUDE_DIR}")
set(PCAP_LIBRARIES "${PCAP_LIBRARY}")
set(PCAP_STATIC_LIBRARIES "${PCAP_LIBRARY}")
