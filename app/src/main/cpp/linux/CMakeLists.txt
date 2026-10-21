cmake_minimum_required(VERSION 4.1.2)
project(aurora_linux_tools C)
# Standalone corresponding-source build; use the Android NDK toolchain and API 36.
if(NOT ANDROID OR ANDROID_PLATFORM_LEVEL LESS 36)
    message(FATAL_ERROR "Aurora Linux tools require an Android API 36+ toolchain")
endif()
include(Proot.cmake)
include(Namespace.cmake)
