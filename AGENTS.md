# AGENTS.md

## 基本约定

- 与用户沟通时使用中文。
- 文档默认使用中文，双语 README 按各自语言维护：`README.md` 正文使用英文，`README_CN.md` 正文使用中文，语言切换链接可保留语言原名。代码注释使用中文；日志打印使用英文。
- 可保留 Shizuku、Instrumentation、CarrierConfig、IMS、VoLTE、VoWiFi、VoNR、AIDL 等通用英文技术名词。
- 修改代码前先理解当前实现和设备兼容约束，避免只针对一个 Android 版本做硬编码修复。
- 本项目不要求添加单元测试。代码变更后至少运行 `.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain` 验证两个 flavor 编译；适合纯 Kotlin 行为的新增逻辑可增加针对性单元测试。
- Git 提交信息使用中文；自动生成提交信息时也必须生成中文内容。

## 项目概览

TensorIMS 是一个面向 Google Pixel Tensor 设备的 Android 应用，用于读取和修改 IMS/运营商相关配置，开启或关闭 VoLTE、VoWiFi、VT、VoNR、Cross-SIM Calling、UT、5G NR 等功能，并提供少量与 Pixel 网络使用相关的设备级系统设置。项目通过显式选择的外置 Shizuku 或内置私有后端执行特权操作；无线 ADB 路径不要求 root。

主要适配和维护范围为中国大陆的中国移动、中国联通、中国电信网络下的 Pixel Tensor 设备。其他运营商因缺少相应测试条件，不作为主要适配和维护对象。此约定限定维护范围，不代表需要在代码中按地区或运营商限制使用，也不保证所有机型、系统版本和网络功能均可用。

项目原名为 Mystery00/TurboIMS，现由 Mystery00 以 TensorIMS 名称独立维护，仓库归属 Pixel-Tailor-CN 组织。仓库地址为 `https://github.com/Pixel-Tailor-CN/TensorIMS`，项目主页为 `https://pixel.mystery0.app`。项目构建 `tensor` 与 `legacy` 两个安装包，共用业务代码，各自支持外置 Shizuku 和私有内置后端。历史文档和上游仓库名称保留来源。

应用主要面向 Android 13 及以上系统，当前构建目标为 Android SDK 37。设备侧功能验证应优先在已安装并运行 Shizuku 的真实 Pixel 设备上完成。

## 构建环境

- Gradle wrapper：`9.7.1`
- Android Gradle Plugin：`9.4.1`
- Kotlin：`2.4.20`
- `compileSdk`：`37`
- `targetSdk`：`37`
- `minSdk`：`33`
- Java/Kotlin JVM target：`21`
- APK ABI：正式与默认 debug 仅 `arm64-v8a`；显式模拟器 debug 构建可增加 `x86_64`。Android 安装目录使用 ISA 名，ARM64 是 `arm64`，不能与 APK ABI 名 `arm64-v8a` 混用。
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
- `ShizukuProvider.kt`：保留 Shizuku 客户端 Provider 与业务 facade，固定操作交由 `PrivilegeRuntime` 路由。
- `LogcatRepository.kt`：应用日志采集和导出相关逻辑。
- `ConfigurationRepository.kt`：共用配置历史、应用参数构造、自动恢复操作顺序和开机进度。
- `AutoRestoreController.kt`：按用户开关监听 Shizuku 连接与授权结果，执行有限重试的自动恢复。
- `AutoRestoreNotifier.kt`：自动应用一轮结束后汇总结果，先显示文本 Toast，未确认显示时使用通知兜底。
- `ConfigurationOperations.kt`：复用 `OperationCoordinator` 串行协调手动/自动业务与历史提交，和模式切换共享安全边界。

## 后端开发约束

- 外置模式面向独立安装的 Shizuku 及兼容其 API 的 fork；界面使用“外置 Shizuku”，内部保留 `BackendMode.OFFICIAL` 和既有偏好值以兼容升级。
- 未选择模式（UNSET）不执行特权操作；外置模式拒绝授权不自动切换内置模式，后台恢复不替用户配对、提权或启动服务。
- 业务与模式切换统一经 `PrivilegeRuntime` / `OperationCoordinator` 协调；fallback 必须保持在同一后端事务中，不削弱业务写入的取消保护。
- Instrumentation 显式接收操作会话，不读取全局 Shizuku Binder；私有桥接只提供固定操作，保留 shell/root UID、安装身份和一次性挑战校验，不新增通用命令或任意 Binder 转发。
- 外置后端断开只释放客户端引用；内置停止仅作用于本包服务。权限委托冲突报告 `DELEGATION_BUSY`，不得清理其他客户端权限或按进程名停止 Shizuku。
- 客户端超时不代表设备端写入停止；未知结果或权限清理失败保持恢复态，不清理未完成操作日志或持久化原值备份。
- 恢复态应显示具体原因、待核对操作和下一步；SIM 读取受阻不能表示为无 SIM，自动应用的安全暂停必须在界面可见。操作记录无法读取时，使用与原记录指纹绑定的独立重启证明；仅在用户确认、所选后端验证及当前状态只读核对和权限清理确认后归档原记录，保留所有原值备份并继续暂停自动应用，不宣称原操作成功或原目标已恢复。
- 详细方案见[双模式设计](docs/plans/2026-10-01-tensorims-dual-mode-design.md)、[双包发布设计](docs/plans/2026-10-02-dual-package-release-design.md)与[原生电脑 ADB 启动器设计](docs/plans/2026-10-11-native-adb-starter-design.md)，不要在本文件重复实现流程。

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
- 自动恢复默认关闭；由用户在主界面主动启用。仅当前选定后端 READY 时恢复历史，外置模式缺少权限时每次开机最多自动请求一次，用户拒绝后不循环弹窗。自动恢复不会配对、提权或启动任何服务。
- 自动恢复仅作用于当前活动 SIM；按最后成功操作的序号选择全卡或单卡配置。旧历史无序号时单卡优先。重置成功保留界面历史并写入恢复阻止标记，避免重启后撤销重置。持久化 VoLTE 不参与自动恢复。
- 自动恢复元数据和开关保存在 `auto_restore` SharedPreferences，历史中的 `_restore_revision` / `_restore_reset` 不属于 Feature。以系统开机计数去重，同一次开机已成功恢复的相同配置不重复应用；用户重新启用开关可重新尝试。
- 界面明确表述为“后端就绪后自动应用配置”，仅重启设备不会直接应用，必须先启动当前选定后端并获得授权。自动应用按一轮重试汇总实际写入结果，重复跳过的历史不提示；优先 Toast，5 秒未收到显示回调时尝试通知。通知需要 `POST_NOTIFICATIONS` 授权及结果渠道开启；启用开关时申请通知权限，拒绝通知不阻止自动应用。后台不主动弹通知权限窗口。
- 持久化 VoLTE 是独立的即时操作，不属于 `Feature` 配置草稿。原始订阅值（含 -1）保存在 `noBackupFilesDir/persistent_volte_<subId>.json`，以 SIM 标识摘要校验；失败回退失败时保留记录供恢复。重置配置先恢复所选活动 SIM 的原始值，再清除 CarrierConfig；未激活 SIM 的备份保留至重新激活后恢复。
- Captive Portal 属于设备级系统网络设置，不属于 `Feature`，不写入 `sim_config_<subId>`，不参与自动恢复，也不受当前 SIM 选择影响。
- Captive Portal 首版只管理 `captive_portal_http_url` 和 `captive_portal_https_url`；“系统默认”通过删除这两个 SettingsProvider 覆盖值实现，不硬编码第三方默认地址。
- `Feature` 和 `FeatureConfigMapper` 是 IMS 功能开关到运营商配置键的主要映射入口。
- IMS 配置页从系统实时读取，缺键／权限失败不能补成 Feature 默认值。普通开关关闭显式写入 false（5G NR 写空可用性数组），仅下发用户明确修改或预设指定的项目。所有 SIM 的不同值显示“各卡不同”，不得自动补成关闭。
- 参数类只有在 `CarrierConfigManager.getDefaultConfig()` 提供完整、类型匹配的默认参数时支持单项恢复；界面必须说明它是系统默认参数、不是运营商默认。字符串和无完整默认参数的覆盖项独立分组，恢复引导至高级工具重置，不能用空字符串或省略键假装恢复；不引入原值备份，不为单项恢复清空全卡覆盖。
- 新历史以 `_config_version=2` 保存稀疏、明确的目标及 SIM 身份摘要 `_target_identity`，逐卡回读成功才保存；自动恢复新历史同样检查身份并回读。旧历史保留只写开启项的语义，旧 false 不迁移为禁用。批量部分失败保留待应用目标，只保存成功卡历史；持久化 VoLTE 与 Captive Portal 不参与此协议。
- 修改运营商配置时优先走 `ImsModifier`，必要时由当前后端在同一操作事务内触发 `BrokerInstrumentation` fallback，不能跨模式。
- UI 不直接执行业务写入逻辑，业务动作应放在 ViewModel 或特权入口中。

## 内置服务闲置停止

- “闲置时自动停止内置服务”默认关闭，延迟默认 2 分钟、可设整数 1–30 分钟，保存在各安装包独立的 `app_settings` 中。卡片仅在特权模式页内置服务就绪时显示，隐藏不重置偏好。
- `EmbeddedIdleStopController` 观察整个应用的前后台状态（包括日志页）、无线启动、自动恢复和统一操作协调器；返回前台取消等待，后台任务结束后重新等待完整延迟。协调器完成代际防止快速任务的 busy 状态被合并后漏掉计时重置。
- 到期后经 `PrivilegeRuntime.stopEmbeddedWhenIdle` 和服务端双重检查，仅停止本包内置服务；操作结果未知、未完成日志或权限清理未确认时不强停。停止保留内置模式并递增 epoch，不删除配置、备份或配对信息，不自动重新启动，也不控制外置 Shizuku。
- 计时不使用额外保活、闹钟、唤醒锁或前台服务，不保证精确时间。系统可能先回收应用；既有客户端死亡/租约安全退出机制不受开关影响，且必须等待任务结束与权限清理确认。文案不承诺立即退出、固定秒数退出、无需再次操作无线调试或明确省电收益。

## UI 信息架构约定

- 应用设置通过首页右上角齿轮进入；语言使用 Android 13+ LocaleManager 持久化，空语言列表表示跟随系统，与系统应用语言设置同步；选择器语言列表读取 `locales_config.xml`，新增语言必须同时提供相应翻译资源。
- 应用设置集中管理主题（默认跟随系统）、语言单选确认、自动日志、应用日志入口及版本/官网/Telegram 信息。主题与自动日志保存在独立 `app_settings` 偏好中，两包分别保存；主题同时作用于主界面和日志页。
- 自动日志默认关闭；开启后应用进程在用户解锁后、自动恢复初始化之前启动采集，关闭后仅在日志页仍打开时继续。日志仅保留内存中最近 2000 条，不提供进程保活或跨进程结束的日志持久化。应用日志入口只放在设置页。

- 首页承担设备/Shizuku 状态、SIM 选择、IMS 状态入口、功能分类导航和自动恢复开关。特权模式入口归入设备卡，不与业务分类并列；单卡紧凑展示并绑定真实 subId，多卡保留所有 SIM 批量配置选择。IMS 状态使用底部面板，面板内按单卡读取、刷新及二次确认重启，不把能力推断当作实际注册技术。
- IMS 配置页面以实时 CarrierConfig 初始化目标草稿，区分已读取、待应用、应用核对和失败状态；按“通话 / 网络 / 显示 / 自定义覆盖（恢复需重置）”分组，应用配置仍是主操作。预设与历史仅修改草稿，重新读取前确认丢弃未应用修改。
- 系统网络页面承载与 SIM 无关的设备级网络设置；Captive Portal 是首个入口。
- 高级工具页面仅承载持久化 VoLTE 的状态、启用与恢复。运营商配置重置位于 IMS 配置页，保留单卡门禁、目标与影响确认；成功后丢弃对应草稿并重新读取，失败保留草稿并使旧快照失效。
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
- README 面向用户，只写功能、安装与使用；本文件只保留开发约束，设计细节留在 `docs/plans/`。逐轮实施报告、审查记录和原始测试日志使用批准的临时目录，不放入正式文档。

## 安装与启动兼容约束

- `tensor` applicationId 为 `app.mystery0.ims.tensor`，`legacy` 为 `io.github.vvb2060.ims`；namespace 和第一方包名统一，不复制业务代码或按 flavor 删减后端。Provider authority、Instrumentation 目标包和私有会话身份使用当前 applicationId，不能串包。
- 保留现有配置名称、键及私有原值备份；legacy 原地升级要求签名一致且 `versionCode` 更高，不能重命名或清理数据；tensor 独立保存数据，不自动迁移。两包不能并发或同时自动恢复修改同一 SIM。
- 无线启动须由用户操作，使用系统配对界面与短时通知，只接受本机地址；不得要求手填端口、申请悬浮窗或记录配对码。Android 17 自动发现需本地网络权限，Android 13+ 需通知权限与可用渠道，拒绝后不在后台反复请求。
- 电脑 ADB 启动必须有用户主动开启的有限等待；取消、超时或进程结束后须重新开启等待，应用更新后须重新获取命令。保留固定原生入口、危险环境变量清理与一次性 claim 校验，不能扩展成永久脚本或通用 shell 执行器。
- `moe.shizuku.manager.adb.PairingContext` 属于第三方 JNI ABI，保留包名和许可，不是外置管理器的授权入口。
- debug 使用开发签名；release 必须显式提供完整正式签名配置，不得回退开发证书。编译、静态检查或模拟器验证不能替代真实设备功能验收。
