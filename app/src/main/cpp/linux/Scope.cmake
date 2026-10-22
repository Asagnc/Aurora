add_executable(aurora_linux_scope "${CMAKE_CURRENT_LIST_DIR}/scope_probe.c" "${CMAKE_CURRENT_LIST_DIR}/task_scope.c"
    "${CMAKE_CURRENT_LIST_DIR}/scope_identity.c" "${CMAKE_CURRENT_LIST_DIR}/scope_state.c" "${CMAKE_CURRENT_LIST_DIR}/scope_process.c"
    "${CMAKE_CURRENT_LIST_DIR}/mount_probe.c" "${CMAKE_CURRENT_LIST_DIR}/guest_run.c" "${CMAKE_CURRENT_LIST_DIR}/scope_gc.c")
target_compile_options(aurora_linux_scope PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_options(aurora_linux_scope PRIVATE -Wl,-z,relro,-z,now,-z,max-page-size=16384)
