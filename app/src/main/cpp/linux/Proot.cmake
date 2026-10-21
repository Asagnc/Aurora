include("${CMAKE_CURRENT_LIST_DIR}/Sources.cmake")
include(CheckCSourceCompiles)
foreach(feature IN ITEMS process_vm seccomp_filter)
    file(READ "${PROOT_SOURCE}/.check_${feature}.c" check_source)
    string(TOUPPER "${feature}" upper)
    set(CMAKE_REQUIRED_DEFINITIONS -D_GNU_SOURCE)
    check_c_source_compiles("${check_source}" HAVE_${upper})
    if(HAVE_${upper})
        string(APPEND feature_header "#define HAVE_${upper} 1\n")
    endif()
endforeach()
file(WRITE "${LINUX_CONFIG}/build.h" "${feature_header}")

# Derive layout from the pinned upstream architecture header, including its
# Android 39-bit address-space fix. Never reuse an older loader's fixed address.
execute_process(COMMAND "${CMAKE_C_COMPILER}" ${CMAKE_C_COMPILER_ARG1}
    --target=${CMAKE_C_COMPILER_TARGET} --sysroot=${CMAKE_SYSROOT}
    -E -dM -DNO_LIBC_HEADER "${PROOT_SOURCE}/arch.h"
    OUTPUT_VARIABLE arch_macros COMMAND_ERROR_IS_FATAL ANY)
string(REGEX MATCH "#define LOADER_ADDRESS ([0-9a-fA-Fx]+)" found "${arch_macros}")
if(NOT found)
    message(FATAL_ERROR "Missing native PRoot loader address")
endif()
set(loader_address "${CMAKE_MATCH_1}")
enable_language(ASM)
add_executable(aurora_proot_loader "${PROOT_SOURCE}/loader/loader.c" "${PROOT_SOURCE}/loader/assembly.S")
target_include_directories(aurora_proot_loader PRIVATE "${PROOT_SOURCE}")
target_compile_options(aurora_proot_loader PRIVATE -fPIC -ffreestanding -fno-stack-protector)
target_compile_definitions(aurora_proot_loader PRIVATE _GNU_SOURCE)
target_link_options(aurora_proot_loader PRIVATE -static -nostdlib
    "-Wl,--build-id=none,-Ttext=${loader_address},--rosegment,-z,noexecstack,-z,max-page-size=16384")
if(arch_macros MATCHES "#define HAS_POKEDATA_WORKAROUND")
    # The tracer addresses this entry externally; Android Release section GC
    # cannot infer that reference from the freestanding loader's call graph.
    target_link_options(aurora_proot_loader PRIVATE -Wl,--undefined=pokedata_workaround)
endif()

# Keep upstream's explicit object list instead of globbing tests or unused files.
file(READ "${PROOT_SOURCE}/GNUmakefile" upstream_make)
string(REGEX MATCH "OBJECTS \\+=([^$]+)define define_from_arch" object_section "${upstream_make}")
string(REGEX MATCHALL "[a-zA-Z0-9_/-]+\\.o" objects "${object_section}")
if(NOT objects)
    message(FATAL_ERROR "PRoot object list changed")
endif()
foreach(object IN LISTS objects)
    string(REGEX REPLACE "\\.o$" ".c" source "${object}")
    if(source STREQUAL "extension/sysvipc/sysvipc_shm.c")
        file(READ "${PROOT_SOURCE}/${source}" ipc_source)
        string(FIND "${ipc_source}" "assert(!\"No pending mapping found\");" position)
        if(position EQUAL -1)
            message(FATAL_ERROR "PRoot pending-mapping patch no longer applies")
        endif()
        # Keep the impossible-state failure in Release too: assert disappears
        # under NDEBUG, otherwise this non-void function falls through into UB.
        string(REPLACE "assert(!\"No pending mapping found\");" "abort();" ipc_source "${ipc_source}")
        file(WRITE "${LINUX_CONFIG}/sysvipc_shm.c" "${ipc_source}")
        list(APPEND proot_sources "${LINUX_CONFIG}/sysvipc_shm.c")
    else()
        list(APPEND proot_sources "${PROOT_SOURCE}/${source}")
    endif()
endforeach()
if(arch_macros MATCHES "#define HAS_POKEDATA_WORKAROUND")
    set(loader_info "${LINUX_CONFIG}/loader-info.c")
    add_custom_command(OUTPUT "${loader_info}"
        COMMAND "${CMAKE_COMMAND}" "-DREADELF=${CMAKE_READELF}"
            "-DLOADER=$<TARGET_FILE:aurora_proot_loader>" "-DOUTPUT=${loader_info}"
            -P "${CMAKE_CURRENT_LIST_DIR}/LoaderInfo.cmake"
        DEPENDS aurora_proot_loader "${CMAKE_CURRENT_LIST_DIR}/LoaderInfo.cmake" VERBATIM)
    list(APPEND proot_sources "${loader_info}")
endif()
add_executable(aurora_proot ${proot_sources})
# Upstream ashmem extension omits its C string declarations; retain Clang's
# implicit-declaration error checks and supply the missing standard header.
set_source_files_properties("${PROOT_SOURCE}/extension/ashmem_memfd/ashmem_memfd.c"
    PROPERTIES COMPILE_OPTIONS "-include;string.h")
add_dependencies(aurora_proot aurora_proot_loader)
target_include_directories(aurora_proot PRIVATE "${LINUX_CONFIG}" "${PROOT_SOURCE}" "${aurora_talloc_source_SOURCE_DIR}"
    "${PROOT_SOURCE}/extension/sysvipc")
target_compile_definitions(aurora_proot PRIVATE _GNU_SOURCE _FILE_OFFSET_BITS=64 ARG_MAX=131072
    VERSION="5.1.107.98" PROOT_UNBUNDLE_LOADER="/aurora-unavailable" WITH_LIBANDROID_SHMEM)
target_compile_options(aurora_proot PRIVATE -fstack-protector-strong)
target_link_options(aurora_proot PRIVATE -Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384)
target_link_libraries(aurora_proot PRIVATE aurora_talloc aurora_shmem)
