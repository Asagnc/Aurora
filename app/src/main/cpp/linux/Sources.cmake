include(FetchContent)
# Source archives are immutable build inputs; no prebuilt executable is fetched.
FetchContent_Declare(aurora_proot_source
    URL https://codeload.github.com/termux/proot/zip/refs/tags/v5.1.107.98
    URL_HASH SHA256=9de61bddd6f5016d065e2755b72ff4747290e5f992562f031785b107b51d02ba
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
set(AURORA_TALLOC_URL "https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz")
# CI can prefetch once with bounded HTTP/1.1 retries. The same immutable hash
# remains mandatory here, including every ABI/build type using a local archive.
if(DEFINED ENV{AURORA_TALLOC_ARCHIVE})
    set(AURORA_TALLOC_URL "$ENV{AURORA_TALLOC_ARCHIVE}")
    if(NOT IS_ABSOLUTE "${AURORA_TALLOC_URL}" OR NOT EXISTS "${AURORA_TALLOC_URL}" OR IS_DIRECTORY "${AURORA_TALLOC_URL}")
        message(FATAL_ERROR "AURORA_TALLOC_ARCHIVE must be an existing absolute archive path")
    endif()
endif()
FetchContent_Declare(aurora_talloc_source
    URL "${AURORA_TALLOC_URL}"
    URL_HASH SHA256=912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_Declare(aurora_shmem_source
    URL https://codeload.github.com/termux/libandroid-shmem/tar.gz/refs/tags/v0.7
    URL_HASH SHA256=1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_MakeAvailable(aurora_proot_source aurora_talloc_source aurora_shmem_source)
set(PROOT_SOURCE "${aurora_proot_source_SOURCE_DIR}/src")
set(LINUX_CONFIG "${CMAKE_CURRENT_BINARY_DIR}/linux-config")
file(MAKE_DIRECTORY "${LINUX_CONFIG}/sys")
configure_file("${CMAKE_CURRENT_LIST_DIR}/talloc-replace.h" "${LINUX_CONFIG}/replace.h" COPYONLY)
configure_file("${aurora_shmem_source_SOURCE_DIR}/shm.h" "${LINUX_CONFIG}/sys/shm.h" COPYONLY)

# Shared linkage permits replacement/relinking of the LGPL allocation library.
add_library(aurora_talloc SHARED "${aurora_talloc_source_SOURCE_DIR}/talloc.c")
target_include_directories(aurora_talloc PRIVATE "${LINUX_CONFIG}")
target_compile_definitions(aurora_talloc PRIVATE _GNU_SOURCE TALLOC_BUILD_VERSION_MAJOR=2
    TALLOC_BUILD_VERSION_MINOR=5 TALLOC_BUILD_VERSION_RELEASE=0 HAVE_SYS_AUXV_H HAVE_GETAUXVAL
    HAVE_CONSTRUCTOR_ATTRIBUTE HAVE_INTPTR_T HAVE_VA_COPY)
target_link_options(aurora_talloc PRIVATE -Wl,-z,relro,-z,now,-z,max-page-size=16384)

include("${CMAKE_CURRENT_LIST_DIR}/Shmem.cmake")
