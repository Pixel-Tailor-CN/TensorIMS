# 实施记录

计划：docs/plans/2026-10-01-tensorims-dual-mode-plan.md
基线：9441fcc4e3ae265c8dc82cd88f198f30641ad339
分支：feat/tensorims-dual-mode
环境：dot 云端 /workspace/shared/tensorims-dual-mode

## 已核对
- 2026-10-01 远端 master 与基线相同，无额外上游变更。
- 新身份静态测试先在旧包失败，迁移后通过。
- 原始签名脚本缺少 SIGN_KEY_STORE_FILE 时配置失败已复现；修复后可进入 Kotlin 编译。
- 新后端测试尚未实现时，整合构建在其缺失状态机类型上失败，构成首轮 RED 记录。

## 实施决定
- 按批准设计替代原 AGENTS.md 中保留旧包名的历史规则。
- 四个不重叠文件集并行实现，通过公共接口契约整合；整合者集中执行 Gradle，避免同一输出目录并发写入。
- 缺少 NDK 时采用官方 Shizuku 发布 APK 的 arm64 配对库，保留其第三方 JNI 注册类名；第一方代码均使用新包名。必须记录文件来源、哈希与许可。
- 运行时启用配对/root 必须由用户显式操作；本次离线验证不触发任何真实设备授权。
- debug 使用标准开发证书；release 缺少完整显式签名配置时失败，不回退到 debug/未签名正式包。

## 验证边界
真实 Pixel、无线 ADB 配对、root、两服务共存、IMS/运营商通信与 Android 版本兼容尚未验证。离线测试不得替代这些验收。
