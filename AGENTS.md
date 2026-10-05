# AGENTS.md

## 基本约定

- 与用户沟通时使用中文。
- 生成或修改的文档、代码注释使用中文；日志打印使用英文。
- 可保留 Shizuku、Instrumentation、CarrierConfig、IMS、VoLTE、VoWiFi、VoNR、AIDL 等通用英文技术名词。
- 修改代码前先理解当前实现和设备兼容约束，避免只针对一个 Android 版本做硬编码修复。
- 本项目不要求添加单元测试。代码变更后至少运行 `.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain` 验证两个 flavor 编译；适合纯 Kotlin 行为的新增逻辑可增加针对性单元测试。
- Git 提交信息使用中文；自动生成提交信息时也必须生成中文内容。

## 项目概览

TensorIMS 是一个面向 Google Pixel Tensor 设备的 Android 应用，用于读取和修改 IMS/运营商相关配置，开启或关闭 VoLTE、VoWiFi、VT、VoNR、Cross-SIM Calling、UT、5G NR 等功能，并提供少量与 Pixel 网络使用相关的设备级系统设置。项目通过显式选择的官方 Shizuku 或内置私有后端执行特权操作；无线 ADB 路径不要求 root。

主要适配和维护范围为中国大陆的中国移动、中国联通、中国电信网络下的 Pixel Tensor 设备。其他运营商因缺少相应测试条件，不作为主要适配和维护对象。此约定限定维护范围，不代表需要在代码中按地区或运营商限制使用，也不保证所有机型、系统版本和网络功能均可用。

项目原名为 Mystery00/TurboIMS，现由 Mystery00 以 TensorIMS 名称独立维护，仓库归属 Pixel-Tailor-CN 组织。仓库地址为 `https://github.com/Pixel-Tailor-CN/TensorIMS`，项目主页为 `https://pixel.mystery0.app`。本双模式分支同时构建 `tensor`（`app.mystery0.ims.tensor`）与 `legacy`（`io.github.vvb2060.ims`）两个 flavor，每个 APK 都包含完整官方/私有内置双模式。`tensor` 与旧版独立安装、不自动迁移私有数据；`legacy` 仅在签名与原安装一致且 `versionCode` 更高时可覆盖升级并原地保留数据。历史文档和上游仓库名称保留来源。

应用主要面向 Android 13 及以上系统，当前构建目标为 Android SDK 37。设备侧功能验证应优先在已安装并运行 Shizuku 的真实 Pixel 设备上完成。

## 构建环境

- Gradle wrapper：`9.7.1`
- Android Gradle Plugin：`9.4.1`
- Kotlin：`2.4.20`
- `compileSdk`：`37`
- `targetSdk`：`37`
- `minSdk`：`33`
- Java/Kotlin JVM target：`21`
- ABI：仅 `arm64-v8a`
- UI：Jetpack Compose + Material 3 + Navigation Compose
- 版本号：`versionCode` 由 Git commit 数生成，`versionName` 来自 `gradle/libs.versions.toml` 的 `app-version`

常用命令：

```powershell
.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain
.\gradlew.bat :app:assembleTensorRelease :app:assembleLegacyRelease --stacktrace --console=plain
.\gradlew.bat :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest --stacktrace --console=plain
.\gradlew.bat :app:testTensorReleaseUnitTest :app:testLegacyReleaseUnitTest --stacktrace --console=plain
.\gradlew.bat :app:lintTensorDebug :app:lintLegacyDebug --stacktrace --console=plain
.\gradlew.bat :app:lintTensorRelease :app:lintLegacyRelease --stacktrace --console=plain
.\gradlew.bat clean
```

依赖版本统一维护在 `gradle/libs.versions.toml`，不要在 Gradle 脚本中硬编码依赖版本。

`master` 的自动预发布由 `.github/workflows/android_master.yml` 管理，保持提交信息包含 `ci` 时触发。正式发布与预发布均在同一 Release 中提供 `tensorRelease`、`legacyRelease` 两个 APK 及各自的 mapping；两包使用相同 `versionCode`/`versionName` 和既有签名 Secrets，启用混淆和资源压缩。文件名为 `TensorIMS-<applicationId>-<versionName>.apk` 及 `TensorIMS-<applicationId>-<versionName>-mapping.txt`，避免相互覆盖；预发布仍使用 `pre-` 标签并标记为 prerelease。CI 共用签名配置不等于已经核实它与用户已安装旧包的证书一致，覆盖升级前必须单独核对。

## 模块结构

- `app/`：主应用模块，包含 UI、ViewModel、Shizuku 桥接、Instrumentation 入口和业务逻辑。
- `stub/`：编译期隐藏 API 和 internal AIDL stub。该模块通过 `compileOnly(project(":stub"))` 供 `app` 编译使用，不应作为运行时代码打包进 APK。

`stub` 模块启用了 AIDL，且显式设置 `enableKotlin = false`，用于配合 AGP 9 的 built-in Kotlin 行为。

## 核心包结构

- `app.mystery0.ims.tensor.model`：数据模型和功能映射；`CaptivePortalSettings` 负责设备级联网检测地址的纯数据与校验。
- `app.mystery0.ims.tensor.viewmodel`：界面状态和业务调度；`MainViewModel` 管理 SIM/IMS/Shizuku，`SystemNetworkViewModel` 管理设备级系统网络设置，`LogcatViewModel` 管理日志。
- `ImsConfigViewModel` 管理 IMS 目标配置的实时快照、待应用修改与逐卡结果；`TargetConfigMapper` 集中定义新协议的显式开关、系统默认参数恢复和仅可重置项目。
- `app.mystery0.ims.tensor.ui`：Activity、Compose UI、组件和主题；`MainActivity` 只承担状态连接与导航承载。
- `app.mystery0.ims.tensor.ui.screens`：首页、IMS 配置、系统网络、Captive Portal 和高级工具页面。
- `app.mystery0.ims.tensor.ui.components`：跨页面复用的 Compose 组件。
- `app.mystery0.ims.tensor.privileged`：通过 Instrumentation 执行的特权逻辑。
- `ShizukuProvider.kt`：保留官方客户端 Provider 与业务 facade，固定操作交由 `PrivilegeRuntime` 路由。
- `LogcatRepository.kt`：应用日志采集和导出相关逻辑。
- `ConfigurationRepository.kt`：共用配置历史、应用参数构造、自动恢复操作顺序和开机进度。
- `AutoRestoreController.kt`：按用户开关监听 Shizuku 连接与授权结果，执行有限重试的自动恢复。
- `AutoRestoreNotifier.kt`：自动应用一轮结束后汇总结果，先显示文本 Toast，未确认显示时使用通知兜底。
- `ConfigurationOperations.kt`：复用 `OperationCoordinator` 串行协调手动/自动业务与历史提交，和模式切换共享安全边界。

## 运行架构

项目采用主应用进程、可选的私有 shell/root 服务进程与受控 Instrumentation：

1. 主进程首次保持 UNSET，用户明确选择 OFFICIAL 或 EMBEDDED 后才连接选定后端。
2. `PrivilegeRuntime` 管理选定模式、epoch、未完成操作日志与恢复门禁；`OperationCoordinator` 统一串行业务/历史/fallback/切换/关闭。
3. `OfficialBackend` 封装官方 API 与 `ShizukuBinderWrapper`。断开仅释放客户端引用，不能停止官方服务。
4. `EmbeddedServerMain` 从当前安装 APK 启动；`EmbeddedBridgeProvider` 检查真实 shell/root UID 与一次性挑战；`EmbeddedServer` 只映射固定 AIDL 操作。
5. 两后端都把 `IPrivilegeSession` 和操作 ID 显式传入 `SessionInstrumentation`。业务入口不读取全局 Shizuku Binder。
6. Instrumentation 执行受限 API、回读与权限清理后通过 watcher 返回结果。客户端超时不取消设备端写入，未知结果保持恢复态。

典型数据流：

```text
UI / ViewModel -> ConfigurationOperations -> PrivilegeRuntime
  -> OfficialBackend 或 EmbeddedBackend -> 固定 Instrumentation + 显式会话
  -> CarrierConfig / SettingsProvider / Telephony -> 回读 / 清理 -> 结果 / 历史
```

## 特权入口

- `SimReader`：读取当前可用 SIM 列表。
- `ImsCapabilityReader`：读取 IMS 注册状态和 VoLTE/VoWiFi/VoNR/VT/NR 能力状态。
- `ImsModifier`：写入或重置运营商配置覆盖值。
- `ImsConfigurationReader`：读取活动 SIM 的当前 CarrierConfig 和可用恢复策略，不用 IMS 能力或历史草稿推断当前开关。新目标写入由 `TargetConfigurationOperation` 统一处理，主 Modifier 和 Broker 共用，有限回读一致后才确认成功。
- `ImsResetter`：调用 telephony service 重置 IMS。
- `PersistentVolteModifier`：按单张 SIM 读写持久化 VoLTE，使用 VoIMS opt-in 和用户开关；复用统一权限委托，通过系统管理类反射访问隐藏 API。
- `CaptivePortalSettingsModifier`：读取、写入或删除 `captive_portal_http_url` / `captive_portal_https_url` SettingsProvider 值；两个值作为一组更新，失败时尽力恢复原值并以回读结果判定成功。
- `BrokerInstrumentation`：在主 CarrierConfig 修改路径权限受限或返回空结果时作为 fallback。
- `ShellPermissionDelegation`：统一处理 shell permission delegation 停止调用的 Android 版本兼容。

## Android 版本兼容重点

- 隐藏 API 和 AIDL 接口可能随 Android 版本变更。修改 `stub/` 后必须确认目标设备 framework 的真实签名。
- Android 17 QPR1 Beta 6 中 `IActivityManager.stopDelegateShellPermissionIdentity` 从旧版无参签名变为带 `uid` 的签名。项目通过 `ShellPermissionDelegation.kt` 同时兼容：
  - 旧版：`stopDelegateShellPermissionIdentity()`
  - 新版：`stopDelegateShellPermissionIdentity(int uid)`
- 不要在业务类里分散写这类兼容分支；优先集中封装，保持 `SimReader`、`ImsModifier` 等入口只调用统一 helper。
- 使用 Android 14+ 才稳定存在或行为不同的能力时，需要做版本判断或反射 fallback。
- Captive Portal 的 SettingsProvider key 在不同 Android/NetworkStack 版本上的优先级可能不同；资源 overlay 可能覆盖 SettingsProvider。只能把“写入并回读一致”描述为系统设置写入成功，不能据此宣称实际 probe URL 已切换。

## Shizuku 与隐藏 API 约束

- 任何依赖 Shizuku 的路径都要处理 binder 不可用、权限未授予、服务版本过旧、返回空结果等情况。
- 使用隐藏 API、反射或编译期 stub 时，必须在代码附近用中文注释说明原因和兼容考虑。
- `LSPass.setHiddenApiExemptions("")` 目前在 `ShizukuProvider.onCreate()` 中用于隐藏 API 绕过，不要随意删除。
- 真实功能验证优先使用已连接设备和 logcat，不能只依赖编译通过。

## 业务与数据约定

- SIM 配置按 `subId` 持久化到 `SharedPreferences`，名称形如 `sim_config_<subId>`。
- `subId = -1` 表示应用到所有 SIM。
- 自动恢复默认关闭；由用户在主界面主动启用。仅当前选定后端 READY 时恢复历史，官方模式缺少权限时每次开机最多自动请求一次，用户拒绝后不循环弹窗。自动恢复不会配对、提权或启动任何服务。
- 自动恢复仅作用于当前活动 SIM；按最后成功操作的序号选择全卡或单卡配置。旧历史无序号时单卡优先。重置成功保留界面历史并写入恢复阻止标记，避免重启后撤销重置。持久化 VoLTE 不参与自动恢复。
- 自动恢复元数据和开关保存在 `auto_restore` SharedPreferences，历史中的 `_restore_revision` / `_restore_reset` 不属于 Feature。以系统开机计数去重，同一次开机已成功恢复的相同配置不重复应用；用户重新启用开关可重新尝试。
- 界面明确表述为“后端就绪后自动应用配置”，仅重启设备不会直接应用，必须先启动当前选定后端并获得授权。自动应用按一轮重试汇总实际写入结果，重复跳过的历史不提示；优先 Toast，5 秒未收到显示回调时尝试通知。通知需要 `POST_NOTIFICATIONS` 授权及结果渠道开启；启用开关时申请通知权限，拒绝通知不阻止自动应用。后台不主动弹通知权限窗口。
- 持久化 VoLTE 是独立的即时操作，不属于 `Feature` 配置草稿。原始订阅值（含 -1）保存在 `noBackupFilesDir/persistent_volte_<subId>.json`，以 SIM 标识摘要校验；失败回退失败时保留记录供恢复。重置配置先恢复所选活动 SIM 的原始值，再清除 CarrierConfig；未激活 SIM 的备份保留至重新激活后恢复。
- Captive Portal 属于设备级系统网络设置，不属于 `Feature`，不写入 `sim_config_<subId>`，不参与自动恢复，也不受当前 SIM 选择影响。
- Captive Portal 首版只管理 `captive_portal_http_url` 和 `captive_portal_https_url`；“系统默认”通过删除这两个 SettingsProvider 覆盖值实现，不硬编码第三方默认地址。
- `Feature` 和 `FeatureConfigMapper` 是 IMS 功能开关到运营商配置键的主要映射入口。
- IMS 配置页从系统实时读取，缺键／权限失败不能补成 Feature 默认值。普通开关关闭显式写入 false（5G NR 写空可用性数组），仅下发用户明确修改或预设指定的项目。所有 SIM 的不同值显示“各卡不同”，不得自动补成关闭。
- 参数类只有在 `CarrierConfigManager.getDefaultConfig()` 提供完整、类型匹配的默认参数时支持单项恢复；界面必须说明它是系统默认参数、不是运营商默认。字符串和无完整默认参数的覆盖项独立分组，恢复引导至高级工具重置，不能用空字符串或省略键假装恢复；不引入原值备份，不为单项恢复清空全卡覆盖。
- TikTok 区域兼容使用字符串目标 `TIKTOK_NETWORK_FIX`，仅 Android 14+ 且实时可读时开放单卡确认入口，写入随机三位 ASCII 数字（001–999）`sim_country_iso_override_string`。不修改 MCC/MNC，不按地区限制使用，不加入预设；目标数值随新协议历史保存，恢复沿用同一数值，移除引导至高级工具重置，不写空值或猜测国家 ISO。
- 新历史以 `_config_version=2` 保存稀疏、明确的目标及 SIM 身份摘要 `_target_identity`，逐卡回读成功才保存；自动恢复新历史同样检查身份并回读。旧历史保留只写开启项的语义，旧 false 不迁移为禁用。批量部分失败保留待应用目标，只保存成功卡历史；持久化 VoLTE 与 Captive Portal 不参与此协议。
- 修改运营商配置时优先走 `ImsModifier`，必要时由当前后端在同一操作事务内触发 `BrokerInstrumentation` fallback，不能跨模式。
- UI 不直接执行业务写入逻辑，业务动作应放在 ViewModel 或特权入口中。

## UI 信息架构约定

- 首页只承担设备/Shizuku 状态、SIM 选择、功能分类导航、应用日志入口和自动恢复开关，不继续堆叠具体功能按钮。
- IMS 配置页面以实时 CarrierConfig 初始化目标草稿，区分已读取、待应用、应用核对和失败状态；按“通话 / 网络 / 显示 / 自定义覆盖（恢复需重置）”分组，应用配置仍是主操作。预设与历史仅修改草稿，重新读取前确认丢弃未应用修改。
- 系统网络页面承载与 SIM 无关的设备级网络设置；Captive Portal 是首个入口。
- 高级工具页面承载 IMS 实时状态、持久化 VoLTE、IMS 重启、运营商配置重置等即时或低频操作。
- 新增功能前先判断作用域是 SIM CarrierConfig、设备级系统设置还是即时工具，不要因为实现方便就把所有入口堆回首页。

## 代码风格

- 保持现有 Kotlin/Compose 风格，不引入 Hilt、Dagger、Room 等新框架，除非任务明确需要。
- Activity 只承担界面承载和生命周期对接，不放复杂业务逻辑。
- ViewModel 负责状态流、用户动作调度和持久化协调。
- 复杂隐藏 API 调用集中在 `privileged/` 或明确的 helper 中，不要散落到 UI 层。
- 文件过长或逻辑明显复用时，优先提取小型 helper，避免大范围重构。
- 注释写中文，日志 message 写英文。

## 验证流程

常规代码变更：

```powershell
.\gradlew.bat :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest --stacktrace --console=plain
.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain
.\gradlew.bat :app:lintTensorDebug :app:lintLegacyDebug --stacktrace --console=plain
```

设备验证示例：

```powershell
adb devices -l
adb install -r app\build\outputs\apk\tensor\debug\app-tensor-debug.apk
adb logcat -c
adb shell am force-stop app.mystery0.ims.tensor
adb shell am start -n app.mystery0.ims.tensor/app.mystery0.ims.tensor.ui.MainActivity
adb logcat -d -v time AndroidRuntime:E ShizukuProvider:D SimReader:I ShellPermission:W '*:S'
```

验收 `legacy` 时使用 `app\build\outputs\apk\legacy\debug\app-legacy-debug.apk`，启动组件为 `io.github.vvb2060.ims/app.mystery0.ims.tensor.ui.MainActivity`。不要用 debug 包覆盖签名不同的已安装正式版，也不要为安装方便卸载含原值备份的旧版。两包需分别验收，且不可同时启用自动恢复修改同一 SIM。

Captive Portal 设置回读示例：

```powershell
adb shell settings get global captive_portal_http_url
adb shell settings get global captive_portal_https_url
```

遇到系统版本相关崩溃时，优先抓完整 `AndroidRuntime` 栈，并对照设备 `/system/framework/framework.jar` 或对应 AOSP 接口确认真实签名。

## 文档维护

- 本文件是代理协作的权威说明。
- 所有设计文档和实施计划统一存放在 `docs/plans/` 目录，不要创建 `docs/superpowers/specs/`、`docs/superpowers/plans/` 等其他规划文档目录。
- 设计文档和实施计划文件名使用 `YYYY-MM-DD-<主题>-design.md` 与 `YYYY-MM-DD-<主题>-plan.md` 格式。
- 若项目结构、构建版本、兼容策略或开发约定发生变化，应更新本文件。
- `CLAUDE.md` 仅作为 Claude Code 的兼容入口，不维护重复的项目说明。

## 双包名、各自双模式分支补充

- `tensor` 的 applicationId 为 `app.mystery0.ims.tensor`，`legacy` 为 `io.github.vvb2060.ims`；namespace、第一方 Kotlin/Java/AIDL 包仍统一为 `app.mystery0.ims.tensor`，不复制业务代码，不按 flavor 删减后端。
- Provider authority、Instrumentation 目标包、私有握手和启动器安装身份取当前 applicationId；组件类名仍属于统一 namespace。不同安装包不能借对方的私有服务或会话执行操作。
- 保留现有 SharedPreferences 名称、键、私有 `noBackupFilesDir` 文件名及原值；legacy 原地升级不重命名或清理它们，tensor 私有数据独立且不自动迁移。
- 双包可以独立安装，但不允许同时启用自动恢复修改同一 SIM；进程内串行调度不提供跨包或 Android 全局 shell 委托的并发保证。
- 2026-10-02 的[双包发布补充设计](docs/plans/2026-10-02-dual-package-release-design.md)替代原设计的单包限制；其他安全边界保持不变。
- `privilege/` 管理选定后端、官方适配、串行调度及未确认操作日志；`bridge/` 定义固定 AIDL/认证/会话；`embedded/` 承载仅供本应用的私有进程和显式启动器。
- 业务层不可直接读取官方 Shizuku 单例。Instrumentation 必须接收本次操作的会话 Binder；内置 Binder 不交给官方 API 静态入口。
- `UNSET` 不执行特权操作；官方拒绝授权不切内置；所有后台自动恢复仅在已选后端 READY 时触发，绝不代替用户启动 root/ADB。
- 结果未知或清理失败保持恢复态；客户端超时不意味着设备端停止。持久化原值备份不是缓存，禁止在切换、禁用或升级时清理。
- Android 全局 shell 委托冲突应报告 DELEGATION_BUSY，不清理其他客户端权限，不按进程名停止 Shizuku。
- 内置无线调试使用用户显式启动的短时前台通知，用户保留系统配对对话框并通过 RemoteInput 输入验证码；不要求手填端口，不申请悬浮窗权限。分别发现 `_adb-tls-pairing._tcp` 和 `_adb-tls-connect._tcp`，只接受本机地址且 Socket 保持回环限定。
- Android 17（target 37）自动 NSD 发现先请求 `ACCESS_LOCAL_NETWORK`，Android 13+ 先确认通知权限与渠道可用；权限拒绝不得在后台反复请求。通知会话、发现、取消及迟到回调按独立 token 隔离，配对码绝不进入持久状态或日志。
- 无线流程、协议/证书测试与设备验收边界详见 `docs/implementation/WIRELESS_ADB_VALIDATION.md`；云端 JVM TLS 与双包构建不能代替 Android 实际配对/JNI/通知/私有服务握手验收。
- 配对库的 `moe.shizuku.manager.adb.PairingContext` 是上游二进制 JNI ABI，保留第三方包名及许可。它不是官方管理器的授权/配置入口。
- debug 使用 Android 标准开发签名；release 要求显式完整签名配置。禁止把调试证书用于正式发布。
- 本地纯逻辑、构建和静态测试不替代真实设备验收，记录详见 `docs/implementation/DEVICE_ACCEPTANCE.md`。
