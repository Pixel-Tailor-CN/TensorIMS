# TensorIMS 单 APK 双模式实施计划

> **历史文档说明（2026-10-02）：** 用户新增要求以两个 flavor 同时发布新包 `app.mystery0.ims.tensor` 与旧包 `io.github.vvb2060.ims`，每包仍完整包含官方/私有内置双模式；本文“仅一个 APK、不构建旧包、不增加 flavor”的限制已由[双包发布补充设计](2026-10-02-dual-package-release-design.md)和[实施计划](2026-10-02-dual-package-release-plan.md)替代。namespace 与第一方代码仍为 `app.mystery0.ims.tensor`，原其他安全边界不变。原计划“禁止推送/PR”的本地阶段限制亦已由后续明确授权更新并推送现有 PR #39 所替代；仍不得合并或手动触发发布。下文保留历史方案、命令与当时验证状态，不作为当前双包构建或授权范围的依据。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 在一个 app.mystery0.ims.tensor APK 内交付显式选择的官方与私有内置后端，保持原有业务和恢复语义。

**Architecture:** 业务继续通过原有 facade 调用，由 PrivilegeRuntime 和统一 OperationCoordinator 把固定操作分派到 OfficialBackend 或 EmbeddedBackend。内置进程由当前 APK 的 app_process 入口加载，以私有 Provider 挑战握手交付窄 AIDL；Instrumentation 使用显式会话而非静态默认后端。启动器仅在用户主动要求时执行 root 或本机无线 ADB。

**Tech Stack:** Kotlin、Compose、AIDL、Android 13+、SDK 37、Java 21、现有 Shizuku 13.1.5 API、经固定官方来源审阅的 ADB 配对实现。

**Spec:** docs/plans/2026-10-01-tensorims-dual-mode-design.md

## Global Constraints
- applicationId、namespace、全部第一方代码包：app.mystery0.ims.tensor；仅一个 APK，不添加 flavor。
- 所有工作仅在 dot 云端文件系统；禁止桌面任务、远端推送、PR、部署及真实设备凭据生成/授权。
- UNSET 不启动特权服务、不写入；官方拒绝授权不自动切内置；不自动改变全局 ADB 设置。
- 内置服务不提供通用 Binder 转发、任意组件、任意命令、rish、客户端扫描或官方授权配置。
- 写入、fallback、自动恢复、模式切换、服务关闭共享安全调度；超时不是设备端结束。
- 私有 VoLTE 原值备份位于 noBackupFilesDir，模式切换不能清理；结果未知禁止重放。
- 中文注释、中文提交；英文日志且不记录配对码、私钥、完整 SIM 标识。
- 无模拟器/真机条件：编译、测试、lint 与静态检查可以完成；不得声称 IMS 或双服务共存真机通过。

## Review Focus
- 不同 Android 用户、陈旧启动挑战、UID 与安装签名变化必须拒绝握手/请求（任务 3）
- 超时后迟到 watcher 与模式切换/进程重建不得释放尚在运行写入的安全门（任务 2）
- ADB 部分读、错误帧长、断联、配对撤销及取消不能泄漏密钥或误报启动成功（任务 4）
- 返回、旋转、连续点击与后台恢复不能默认选择模式或重复启动（任务 5）
- 原签名环境缺失仍可安全 debug；release 不应默默变成调试签名（任务 1、6）

## 公共接口约定
这些名称用于独立文件集并行开发；签名变更须先通知整合者。

- privilege/BackendModels.kt：BackendMode { UNSET, OFFICIAL, EMBEDDED }；ConnectionState { DISCONNECTED, CONNECTING, READY, BUSY, BLOCKED, SWITCHING, RECOVERY_REQUIRED }；BackendStatus(mode, connection, epoch, runtimeUid: Int?, version: String?, errorCode: String?, message: String?)，isReady 只在 READY。
- privilege/PrivilegeRuntime.kt：object PrivilegeRuntime，initialize(context: Context)，val status: StateFlow<BackendStatus>，suspend chooseMode(mode: BackendMode): String?，refresh()，requestOfficialPermission()，suspend execute(context: Context, operation: OperationType, args: Bundle?, canStart: () -> Boolean = { true }): Bundle?。
- privilege/OperationType.kt：READ_SIMS, READ_CAPABILITIES, READ_CONFIG, APPLY_CONFIG, BROKER_CONFIG, RESET_IMS, READ_PERSISTENT_VOLTE, SET_PERSISTENT_VOLTE, RESTORE_PERSISTENT_VOLTE, READ_CAPTIVE_PORTAL, WRITE_CAPTIVE_PORTAL, RESET_CAPTIVE_PORTAL；固定映射组件，写入标志由类型决定。
- privilege/PrivilegeBackend.kt：val mode: BackendMode；suspend connect(context: Context, epoch: Long): BackendStatus；suspend execute(context: Context, operation: OperationType, arguments: Bundle, operationId: String, epoch: Long): Bundle?；suspend disconnect(): Boolean。执行完成必须表示 watcher 已结束与清理已确认；超时作为结果未知上报，保持后台安全门。
- embedded/EmbeddedConnection.kt：object EmbeddedConnection，val changes: StateFlow<Long>，fun current(): IEmbeddedServer?，fun expect(challenge: String)，fun clear()。定向 Provider 仅接受预期 shell/root 与当前启动挑战；实际握手由 EmbeddedBackend 完成。
- embedded/EmbeddedLauncher.kt：class EmbeddedLauncher(context: Context)，suspend startRoot(): String?，suspend pair(port: Int, code: String): String?，suspend startWireless(port: Int): String?。null 仅表示握手确实收到且验证通过；失败返回分阶段的安全消息。connect/pair 固定本机地址，不接受任意远端主机。
- bridge/IEmbeddedServer.aidl：Bundle handshake(String challenge, IBinder clientToken)；void execute(in Bundle request, IEmbeddedResult callback)；boolean shutdownOwnedServer(String instanceId)；Bundle getStatus()。
- bridge/IEmbeddedResult.aidl：oneway void onResult(String operationId, long epoch, in Bundle result)。
- bridge/IPrivilegeSession.aidl：void beginDelegation(String operationId)；void endDelegation(String operationId)；int getSlotIndex(String operationId, int subId)；void resetIms(String operationId, int slotIndex)。服务端会话唯一且固定操作/目标校验，不接收任意 Binder。
- bridge/BridgeProtocol.kt：VERSION=1，PACKAGE=app.mystery0.ims.tensor，AUTHORITY=app.mystery0.ims.tensor.embedded.bridge；Bundle 键统一常量由任务 3 定义并告知任务 2/4。

### Task 1: 新身份与可复现本地构建
**Files:** app/build.gradle.kts、signing.gradle、全部第一方源码/测试目录、AGENTS.md、README*.md、CI 示例
**Produces:** 新包名工作树；标准 debug 签名路径；release 缺失配置明确失败。
- [x] 添加静态身份检查，先在旧包运行并确认失败
- [x] 机械迁移第一方命名，不替换第三方契约；迁移文档明确两个应用身份不共享数据
- [x] 修改签名：debug 默认 Android debug keystore；只有完整显式发布配置才签 release；不得读取生产秘密
- [x] 跑原有测试与 debug 编译，检查 Manifest 与旧包残留；记录日志
- [x] 中文提交

### Task 2: 后端状态机、官方适配与恢复日志
**Files:** privilege/*、ShizukuProvider.kt、AutoRestoreController.kt、ConfigurationOperations.kt、Application.kt；测试 privilege/*Test.kt
**Consumes/Produces:** 公共接口中的 BackendModels、OperationType、PrivilegeBackend、PrivilegeRuntime；消费 EmbeddedBackend。
- [x] 先写状态测试：UNSET 不执行；拒绝不切模式；BUSY/RECOVERY_REQUIRED 拒绝切换；epoch 丢弃迟到回调；进程重建未完成日志阻止写入；测试先红
- [x] 实现独立纯逻辑状态机，操作日志先可靠落盘，只有终态与清理确认后完成
- [x] 封装官方 API/权限/版本/Watcher；所有 facade 调用转固定 OperationType，fallback 不跨模式
- [x] 自动恢复只听当前后端 READY 与 epoch，不触发 root/ADB/配对；保持原有开机/revision/SIM 去重
- [x] 测试绿；与原有业务测试联合验证；中文提交由整合者统一执行

### Task 3: 私有服务、窄 AIDL 与显式会话
**Files:** bridge/*、embedded/EmbeddedServer.kt、EmbeddedServerMain.kt、EmbeddedConnection.kt、EmbeddedBridgeProvider.kt、privilege/EmbeddedBackend.kt、privileged/*（会话改造）；AIDL；必要 stub
**Consumes/Produces:** 公共接口全部私有桥接类型；消费 OperationType、PrivilegeBackend。
- [x] 先写认证策略/请求白名单/租约/会话有效期测试：重放、不同 UID、未知操作、版本不匹配、过期请求应拒绝
- [x] 实现私有 app_process 服务，从可信安装 APK 与 PackageManager 派生 UID/签名；定向握手；认证每次业务请求
- [x] 固定映射 Instrumentation 并传会话 Binder；改造所有特权入口，不从静态默认后端取 Binder；有限权限集合与明确 DELEGATION_BUSY
- [x] 仅已成功建立的委托允许清理；清理失败状态传回；Binder 死亡/租约等待操作清理，不按进程名 kill
- [x] 实现空闲 owned-server shutdown 和客户端 EmbeddedBackend；测试绿、静态查通用 Binder/命令面

### Task 4: 独立无线 ADB 与 root 启动
**Files:** embedded/EmbeddedLauncher.kt、embedded/adb/*、适用 JNI/原生资源、assets/licenses/*、来源清单
**Consumes/Produces:** 公共 EmbeddedLauncher；消费 EmbeddedConnection 与 BridgeProtocol。
- [x] 先写本机端口/配对码/启动命令转义/ADB 帧校验测试，确认错误输入拒绝
- [x] 审阅固定官方 Shizuku ADB 客户端与配对代码，移植必要代码；私有密钥以 Android Keystore AES 加密存储于 noBackup；配对码只驻内存
- [x] JNI 包名若可重编则改为新包；官方预编译 JNI 必须有来源和可验证哈希，说明第三方包身份保留原因
- [x] 启动每次解析当前 APK、校验版本/路径，固定 app_process 入口；root 仅显式 su，ADB 只本机 TLS 连接；挑战与握手核对
- [x] 许可保留 Apache 2.0/MIT/所需第三方 NOTICE；测试绿；无真实设备配对或提权

### Task 5: 首次选择、设置切换与状态 UI
**Files:** ui/、viewmodel/MainViewModel.kt、model/Shizuku.kt、res/values*/strings.xml（新增独立 backend_strings.xml 避免冲突）
**Consumes:** PrivilegeRuntime.status/chooseMode/refresh/requestOfficialPermission；EmbeddedLauncher。
- [x] 先写纯 UI 决策测试：未选择、重复点击、BUSY、RECOVERY_REQUIRED 与拒绝授权显示准确动作
- [x] 首次进入显示两个同等选项，可取消；设置固定入口显示模式/身份/连接/授权/版本
- [x] 切换确认说明配置保留和内置停止；写入中不排队切换；失败保留选中模式
- [x] 内置显示无线调试系统入口、独立配对端口/六位码、连接端口与 root 手动启动；不得宣称选择即授权
- [x] 首页和自动恢复文案后端中立；迁移提示保护旧版备份；测试绿、Compose 编译通过

### Task 6: 整合、验收与交付
**Files:** app/src/main/AndroidManifest.xml、app/proguard-rules.pro、docs/implementation/*、测试、交付产物
- [ ] 注册私有 Provider/权限和 main/session R8 keep；开启 AIDL，检查合并 Manifest
- [ ] 全量 testDebugUnitTest、assembleDebug、lint；修复范围内问题，验证签名与唯一包身份
- [ ] 独立全分支安全/设计审查：身份、IPC、超时、备份、自动恢复、JNI、许可证与失败恢复；必要修复后重跑
- [ ] 保存提交、差异包、APK、校验和与验证报告；标明真机未验证及发布前验收清单
- [ ] 仅本地提交；不推送/发布；debug 测试签名不作为长期正式证书

## 实施后的验收说明

任务 1–5 已完成代码与各自测试；CarrierConfig 重置的独立成功判定仍受系统接口可观察性限制，明确返回未确认并保留恢复门。静态源码审查对该项为有条件通过，不视为完整重置成功能力。最终整合与真实设备验证见 docs/implementation/VALIDATION.md 及交付验证清单。
