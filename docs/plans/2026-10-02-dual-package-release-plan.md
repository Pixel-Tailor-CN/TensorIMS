# TensorIMS 双包名发布补充实施计划

2026 年 10 月 2 日；依据[双包发布补充设计](2026-10-02-dual-package-release-design.md)，补充[原实施计划](2026-10-01-tensorims-dual-mode-plan.md)。下列复核项只有取得相应证据后才能标记完成。

## 范围与授权

- 增加 `tensor` / `legacy` flavors，分别对应 `app.mystery0.ims.tensor` / `io.github.vvb2060.ims`；每包保留完整双模式，namespace 与第一方源码仍为 `app.mystery0.ims.tensor`。
- 不改业务数据键或私有原值备份格式，不自动迁移，不扩大私有桥接能力，不取消原设计的安全门。
- 用户已授权更新并推送现有 PR #39；由整合者完成提交、推送和远端状态核对。不得合并 PR、手动触发 workflow 或发布 Release。
- 不生成真实设备授权/配对凭据；未执行的真机、升级和通信验收明确列为待验证。

## 1 构建身份与运行时隔离

- [x] 在 app 构建配置增加两个 flavor，复用默认版本、依赖、资源、源码和 release 签名配置
- [x] 核对 Provider authority、Instrumentation targetPackage、启动器、包查询和私有认证均指向本 flavor；组件类名、AIDL 和 R8 入口继续使用统一 namespace
- [x] 核对配置键、自动恢复数据和 `noBackupFilesDir` 原值文件保持不变，且未知结果/清理失败不会因 flavor 放开恢复门
- [x] 为适合纯逻辑的身份选择与拒绝跨包访问补充回归检查

## 2 CI 同版本双包产物

- [x] 正式发布和 master 预发布均构建 `:app:assembleTensorRelease :app:assembleLegacyRelease`，读取各自 output-metadata 与 mapping
- [x] 核对两包版本一致、applicationId 正确、每包内容完整；以 applicationId 和 versionName 命名两个 APK 与两个 mapping，不能相互覆盖
- [x] 沿用既有签名 Secrets；缺配置失败，不使用 debug 证书发布；保留 master 的 `contains(..., 'ci')` 门禁及预发布标签规则
- [x] 不把共用签名配置写成已确认旧版证书，也不执行发布 workflow

## 3 本地验证与文档

在 JDK 21、SDK 37 环境运行，release 构建需要完整、已授权的签名配置：

```sh
./gradlew :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest
./gradlew :app:assembleTensorDebug :app:assembleLegacyDebug
./gradlew :app:lintTensorDebug :app:lintLegacyDebug
./gradlew :app:testTensorReleaseUnitTest :app:testLegacyReleaseUnitTest
./gradlew :app:assembleTensorRelease :app:assembleLegacyRelease
./gradlew :app:lintTensorRelease :app:lintLegacyRelease
```

- [x] 检查两包合并 Manifest、开发 APK 身份/签名、Instrumentation 目标、组件类名、JNI 和许可内容；正式签名 APK 尚未构建
- [x] 同步 AGENTS、README 与真机清单；在原 2026-10-01 设计/计划顶部注明新的发布及推送授权范围，其余正文保留为历史
- [x] 在 VALIDATION 中只记录实际执行命令与结果，区分通过、失败、受阻和未运行；本地测试签名不证明旧正式版覆盖升级可行
提交后的推送与远端核对结果以 PR #39 最新提交和描述为准；本计划仅记录本地验证证据，不记录发布成功。不得合并或手动触发发布。

## 4 发布前门槛（含真机，尚未执行）

- [ ] 使用已授权的生产签名配置完成两份 release APK 构建并核对证书；本地 R8 通过不能替代这一步

- [ ] 两包各自完成官方/内置模式安全与通信验收，验证两包及官方 Shizuku 的安装隔离与冲突行为
- [ ] legacy 在已核对旧安装证书相同且新 versionCode 更高的前提下覆盖升级，确认历史、自动恢复状态和持久化 VoLTE 原值数据原地保留
- [ ] tensor 独立安装不迁移旧包数据，按安全步骤恢复旧版原值后再开展写入；两包不同时对同一 SIM 自动恢复

完整步骤与记录字段见[真机验收与安全迁移](../implementation/DEVICE_ACCEPTANCE.md)。缺少真机证据时不得宣称已满足发布候选条件。
