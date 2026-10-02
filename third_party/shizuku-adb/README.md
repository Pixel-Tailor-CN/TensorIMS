# Shizuku 配对 JNI 来源

这些上游源码仅供来源审计，本项目不将此目录加入 CMake 构建。

- 上游审阅提交：`b844bc491f1790c72328e1a8e5b2349f8978f0ea`
- 官方 v13.6.0 APK 发布提交：`2650830c5b099ae0dd34fedf614d4f592ca05d65`
- `adb_pairing.cpp` 已从发布提交重新获取，并确认与审阅提交逐字节相同
- 其余原生构建参考文件来自审阅提交；完整 upstream 工程仍在不可变源链接下可得，不能只用本目录当作可重构建工程
- 运行时只使用官方 APK 中提取的 arm64 `libadb.so`；无官方 starter、服务端或 rish
- 官方硬编码 JNI 需要保留 `moe.shizuku.manager.adb.PairingContext`。这是第三方适配身份，第一方逻辑全部使用新包名
- APK、JNI、来源、许可及哈希见 `app/src/main/assets/licenses/source-manifest.json`
- 本机无已安装 Android NDK，因此没有宣称原生二进制是本地可复现构建。未来替换为自行重编时需核对 SPAKE2、TLS exporter、JNI 注册、16KB 页对齐与许可，并重做真机配对验证

提取可复核：`unzip -p shizuku-v13.6.0.r1086.2650830c-release.apk lib/arm64-v8a/libadb.so > libadb.so`
