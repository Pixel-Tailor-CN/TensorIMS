# 打开应用时自动启动内置服务实施计划

> 执行方式：使用 `executing-plans` 在当前会话逐项实施；完成后安排一次独立代码审查。用户已明确要求写完计划后直接开始实现。

**目标：** 用户启用独立开关后，在有效前台打开应用时，使用既有无线 ADB 凭据有限尝试启动本包内置服务。

**架构：** 应用级控制器管理前台代际、显式启用代际和一次性启动许可；纯 Kotlin 策略负责去重与门禁。无线短时前台服务复用 CONNECT 路径，并在服务接纳、运行时安全门和 Binder 交付三个边界重新校验自动请求。

**技术栈：** Kotlin、协程 / StateFlow、Android Activity 生命周期、SharedPreferences、现有无线 ADB / Shizuku 桥接、Compose Material 3；不新增框架。

**设计：** [打开应用时自动启动内置服务设计](2026-10-13-embedded-auto-start-design.md)。

## 全局约束

- 默认关闭，偏好键 `app_settings / embedded_auto_start`，两个 flavor 独立保存。
- 仅 `EMBEDDED` 普通未连接状态可自动启动；UNSET、外置模式、READY、BLOCKED、BUSY、CONNECTING、SWITCHING、RECOVERY_REQUIRED 均不得派发。
- 只复用 CONNECT 与 `AdbIdentity.load(pairing = false)`；不配对、创建身份、使用 root、打开系统设置或电脑等待。
- 用户解锁、有效前台和必要权限是前提；开关开启时的明确操作可以申请权限，自动触发不能申请。
- 前后台去抖 700 毫秒；每个前台 / 显式启用请求最多一次，配置重建和应用内 Activity 交接不重复。
- 沿用连接发现 20 秒、整轮 150 秒、现有 Socket / 握手边界和短时通知。
- 保留 OperationCoordinator、epoch、未知结果、恢复门禁、安装身份及一次性挑战；关闭开关不停止已就绪服务。
- 不修改自动应用历史和 `restore_paused`；不在后台拉起闲置停止后的服务。
- 中文沟通与注释、英文日志；中英文资源和双语 README 同步。
- 验证日志与实施进度放在系统临时目录；正式文档仅保留设计与计划。

## 审查重点

1. 凭据检查期间用户关闭开关、切模式或启动手动任务：旧检查不能派发，取消不能影响新手动会话。
2. Service Intent 迟到或进程重建：自动来源不能退化成手动请求，也不能重新得到启动许可。
3. NSD 完成与应用退后台 / 配置重建竞争：没有前台授权不能进入引导，旋转不得产生第二轮。
4. 关闭开关与 Binder 握手竞争：撤销后的交付须拒绝，已确认就绪的服务不误停。
5. 初次只读连接刷新与普通业务占用：只等待初次刷新，不把启动排在用户业务或模式切换后执行。

## 文件与职责

以下路径均相对仓库根目录；实现包根目录为 `app/src/main/java/app/mystery0/ims/tensor/`，测试包根目录为 `app/src/test/java/app/mystery0/ims/tensor/`。

| 文件 | 职责 |
| --- | --- |
| `privilege/EmbeddedAutoStartPolicy.kt` | 后端状态与协调器门禁，区别等待只读刷新和拒绝业务占用 |
| `privilege/EmbeddedForegroundTracker.kt` | Activity 交接、700 毫秒后台确认、请求消费与显式启用代际 |
| `embedded/EmbeddedAutoStartPermit.kt` | 可撤销的自动来源许可与发现 / 引导阶段边界 |
| `EmbeddedAutoStartController.kt` | 应用级事件、凭据预检查、自动会话发起、原因展示和来源限定取消 |
| `embedded/WirelessAdbService.kt` | 自动会话来源、接纳校验、Scoped 取消与结果发布 |
| `embedded/EmbeddedLauncher.kt` / `EmbeddedConnection.kt` | 引导和最终交付时校验许可，防止迟到握手 |
| `privilege/PrivilegeRuntime.kt` | 暴露只读刷新状态及普通自动启动门禁，锁内再次检查 |
| `AppSettingsRepository.kt` / `viewmodel/AppSettingsViewModel.kt` | 偏好保存与用户明确启用入口 |
| `Application.kt` | 解锁后注册控制器，不因后台初始化启动 |
| `ui/components/EmbeddedAutoStartCard.kt` | 开关、一次性权限请求、自动启动原因与处理入口 |
| `ui/screens/BackendSettingsScreen.kt` / `ui/MainActivity.kt` | 状态连接与卡片展示 |
| `viewmodel/MainViewModel.kt` | 手动启动前撤销待发自动请求，保留现有手动动作门禁 |
| 中英文 `embedded_auto_start_strings.xml`、README、AGENTS.md | 用户文案和新约束 |

## 任务 1：纯逻辑门禁、前台代际与可撤销许可

**文件：** 新建三个纯逻辑实现文件；新建对应的 `EmbeddedAutoStartPolicyTest.kt`、`EmbeddedForegroundTrackerTest.kt`、`EmbeddedAutoStartPermitTest.kt`。

**接口：**

- `EmbeddedAutoStartPolicy.evaluate(status: BackendStatus, busy: Boolean, refreshing: Boolean): EmbeddedAutoStartDecision`。
- `EmbeddedForegroundTracker` 接收 Activity 身份与单调时间，提供 started / stopped / mainResumed / mainPaused / confirmBackground 事件，以及 explicitEnable / consume / pending 状态。
- `EmbeddedAutoStartPermit` 保存请求 ID、预期 epoch 和有效性回调，提供 `canContinue()`、`tryBeginBootstrap()`、`revoke(beforeBootstrapOnly: Boolean = false)` 与同步 `commitDelivery(commit)`。撤销与最终本地接纳使用共同原子边界，不持许可锁执行远端握手。

- [x] 先写真实行为测试：非内置 / 所有阻塞状态拒绝，READY 不重复启动；只读刷新可等待，普通 busy 不排队。
- [x] 测试配置重建、日志页交接、700 毫秒边界、首次进入、后台重新进入、同代际重复与重新启用。
- [x] 测试撤销后检查 / 派发拒绝，后台发现不能进入引导，引导已开始后仅离开前台不提前释放任务，epoch / 偏好失效仍拒绝。
- [x] 实现纯逻辑，并运行上述指定测试；预期全部通过，断言覆盖审查重点 1、3、4、5。

验证命令：

```powershell
.\gradlew.bat :app:testTensorDebugUnitTest --tests '*EmbeddedAutoStartPolicyTest' --tests '*EmbeddedForegroundTrackerTest' --tests '*EmbeddedAutoStartPermitTest' --stacktrace --console=plain
```

## 任务 2：为现有无线启动链增加自动来源和安全复核

**文件：** 修改 WirelessAdbService、WirelessSessionGuard、PrivilegeRuntime、EmbeddedLauncher、EmbeddedConnection；补充既有会话 / 挑战测试。

**接口：**

- `WirelessAdbService.startAutomaticConnect(context: Context, permit: EmbeddedAutoStartPermit): Boolean`，仅在当前进程保存许可，不由 Intent 重建。
- `WirelessAdbService.cancelAutomatic(context: Context, requestId: String)`，只取消匹配的自动来源会话。
- `WirelessAdbState` 增加自动来源和请求归属字段，默认仍为手动。
- `PrivilegeRuntime.canAutomaticallyStartEmbedded(epoch: Long): Boolean`；`startEmbedded` 接受可选自动 epoch 与有效性回调，原手动调用继续使用默认参数。
- `PrivilegeRuntime.connectionRefreshInProgress` 提供只读连接刷新状态。
- `EmbeddedLauncher.startWireless(port: Int, canContinue: () -> Boolean = { true }, automatic: Boolean = false)`。
- `EmbeddedConnection.expect` 接受有界交付许可与原子提交回调；最终提交内保留会话归属复核。请求结束先撤销许可再释放 token；电脑等待不使用自动许可。

- [x] 在会话测试中加入旧自动请求不能取消新手动请求、未知 / 重建 Intent 不拥有自动许可的覆盖。
- [x] 将来源、epoch 和许可保存为进程内会话归属；静态入口和 Service 接纳时均重新校验，不给自动请求恢复入口或业务排队。
- [x] Runtime 获取统一协调器后再次验证普通未连接状态及许可；所有固定引导仍在原事务中执行。
- [x] 在生成引导命令前、登记挑战时与 Binder 交付前复核许可；已认证完成的自动服务不因偏好关闭被误停，原手动取消语义保持不变。
- [x] 保留 20 / 150 秒、通知、地址筛选和现有取消清理；来源限定取消不得停止后来创建的 Service 会话。
- [x] 运行会话与 BootstrapLifecycle 针对性测试并编译两 flavor；预期旧入口和新增边界均通过，覆盖审查重点 2、4。

## 任务 3：应用级控制器、前台事件与开关偏好

**文件：** 新建 EmbeddedAutoStartController、EmbeddedAutoStartState；修改 Application、AppSettingsRepository、AppSettingsViewModel、MainViewModel。状态模型以当前真实后端状态覆盖历史 READY，不产生新启动请求。

**接口：**

- 控制器由 `Application` 持有，用户解锁后 `start(application)` 注册 ActivityLifecycleCallbacks。
- 控制器暴露只读 `state`、`setEnabled(enabled: Boolean)` 和 `manualStart()`；只读初始化不触发启动。
- Repository 暴露 `autoStartEnabled` 和 `setAutoStartEnabled`，默认 false。
- 主界面恢复事件使本轮可检查；所有应用 Activity 共同决定是否真正退后台。

- [x] 将纯逻辑 tracker 接入生命周期，700 毫秒确认后台；主界面恢复才接纳首次检查，非主界面进程不触发。
- [x] 只等待 connectionRefreshInProgress，其他 busy / 恢复 / 身份验证失败结束本轮，不排队；每轮的状态变化不产生第二个启动请求。
- [x] 在 IO 线程调用 `AdbIdentity.load(pairing = false)` 预检查；回到前台调度后重新验证请求、偏好、模式、epoch 和安全门。
- [x] 创建许可、调用自动 CONNECT，按匹配请求观察成功 / 失败；前台退出只撤销发现阶段，开关关闭撤销自己的任务。
- [x] 手动启动入口在成功接纳前消费本轮并撤销待发自动检查，不干扰进行中的手动流程。
- [x] 验证失效预检查、来源竞争、恢复态、重新启用与一次性检查；预期无自动配对、root、设置跳转或 restore_paused 修改。

## 任务 4：界面、翻译与维护约束

**文件：** 新建 EmbeddedAutoStartCard 和两语言资源；修改 BackendSettingsScreen、MainActivity、首页状态连接（需要时）、AGENTS.md、README.md、README_CN.md。

- [x] 特权模式页仅在内置模式显示卡片，未就绪时仍可管理开关；关掉开关保留已运行服务。
- [x] 用户明确启用时一次性请求必要权限，拒绝保留偏好；自动进入前台不申请权限。提供明确点击的权限处理入口。
- [x] 展示凭据、通知、本地网络、业务忙碌和恢复核对等原因；现有手动连接 / 配对按钮继续可用。
- [x] 正在自动连接时，页面及现有短时通知注明自动来源；首页不得把发现阶段表述为已就绪或无 SIM。
- [x] 补齐英文资源，保留既有后端模式值；AGENTS.md 允许明确启用后在有效前台有限 CONNECT，自动应用自身仍不启动服务。
- [x] 核对 XML、资源键、README 语言及设计覆盖，运行双包编译、单测和 lint。

## 任务 5：累计审查与验收

- [x] 独立审查累计变化，逐项检查审查重点；修复实际影响安全或功能的问题。
- [x] 最终运行：

```powershell
.\gradlew.bat :app:testTensorDebugUnitTest :app:testLegacyDebugUnitTest --stacktrace --console=plain
.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain
.\gradlew.bat :app:lintTensorDebug :app:lintLegacyDebug --stacktrace --console=plain
```

- [x] Windows JVM 不支持目录同步的既有测试，在 WSL/Linux 运行相同编译产物与完整测试类集合；不弱化 Android 持久性保障，不把宿主限制当作设备功能已验证。
- [x] 在不覆盖签名不同的正式包、不清数据 / 配对 / 原值备份的前提下，尽可能完成真实设备的启动和生命周期验证；不足的设备版本或功能场景明确报告。
- [x] 对照设计第 10 节逐项记录已验证与未验证项，更新本计划完成标记；正式文档不保存原始日志或逐轮审查记录。

所有实现完成前保持用户可审阅的工作区变化；是否提交最终实现按用户指令处理，不自动推送或发布。

## 验收覆盖与真机待验项

实施与独立审查已完成。最终双 flavor Debug 编译和 lint 通过；每个 flavor 的 41 个测试类、248 项完整测试在 WSL/Linux 使用相同编译产物通过。新增回归覆盖许可撤销与接纳竞争，以及服务断开后的状态展示。Windows JVM 的目录同步限制沿用项目已有处理，不修改设备持久性逻辑。

下表区分代码／纯逻辑验证与设备实际行为。首次宿主验收时没有设备连接，未安装或修改设备应用。

已补充 Android 17（API 37）模拟器双包验收：默认关闭、权限拒绝不重复申请、凭据缺失提示、实际无线配对、冷启动自动 CONNECT 至 READY、关闭开关与手动会话归属、READY 下导航与配置重建去重，以及重置后后端可用、历史恢复阻止标记和重新读取。ARM64 配对完成后，模拟器重启首次打开应用和闲置停止后再次打开的自动启动均已通过；后台闲置停止未自行触发重启。原始日志保存在系统临时目录，不纳入仓库。

模拟器的 x86_64 原生启动器构建不包含 x86_64 配对 JNI；无线配对验收使用默认 ARM64 包和模拟器 ARM64 转译，未清数据或配对身份。该模拟器 `ro.adb.secure=0`，不能代替真机 TLS 身份拒绝、授权被撤销及真实运营商 IMS 注册／通话验证；真实手机上的正式版未安装或覆盖任何测试包。

| 设计验收场景 | 已覆盖的验证 | 仍需真机确认 |
| --- | --- | --- |
| 默认设置、UNSET、外置模式 | 默认偏好与入口审查；模式门禁测试 | 默认界面与无自动弹窗 |
| 已配对后冷启动 | 既有凭据读取与 CONNECT 链路审查 | NSD、TLS 与最终服务就绪 |
| 重启但未打开应用 | 后台初始化不能消费请求的测试；生命周期入口审查 | 重启后不创建任务 |
| 解锁后首次打开 | 解锁注册及初次只读刷新门禁审查 | 重启后实际连接与缺条件提示 |
| 旋转、导航、日志页交接 | 慢重建、Activity 交接与去抖测试 | 设备生命周期与通知只出现一轮 |
| 服务就绪后重新打开 | READY 不启动测试 | 真实连接保留 |
| 闲置停止后再次打开 | 新前台代际只消费一次测试；不观察断开来重启 | 闲置停止后的再次启动 |
| 凭据失效或系统忘记身份 | 不创建身份的读取路径审查；失败保留手动入口 | Keystore 与 TLS 授权失败 |
| 无线调试不可发现或端口改变 | 有界发现及沿用动态端口路径审查 | 不同网络和系统上的发现 |
| 权限拒绝、通知渠道关闭 | 自动路径无权限请求；明确启用／处理入口审查 | Android 13–17 权限与通知实际表现 |
| 手动与自动竞争 | 会话所有权与单请求接纳测试；预检查失效审查 | 真实 UI 操作竞争 |
| 业务忙碌、模式切换、恢复核对 | 状态门禁测试；Runtime 锁内安全门审查 | 未完成设备操作下的门禁 |
| 关闭开关、epoch 变化与迟到握手 | 撤销／授权失效测试；原子提交并发测试；来源终结审查 | Binder 与 Service 超时竞争 |
| Provider／Instrumentation 后台初始化 | 无前台不能消费请求的测试；无后台触发器审查 | 真实后台初始化 |
| 自动启动与自动应用同时开启 | 两控制器独立、仅由 READY 驱动原恢复路径审查 | 实际历史回读和应用 |
| `restore_paused` 保持 | 自动启动不写暂停标记；暂停不授予启动特权的测试 | 未知写入后的实际恢复核对 |
| 两个 flavor | 双包编译、各自完整测试与 lint；包内偏好／身份审查 | 分别安装后的无线启动 |

真实设备矩阵仍待验收；宿主测试和模拟器通过不能代替真实 Pixel 上的无线启动、重启、权限及运营商网络验证。
