# TensorIMS 双包名发布补充设计

2026 年 10 月 2 日；补充[原双模式设计](2026-10-01-tensorims-dual-mode-design.md)。本文记录用户新增的发布要求，不表示真机或覆盖升级已经验证。

## 1 变更范围

每个正式 Release 和 master 预发布同时提供两个 APK：

| flavor | applicationId | 功能 |
| --- | --- | --- |
| `tensor` | `app.mystery0.ims.tensor` | 完整官方 Shizuku / 私有内置双模式 |
| `legacy` | `io.github.vvb2060.ims` | 完整官方 Shizuku / 私有内置双模式 |

两者共用源码、资源、依赖、业务与安全策略。namespace、第一方 Kotlin/Java/AIDL 包和组件类名继续使用 `app.mystery0.ims.tensor`。只替代原设计的单包发布限制，不拆成“官方版/内置版”，不复制一套 legacy 业务代码。legacy 桌面名使用“TensorIMS（旧包名）”/“TensorIMS (Legacy)”，日志头标明 applicationId/flavor，迁移提示按其覆盖升级路径说明。

## 2 身份与隔离

- Manifest 的 Provider authority、Instrumentation targetPackage 和其他安装身份由当前 applicationId 派生，不能继续硬编码成 tensor。组件类名仍解析到统一 namespace。
- 启动器、已安装 APK 查询、UID/签名认证和私有握手使用本 flavor 的安装身份；固定组件与窄 AIDL 边界保持不变，不能接受任意目标包。
- 两包不能借用对方会话、配对身份或私有服务；官方模式仍只是官方 Shizuku 客户端，内置不接管官方服务或其他客户端。
- 进程内协调器只串行化本应用操作，不能提供跨包或 Android 全局 shell 委托的并发保证。不得同时启用两包自动恢复修改同一 SIM；冲突仍应报告 `DELEGATION_BUSY`，不能清理对方委托。

## 3 数据与升级

- 现有 SharedPreferences 名称/键、`sim_config_<subId>`、`auto_restore`、`noBackupFilesDir` 文件及持久化 VoLTE 原值保持不变，不能因 flavor、模式切换或升级清理、重命名或重新初始化原值。
- `legacy` 沿用旧 applicationId；仅在签名与原安装一致且 `versionCode` 更高时可覆盖升级，并原地保留私有数据。需真机验证数据仍可读取和恢复，不能以代码检查替代。
- `tensor` 是独立应用，既不覆盖旧包，也不读取或自动迁移旧包私有数据。换用它前先关闭旧版自动恢复，并由旧版恢复持久化 VoLTE 原值；尚未恢复的备份不得删除。
- CI 使用同一组 Secrets 只能说明构建配置一致，不证明用户旧安装使用相同证书。签名不匹配时停止覆盖升级，不建议卸载旧版来绕过问题。

## 4 发布与签名

两个 flavor 在同一提交使用同一 `versionCode`、`versionName`，分别构建 `tensorRelease`、`legacyRelease`；均启用既有 release 混淆、资源压缩和同一组签名 Secrets。debug 继续使用标准开发签名，release 缺少完整 `SIGN_KEY_*` 配置时明确失败。

同一 Release 必须包含四个对应文件，名称中保留 applicationId：

- `TensorIMS-app.mystery0.ims.tensor-<versionName>.apk`
- `TensorIMS-io.github.vvb2060.ims-<versionName>.apk`
- `TensorIMS-app.mystery0.ims.tensor-<versionName>-mapping.txt`
- `TensorIMS-io.github.vvb2060.ims-<versionName>-mapping.txt`

正式发布与 master 预发布使用相同双包打包规则；master 仍保留 `contains(github.event.head_commit.message, 'ci')` 门禁、`pre-` 标签和 prerelease 标记。新增 flavor 不构成本次触发发布或合并 PR 的授权。

## 5 保持的安全边界与证据要求

`UNSET` 不启动特权服务、不写入；官方拒绝授权不切内置；root/无线 ADB 仍只由用户显式启动。固定操作、UID/签名/挑战认证、epoch、未知结果恢复门、原值备份和许可/JNI 边界均不变。当前 CarrierConfig 重置只能报告“已请求但尚未确认”的限制也不变。

构建、单元测试、lint、合并 Manifest 和 APK 静态检查需要覆盖两个 flavor。真实 Pixel 上的双包安装、legacy 覆盖升级、模式切换、桥接共存与 IMS 通信全部按[验收清单](../implementation/DEVICE_ACCEPTANCE.md)分别执行；本补充设计不声称已完成。实际验证记录由[验证报告](../implementation/VALIDATION.md)维护。
