# Shizuku 配对 JNI 构建

`app/src/main/cpp/CMakeLists.txt` 接入本目录，两个安装包均使用 NDK 从 `adb_pairing.cpp` 编译并链接生成 `libadb.so`。BoringSSL 通过固定版本的 Maven Central Prefab 静态库提供；不再打包从 Shizuku APK 提取的原生库，也不使用 Git 子模块。

## 固定来源与依赖

- Shizuku 上游审阅提交：`b844bc491f1790c72328e1a8e5b2349f8978f0ea`。
- 官方 v13.6.0 发布提交：`2650830c5b099ae0dd34fedf614d4f592ca05d65`。
- `adb_pairing.cpp` 来自发布提交，与审阅提交逐字节相同；保留 SPAKE2、HKDF、AES-128-GCM 和 JNI 注册实现。
- BoringSSL 依赖：`io.github.vvb2060.ndk:boringssl:20250114`，来自 Maven Central，与上游 Shizuku v13.6.0 使用的版本一致。它是第三方维护的开源 Android 封装，不是 Google 官方 Maven 包。
- 封装仓库：`https://github.com/vvb2060/BoringSSL_Android`，对应提交 `20b87e2e25a793531c9cd651c629c62976b42e2b`；BoringSSL 源码提交为 `571a7432a19592c620fa316abde47770dad4f82b`。
- Gradle 启用 Prefab，CMake 使用 `find_package(boringssl REQUIRED CONFIG)` 与 `boringssl::crypto_static`。依赖中的 `libcrypto_static.a` 为预编译库，不能将当前构建描述为所有原生依赖均从源码构建。
- BoringSSL 与 NDK/CMake 版本统一维护在 `gradle/libs.versions.toml`。使用 NDK 的 `c++_static`，不依赖系统私有 libcrypto、libssl 或额外 libcxx prefab。
- `CMakeLists.txt` 为本项目适配：仅编译配对 JNI，隐藏依赖符号、只导出 `JNI_OnLoad`，设置 16 KB 页对齐。`misc.cpp`、`misc.h` 仅保留来源参考，不参与构建。
- 默认只构建 `arm64-v8a`；现有显式模拟器 debug 开关同时构建 `x86_64` 配对库与启动器。
- 保留 `moe.shizuku.manager.adb.PairingContext`，以维持上游 JNI 注册契约；不包含官方 starter、服务端或 rish。

## 构建与升级验证

普通克隆后直接运行 Gradle 构建即可，不需要初始化子模块。Gradle 从既有的 Maven Central 仓库解析依赖；CMake 不自行下载依赖。

在 Windows 上：

```powershell
.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain
```

升级 JNI 或 BoringSSL 时应核对源码来源与许可、Prefab ABI/API 要求、JNI 注册、SPAKE2 协议、AES-GCM 双向消息与失败路径、16 KB 页对齐，并重新验证真实设备无线配对和 TLS exporter 组合。编译或模拟器验证不能代替真机配对验收。

许可及固定来源见 `app/src/main/assets/licenses/`。`source-manifest.json` 中的 `historical_prebuilt_provenance` 只用于追溯迁移前 APK 提取库，不代表当前构建产物。F-Droid 对 Maven 依赖的收录审核及整个 APK 的可复现构建仍需单独验证。
