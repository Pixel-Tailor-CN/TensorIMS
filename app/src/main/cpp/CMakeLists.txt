cmake_minimum_required(VERSION 3.22.1)
project(tensorims_starter LANGUAGES CXX)

# 无线配对 JNI 与电脑启动器共用 NDK 工具链，两个安装包均从源码构建。
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/shizuku-adb"
                 "${CMAKE_CURRENT_BINARY_DIR}/shizuku-adb")

add_executable(tensorims_starter native_starter.cpp)
set_target_properties(tensorims_starter PROPERTIES
    OUTPUT_NAME "tensorims_starter"
    PREFIX "lib"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED YES
)
target_compile_options(tensorims_starter PRIVATE -Wall -Wextra -Werror)

# APK ABI 与 Android 安装目录中的 ISA 名称不同，需分别映射。
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(TENSORIMS_INSTALL_ISA "arm64")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(TENSORIMS_INSTALL_ISA "x86_64")
else()
    message(FATAL_ERROR "Unsupported native starter ABI: ${ANDROID_ABI}")
endif()
target_compile_definitions(tensorims_starter PRIVATE INSTALL_ISA="${TENSORIMS_INSTALL_ISA}")
