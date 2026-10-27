include(FetchContent)
include(ExternalProject)
# Official stable release archives; source-only population avoids importing
# upstream project-wide options into Aurora's native target namespace.
FetchContent_Declare(aurora_pcap_source
    URL https://www.tcpdump.org/release/libpcap-1.10.7.tar.xz
    URL_HASH SHA256=68fa62cffb974f4275641ce14c2e2d75739251f30e00e6a0900903b247d76a03
    SOURCE_SUBDIR aurora-source-only DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_Declare(aurora_tcpdump_source
    URL https://www.tcpdump.org/release/tcpdump-4.99.7.tar.xz
    URL_HASH SHA256=39675dfa06fb0e99be8da5d4a1784acbaab58ae0870231d956ff7870cb99eec1
    SOURCE_SUBDIR aurora-source-only DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_MakeAvailable(aurora_pcap_source aurora_tcpdump_source)

find_program(AURORA_LEX NAMES flex win_flex REQUIRED)
find_program(AURORA_YACC NAMES bison win_bison REQUIRED)
set(capture_prefix "${CMAKE_CURRENT_BINARY_DIR}/packet-prefix")
set(capture_module "${CMAKE_CURRENT_LIST_DIR}")
set(capture_tmp "${CMAKE_CURRENT_BINARY_DIR}/packet-tmp")
file(MAKE_DIRECTORY "${capture_tmp}")
# win_flex uses fixed names in TEMP; parallel ABI builds need private temp dirs.
set(capture_env "${CMAKE_COMMAND}" -E env "TEMP=${capture_tmp}" "TMP=${capture_tmp}")
set(pcap_source "${aurora_pcap_source_SOURCE_DIR}")
set(tcpdump_source "${CMAKE_CURRENT_BINARY_DIR}/packet-tcpdump-source")
file(COPY "${aurora_tcpdump_source_SOURCE_DIR}/" DESTINATION "${tcpdump_source}")
configure_file("${capture_module}/FindPCAP.cmake" "${tcpdump_source}/cmake/Modules/FindPCAP.cmake" COPYONLY)
set(SOURCE_DIR "${tcpdump_source}")
include("${capture_module}/PatchTcpdump.cmake")
if(CMAKE_HOST_WIN32)
    # Upstream's generator runs on the host even while targeting Android.
    # Use a derived tree; never change the verified cached upstream sources.
    set(pcap_source "${CMAKE_CURRENT_BINARY_DIR}/packet-source")
    file(COPY "${aurora_pcap_source_SOURCE_DIR}/" DESTINATION "${pcap_source}")
    file(READ "${pcap_source}/CMakeLists.txt" pcap_cmake)
    set(null_check "if(WIN32)\n    set(NULL_DEVICE \"NUL:\")")
    string(FIND "${pcap_cmake}" "${null_check}" position)
    if(position EQUAL -1)
        message(FATAL_ERROR "libpcap host generator patch no longer applies")
    endif()
    string(REPLACE "${null_check}" "if(CMAKE_HOST_WIN32)\n    set(NULL_DEVICE \"NUL:\")" pcap_cmake "${pcap_cmake}")
    file(WRITE "${pcap_source}/CMakeLists.txt" "${pcap_cmake}")
endif()

set(capture_args
    "-DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}" "-DANDROID_ABI=${ANDROID_ABI}"
    "-DANDROID_PLATFORM=android-36" "-DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}"
    "-DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}" "-DCMAKE_INSTALL_PREFIX=${capture_prefix}"
    "-DCMAKE_INSTALL_LIBDIR=lib" "-DCMAKE_POSITION_INDEPENDENT_CODE=ON"
    "-DCMAKE_C_FLAGS=-fstack-protector-strong"
    "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384")
ExternalProject_Add(aurora_pcap_build
    SOURCE_DIR "${pcap_source}" BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/packet-pcap"
    DOWNLOAD_COMMAND "" UPDATE_COMMAND ""
    CONFIGURE_COMMAND ${capture_env} "${CMAKE_COMMAND}" -G "${CMAKE_GENERATOR}"
        -S <SOURCE_DIR> -B <BINARY_DIR> ${capture_args} -DBUILD_SHARED_LIBS=OFF -DPCAP_TYPE=linux
        -DBUILD_WITH_LIBNL=OFF -DENABLE_REMOTE=OFF -DDISABLE_BLUETOOTH=ON
        -DDISABLE_DBUS=ON -DDISABLE_RDMA=ON -DDISABLE_DAG=ON -DDISABLE_SNF=ON
        -DDISABLE_NETMAP=ON -DDISABLE_LINUX_USBMON=ON
        "-DLEX_EXECUTABLE=${AURORA_LEX}" "-DYACC_EXECUTABLE=${AURORA_YACC}"
    BUILD_COMMAND ${capture_env} "${CMAKE_COMMAND}" --build <BINARY_DIR> --target pcap_static
    INSTALL_COMMAND "${CMAKE_COMMAND}" --install <BINARY_DIR>
    BUILD_BYPRODUCTS "${capture_prefix}/lib/libpcap.a")
ExternalProject_Add(aurora_tcpdump_build
    BUILD_ALWAYS TRUE
    SOURCE_DIR "${tcpdump_source}" BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/packet-program"
    DOWNLOAD_COMMAND "" UPDATE_COMMAND "" DEPENDS aurora_pcap_build
    CONFIGURE_COMMAND ${capture_env} "${CMAKE_COMMAND}" -G "${CMAKE_GENERATOR}"
        -S <SOURCE_DIR> -B <BINARY_DIR> ${capture_args}
        -DWITH_CRYPTO=OFF -DWITH_SMI=OFF -DWITH_CAP_NG=OFF -DWITH_CAPSICUM=OFF
        "-DCMAKE_MODULE_PATH=${capture_module}"
        "-DPCAP_INCLUDE_DIR=${capture_prefix}/include" "-DPCAP_LIBRARY=${capture_prefix}/lib/libpcap.a"
        "-DPCAP_STATIC_LIBRARY=${capture_prefix}/lib/libpcap.a"
    BUILD_COMMAND "${CMAKE_COMMAND}" --build <BINARY_DIR> --target tcpdump
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS "${CMAKE_CURRENT_BINARY_DIR}/packet-program/tcpdump")
add_custom_target(aurora_tcpdump ALL
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}"
    COMMAND "${CMAKE_COMMAND}" -E copy_if_different
        "${CMAKE_CURRENT_BINARY_DIR}/packet-program/tcpdump" "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/aurora_tcpdump"
    DEPENDS aurora_tcpdump_build
    BYPRODUCTS "${CMAKE_RUNTIME_OUTPUT_DIRECTORY}/aurora_tcpdump")
add_executable(aurora_packet_worker "${capture_module}/Worker.c" "${capture_module}/WorkerLoop.c")
add_dependencies(aurora_packet_worker aurora_pcap_build)
target_include_directories(aurora_packet_worker PRIVATE "${capture_prefix}/include")
target_link_libraries(aurora_packet_worker PRIVATE "${capture_prefix}/lib/libpcap.a")
target_compile_options(aurora_packet_worker PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_options(aurora_packet_worker PRIVATE -Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384)
add_executable(aurora_packet_inject "${capture_module}/Inject.c" "${capture_module}/Select.c")
add_dependencies(aurora_packet_inject aurora_pcap_build)
target_include_directories(aurora_packet_inject PRIVATE "${capture_prefix}/include")
target_link_libraries(aurora_packet_inject PRIVATE "${capture_prefix}/lib/libpcap.a")
target_compile_options(aurora_packet_inject PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_options(aurora_packet_inject PRIVATE -Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384)
add_executable(aurora_socket_diag "${capture_module}/SocketDiag.c")
target_compile_options(aurora_socket_diag PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_options(aurora_socket_diag PRIVATE -Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384)
add_executable(aurora_packet_rotate
    "${capture_module}/RotateMain.c" "${capture_module}/RotateSource.c" "${capture_module}/RotateLoop.c"
    "${capture_module}/RotateStore.c" "${capture_module}/RotateSeal.c" "${capture_module}/RotateChannel.c"
    "${capture_module}/RotateConfig.c" "${capture_module}/Sha256.c" "${capture_module}/RotateStats.c"
    "${capture_module}/../frida/input.c" "${capture_module}/../frida/managed_control.c"
    "${capture_module}/../frida/fd_channel.c" "${capture_module}/../frida/fd_io.c")
add_dependencies(aurora_packet_rotate aurora_pcap_build)
target_include_directories(aurora_packet_rotate PRIVATE "${capture_prefix}/include")
target_link_libraries(aurora_packet_rotate PRIVATE "${capture_prefix}/lib/libpcap.a")
target_compile_options(aurora_packet_rotate PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_options(aurora_packet_rotate PRIVATE -Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384)
