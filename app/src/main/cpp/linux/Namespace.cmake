add_executable(aurora_linux_namespace "${CMAKE_CURRENT_LIST_DIR}/namespace_probe.c")
target_compile_options(aurora_linux_namespace PRIVATE -Wall -Wextra -Werror -fstack-protector-strong)
target_link_options(aurora_linux_namespace PRIVATE -Wl,-z,relro,-z,now,-z,max-page-size=16384)
