# 双包名双模式开发构建验证说明

## 2026-10-02 当前增量

新增 `tensor` / `legacy` 两个安装身份，每个 APK 都包含完整官方 Shizuku / 私有内置双模式。namespace 与第一方代码仍为 `app.mystery0.ims.tensor`。本次验证仅在云端构建环境完成，没有连接设备、实际配对/root、合并 PR 或执行发布工作流。

首轮 APK 检查发现顶层 Instrumentation 的相对类名没有自动按 namespace 展开；已改为八项显式全类名，重新构建后核对实际 Manifest 和 DEX 类定义通过。Provider authority、声明权限、目标包和私有启动/认证身份均按本 flavor 隔离。

## 已完成的检查

环境：Temurin JDK 21.0.12.1+1、Gradle 9.7.1、AGP 9.4.1、Kotlin 2.4.20、SDK Platform 37.0 revision 2、Build Tools 36.0.0。串行、单 worker 构建。

- `:app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest`：每包 139 项、18 类，合计 278 项，0 失败/错误/跳过；含本 flavor 身份、固定入口、用户隔离及同签名不同 UID 拒绝回归
- `:app:assembleTensorDebug :app:assembleLegacyDebug`：通过；最终修复后已重新生成两份 APK
- `:app:lintTensorDebug :app:lintLegacyDebug :stub:lintDebug`：通过；两 app 变体各 0 错误、67 警告，stub 0 错误/警告
- `:app:minifyTensorReleaseWithR8 :app:minifyLegacyReleaseWithR8`：通过；此结论不等于正式签名 APK 已构建/验收
- `:app:mergeTensorReleaseComposeMapping :app:mergeLegacyReleaseComposeMapping`：通过；各自最终 `outputs/mapping/<variant>/mapping.txt` 均为 43,173,197 字节。R8 的 `intermediates` 文本与 `mapping.prt` 不替代 Compose 合并后的最终文本；完整 `assemble*Release` 任务图已确认包含合并步骤
- `:app:validateSigningTensorRelease :app:validateSigningLegacyRelease --continue`：两项均按预期因缺少正式签名配置明确失败，没有回退开发证书
- `python3 -B -m unittest discover -s scripts -p 'test_*.py'`：13 项产物收集测试通过，覆盖缺件、错误包名/变体、版本不一致、拆分/多余 APK、路径与旧输出污染
- `tools/check_identity.py`、`tools/check_bridge_safety.py`、两 APK 的 `tools/check_apk.py`、YAML/shell 语法及 `git diff --check`：通过
- 实际两 APK 的四项 Provider authority、自有声明权限和八项 Instrumentation targetPackage 均互不串包；入口类真实存在于 DEX，第一方旧代码 namespace 不存在
- 两 APK 的 v2 签名、同开发证书及 `zipalign -c -P 16 4` 检查通过；此处只证明 ZIP 对齐，不替代真机原生库/页面大小兼容验收

## 实测开发 APK

以下产物来自最终实现工作树，版本后缀使用构建时本地 HEAD；推送后 CI 会按远端 HEAD 重新生成后缀。它们不是正式发布 APK，不能用开发证书覆盖用户的正式安装。

两包共同：`versionCode=195`、`versionName=3.8.0.d195.0826be7c`、minSdk 33、targetSdk 37、仅 arm64-v8a。版本计数高于基线 `9441fcc4` 的 192，但不据此保证任意已安装版本可升级。

| 变体 | applicationId | 字节数 | APK SHA-256 |
| --- | --- | ---: | --- |
| tensorDebug | app.mystery0.ims.tensor | 73,622,722 | `651666627325289706295cdc60541dc30738da72cbdcd85d084bd405b1b54cce` |
| legacyDebug | io.github.vvb2060.ims | 73,622,812 | `dd997f04537cacd9b38b57244b95b094e9773809cd934d302ff8aaef2f7c4800` |

两包开发证书均为 Android Debug，SHA-256：`91ce60f4005588ba953afd034128b4f94e56da3fb4efd302b050da16571d061b`。

## 发布与升级边界

- master 预发布与 release 正式工作流配置为同次构建两份 release APK，校验版本一致后上传两个独立 APK 与各自最终 mapping；文件名含 applicationId 与 versionName
- master 仍要求提交消息含 `ci`，使用 `pre-` 标签；未取消原触发门禁，没有手动触发 CI/发布
- 沿用既有四项 Secrets，配置缺失提前失败并始终清理临时 keystore。未读取生产证书，因此不能宣称旧安装证书已核对，也未完成正式签名 APK 构建
- legacy 覆盖升级仍要求与原安装签名一致且 versionCode 更高；私有数据键及 VoLTE 原值备份保持不变。不要为绕过签名不匹配先卸载/清数据
- tensor 与 legacy 数据独立，但都能修改同一设备/SIM；不能同时自动恢复或并发写入，也不能承诺 Android 全局 shell 委托可并行
- 真机双装、覆盖升级、官方/内置/双包服务共存、ADB/root、重启和 IMS 通信均未验证；按 [DEVICE_ACCEPTANCE.md](DEVICE_ACCEPTANCE.md) 继续验收
- CarrierConfig 重置仍只确认已请求，不能独立证明覆盖已删除；原恢复保护边界不变

以下保留原交付时点记录；其中“单 APK”和“未推送”是历史状态，当前范围以本文上半部分和 [双包补充设计](../plans/2026-10-02-dual-package-release-design.md) 为准。

## 2026-10-01 原单包交付记录（历史）

### 当前交付性质

单 APK、新应用身份 `app.mystery0.ims.tensor` 的本地开发实现。没有推送、发布、正式签名、连接设备或执行实际配对/root。官方与内置后端共用业务、日志和恢复数据；内置服务不是其他应用的 Shizuku 管理器。

### 已完成的离线检查

- 原基线 36 项测试保留；新增状态、恢复、IPC 策略、ADB 协议和草稿保留回归；受控整合轮次共 135 项、17 个测试类，0 失败/错误/跳过。
- 整合 debug APK 已成功生成；应用身份、minSdk 33/targetSdk 37、唯一 arm64 ABI、私有 main/AIDL 入口、第三方许可存在检查通过。
- 开发证书为 Android Debug；APK 签名及 16KB ZIP 对齐检查通过。不得把该证书用于正式发布。
- Debug lint 已通过：app 为 0 错误、67 警告，stub 为 0 错误；警告包含未用资源、隐藏 API/显式导出 Provider 与 ADB 自签名 TLS 的设计边界。委托后的读取调用采用局部说明，不通过添加无关权限或全局忽略错误规避检查。
- 源码检查确认 Instrumentation 不直接读取官方 Shizuku 单例、AIDL 仅固定方法，无通用命令/Binder 转发与官方服务清理路径。
- 独立源码审查修复了预检顺序、写后权限错误被误判可重试、legacy 部分写入/回读未知、末次回读标记、旧回调与关闭竞态、两处 UI 草稿丢失。
- 最终提交、最终重跑结果与产物校验和以交付目录内的机器可读验证清单为准；本文不把中间轮次产物当成最终版本。

### 明确的能力边界

#### CarrierConfig 重置未确认

`CarrierConfigManager.overrideConfig(subId, null)` 存在吞掉远程失败和异步处理的路径。合并后配置与无法关联操作 ID 的刷新事件都不能独立证明覆盖删除。代码实际请求并读取当前快照，但明确显示「已请求、尚未确认」，保持恢复保护及自动恢复暂停，不记录成功重置历史。全卡重置可能在首卡未确认后停止。不能把系统默认参数当作运营商默认值来制造成功证据。

#### 必须保留的设备验收

- 私有 Binder/Instrumentation/权限委托与真实 Pixel 隐藏 API 兼容
- 独立无线 ADB 配对、撤销和 TLS exporter；root 授权与启动
- 官方与内置服务共存、第三方官方客户端不受干扰
- 进程死亡、应用更新、系统重启、超时、SIM 热切换与原值恢复
- IMS 注册、VoLTE 呼入呼出、VoWiFi/VoNR 及双卡运营商行为

本次未使用模拟器，也不声称真机通过；完整矩阵见 `DEVICE_ACCEPTANCE.md`。Android 17/SDK 37 必须单列预览平台验收。启动 APK 当前限定标准 `/data/app/` 安装路径；其他存储位置不在此实现的支持范围。

### 复现

需要 JDK 21、Android SDK 37、对应 Gradle/AGP 依赖。版本在 `gradle/libs.versions.toml`。常规命令：

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug :stub:lintDebug
python3 tools/check_identity.py
python3 tools/check_bridge_safety.py
python3 tools/check_apk.py app/build/outputs/apk/debug/app-debug.apk --aapt2 "$ANDROID_HOME/build-tools/36.0.0/aapt2"
```

受内存约束时关闭并行、`--max-workers=1`，并设置进程内 Kotlin 编译、1536 MiB Gradle 堆及1024 MiB lint 堆。签名配置缺失不阻断纯编译/lint，但 `validateSigningRelease`/发布打包会明确失败；release 不回退到开发签名或静默生成未签名成品。

### 许可与来源

保留主项目 Apache 2.0；Shizuku 源码 Apache 2.0、API MIT、BoringSSL/LLVM/Bouncy Castle 的适用许可随 APK 分发。`assets/licenses/source-manifest.json` 固定来源与 SHA-256；唯一保留第三方 JNI 类 `moe.shizuku.manager.adb.PairingContext` 是已核验官方二进制的 ABI，不是第一方旧包名残留。
