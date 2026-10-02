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
- release 签名保护已离线验证：:app:preReleaseBuild 在缺少完整配置时明确失败（退出码 1），不会生成未签名正式包；日志 tensorims-dual-release-guard.log。
- APK 校验脚本已对旧基线确认失败：旧 applicationId 被拒绝；新 APK 完成后须再跑 GREEN。
- 独立界面审查发现两处原有读取失败/重进页面导致草稿丢失的集成缺陷，已修复并以隔离旧行为 RED（24 项中 7 失败）与新行为 GREEN（24/24）验证；限域复审闭环。
- 独立特权审查要求将业务预检与严格出站身份验证分离，并补齐 legacy/目标写入的阶段追踪和最后回读结果。这些不是通过编译即可替代的检查。
- 首次整包 JVM 构建在共享内存压力下报 daemon unexpectedly disappeared；Kotlin 主源码编译经过，但未形成最终测试/APK/lint通过结论。后续统一使用单 worker、禁并行、1536 MiB Gradle 堆、进程内 Kotlin 和1024 MiB lint 堆。
- 受控整合测试+debug 构建成功：135/135、17类；Debug 签名/16KB ZIP 对齐/入口与许可扫描通过。限定安全复审关闭所有重要源码问题，CarrierConfig 重置仍明确未确认。
- Debug lint 最终通过（app 0 errors / 67 warnings），非隐藏其已说明的平台警告；最终本地提交后还要重建交付 APK。
