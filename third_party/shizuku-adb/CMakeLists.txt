cmake_minimum_required(VERSION 3.22.1)

# 配对 JNI 沿用 Shizuku 注册契约；BoringSSL 由 Gradle 解析固定版本的
# Maven Central Prefab 依赖，不链接系统私有 libcrypto，也不提取其他 APK。
find_package(boringssl REQUIRED CONFIG)

add_library(adb SHARED adb_pairing.cpp)
target_compile_features(adb PRIVATE cxx_std_17)
target_compile_options(adb PRIVATE
    -Werror=format
    -fdata-sections
    -ffunction-sections
    -fno-exceptions
    -fno-rtti
    -fno-threadsafe-statics
    -fvisibility=hidden
    -fvisibility-inlines-hidden
)
# 只导出 JNI_OnLoad，避免与 Android 或其他库中的 BoringSSL 符号冲突。
# 显式保持 16 KB 页兼容；NDK libc++ 沿用主工程的静态链接配置。
target_link_options(adb PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/adb.map.txt"
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,-z,max-page-size=16384
)
set_property(TARGET adb APPEND PROPERTY LINK_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/adb.map.txt")
target_link_libraries(adb PRIVATE boringssl::crypto_static log)
