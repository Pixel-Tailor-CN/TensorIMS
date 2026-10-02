# 双模式开发构建验证说明

## 当前交付性质

单 APK、新应用身份 `app.mystery0.ims.tensor` 的本地开发实现。没有推送、发布、正式签名、连接设备或执行实际配对/root。官方与内置后端共用业务、日志和恢复数据；内置服务不是其他应用的 Shizuku 管理器。

## 已完成的离线检查

- 原基线 36 项测试保留；新增状态、恢复、IPC 策略、ADB 协议和草稿保留回归；受控整合轮次共 135 项、17 个测试类，0 失败/错误/跳过。
- 整合 debug APK 已成功生成；应用身份、minSdk 33/targetSdk 37、唯一 arm64 ABI、私有 main/AIDL 入口、第三方许可存在检查通过。
- 开发证书为 Android Debug；APK 签名及 16KB ZIP 对齐检查通过。不得把该证书用于正式发布。
- Debug lint 已通过：app 为 0 错误、67 警告，stub 为 0 错误；警告包含未用资源、隐藏 API/显式导出 Provider 与 ADB 自签名 TLS 的设计边界。委托后的读取调用采用局部说明，不通过添加无关权限或全局忽略错误规避检查。
- 源码检查确认 Instrumentation 不直接读取官方 Shizuku 单例、AIDL 仅固定方法，无通用命令/Binder 转发与官方服务清理路径。
- 独立源码审查修复了预检顺序、写后权限错误被误判可重试、legacy 部分写入/回读未知、末次回读标记、旧回调与关闭竞态、两处 UI 草稿丢失。
- 最终提交、最终重跑结果与产物校验和以交付目录内的机器可读验证清单为准；本文不把中间轮次产物当成最终版本。

## 明确的能力边界

### CarrierConfig 重置未确认

`CarrierConfigManager.overrideConfig(subId, null)` 存在吞掉远程失败和异步处理的路径。合并后配置与无法关联操作 ID 的刷新事件都不能独立证明覆盖删除。代码实际请求并读取当前快照，但明确显示「已请求、尚未确认」，保持恢复保护及自动恢复暂停，不记录成功重置历史。全卡重置可能在首卡未确认后停止。不能把系统默认参数当作运营商默认值来制造成功证据。

### 必须保留的设备验收

- 私有 Binder/Instrumentation/权限委托与真实 Pixel 隐藏 API 兼容
- 独立无线 ADB 配对、撤销和 TLS exporter；root 授权与启动
- 官方与内置服务共存、第三方官方客户端不受干扰
- 进程死亡、应用更新、系统重启、超时、SIM 热切换与原值恢复
- IMS 注册、VoLTE 呼入呼出、VoWiFi/VoNR 及双卡运营商行为

本次未使用模拟器，也不声称真机通过；完整矩阵见 `DEVICE_ACCEPTANCE.md`。Android 17/SDK 37 必须单列预览平台验收。启动 APK 当前限定标准 `/data/app/` 安装路径；其他存储位置不在此实现的支持范围。

## 复现

需要 JDK 21、Android SDK 37、对应 Gradle/AGP 依赖。版本在 `gradle/libs.versions.toml`。常规命令：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug :stub:lintDebug
python3 tools/check_identity.py
python3 tools/check_bridge_safety.py
python3 tools/check_apk.py app/build/outputs/apk/debug/app-debug.apk --aapt2 "$ANDROID_HOME/build-tools/36.0.0/aapt2"
```

受内存约束时关闭并行、`--max-workers=1`，并设置进程内 Kotlin 编译、1536 MiB Gradle 堆及1024 MiB lint 堆。签名配置缺失不阻断纯编译/lint，但 `validateSigningRelease`/发布打包会明确失败；release 不回退到开发签名或静默生成未签名成品。

## 许可与来源

保留主项目 Apache 2.0；Shizuku 源码 Apache 2.0、API MIT、BoringSSL/LLVM/Bouncy Castle 的适用许可随 APK 分发。`assets/licenses/source-manifest.json` 固定来源与 SHA-256；唯一保留第三方 JNI 类 `moe.shizuku.manager.adb.PairingContext` 是已核验官方二进制的 ABI，不是第一方旧包名残留。
