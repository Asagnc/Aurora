# Keep the pinned source unmodified. Build a derived copy with checked edits.
file(READ "${aurora_shmem_source_SOURCE_DIR}/shmem.c" shmem_source)
function(shmem_replace before after)
    string(FIND "${shmem_source}" "${before}" position)
    if(position EQUAL -1)
        message(FATAL_ERROR "libandroid-shmem patch no longer applies")
    endif()
    string(REPLACE "${before}" "${after}" shmem_source "${shmem_source}")
    set(shmem_source "${shmem_source}" PARENT_SCOPE)
endfunction()
shmem_replace("#define ASHV_KEY_SYMLINK_PATH _PATH_TMP \"ashv_key_%d\""
    "/* Aurora: key files belong to the explicitly supplied runtime directory. */")
shmem_replace("sprintf(symlink_path, ASHV_KEY_SYMLINK_PATH, key);" [=[const char *runtime = getenv("PROOT_TMP_DIR");
        if (!runtime || runtime[0] != '/') {
            pthread_mutex_unlock(&mutex); errno = EINVAL; return -1;
        }
        int path_size = snprintf(symlink_path, sizeof(symlink_path), "%s/ashv_key_%d", runtime, key);
        if (path_size < 0 || (size_t)path_size >= sizeof(symlink_path)) {
            pthread_mutex_unlock(&mutex); errno = ENAMETOOLONG; return -1;
        }]=])
shmem_replace("if (symlink(num_buffer, symlink_path) == 0) break;" [=[if (symlink(num_buffer, symlink_path) == 0) break;
            if (errno != EEXIST) {
                int failure = errno;
                pthread_mutex_unlock(&mutex); errno = failure; return -1;
            }]=])
file(WRITE "${LINUX_CONFIG}/shmem.c" "${shmem_source}")
add_library(aurora_shmem STATIC "${LINUX_CONFIG}/shmem.c")
target_include_directories(aurora_shmem PRIVATE "${aurora_shmem_source_SOURCE_DIR}")
target_compile_definitions(aurora_shmem PRIVATE _GNU_SOURCE)
target_link_libraries(aurora_shmem PUBLIC log android)
