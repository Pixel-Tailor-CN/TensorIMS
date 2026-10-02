# 任务 2：后端调度、官方适配与恢复日志

## 实现范围

- `privilege/BackendModels.kt`、`BackendStateMachine.kt`、`OperationType.kt`、`PrivilegeBackend.kt`：公共后端契约、纯逻辑状态机、固定组件映射及明确的未启动/未知异常。
- `privilege/OperationCoordinator.kt`：业务历史、Instrumentation、Broker fallback、模式切换、显式内置启动与服务关闭共享串行边界。切换/启动使用立即拒绝，不进入延迟队列；原子 activeOperation 校验防止继承协程上下文的并发子任务同时执行。
- `privilege/OperationJournal.kt`、`RecoveryPolicy.kt`：`noBackupFilesDir/privilege_operations` 中保存操作 ID、模式、类型、epoch、boot count、阶段、是否已派发、SIM 身份摘要集合与原值备份引用。先 fsync 临时文件、原子替换，再 fsync 目录；日志损坏按失败关闭处理，不视作无任务。
- `privilege/OfficialBackend.kt`：官方连接/授权/版本检查、固定 Instrumentation 与显式官方会话。客户端断开不会调用任何官方服务关闭接口。
- `privilege/PrivilegeRuntime.kt`：仅按明确选择路由；UNSET 不执行；不跨模式 fallback。独立监督任务继续等待 watcher，调用者超时不会取消后台执行；迟到回调需要操作 ID 与 epoch 匹配。
- `ShizukuProvider.kt`：保留原 facade 签名；所有执行改为固定 OperationType。Broker fallback 进入同一 Runtime 事务；新增严格 SIM 读取结果，避免失败刷新被当作无 SIM。
- `ConfigurationOperations.kt`、`AutoRestoreController.kt`、`Application.kt`：共用锁、用户解锁后初始化、只响应所选 READY 后端与相同 epoch。自动恢复不启动 root/ADB、不配对、不切模式。

私有服务、AIDL、显式会话和 `EmbeddedBackend` 由任务 3 实现，本任务仅消费其接口。

## 新增辅助公共接口

在计划公共接口之外增加以下接口，供 UI/启动器使用：

- `PrivilegeRuntime.startEmbedded(action: suspend () -> String?): String?`
- `PrivilegeRuntime.canStartEmbeddedForRecovery: StateFlow<Boolean>`
- `PrivilegeRuntime.canRequestOfficialPermissionForRecovery: StateFlow<Boolean>`
- `PrivilegeRuntime.canRecoverPersistentVolte: StateFlow<Boolean>`
- `PrivilegeRuntime.recoverPersistentVolte(): String?`
- `ShizukuProvider.readSimInfoResult(context): privilege.SimReadResult<SimSelection>`，字段为 `sims` 和 `error`

严格 SIM 读取保留兼容的 `readSimInfoList` 包装；UI 应仅在 `error == null` 时更新列表和所选卡。

## 关键恢复裁定

1. 超时后旧 watcher 保留；新操作、fallback、模式切换均不能越过安全门。即使预检晚返回，也不得在超时后开始原写入。
2. 同一次开机且旧终态/清理不明，不能通过重新连接或只读核对掩盖未知任务；设备重启或可信终态+清理是前置证据。
3. 若重启后内置服务尚未启动，允许用户显式启动同一已选模式，仅用于恢复连接；仍保留恢复日志及普通写入/切换禁令。官方恢复期也允许显式重新授权。
4. 核对使用原操作对应的只读入口，并校验原目标 SIM 摘要；VoLTE 回读检查原值备份，Captive Portal 检查两键，CarrierConfig 读取完整快照。核对不合成成功历史。
5. 未确认的持久化 VoLTE 写入仍有备份时，普通写入保持禁用；仅通过 UI 明确确认的专门恢复入口恢复原值。恢复自身先落盘，超时同样保留后台任务和安全门。
6. 写入前读取 SIM 身份，并传给显式会话，在成功取得委托后、业务块执行前再次校验。多卡请求携带逐卡摘要。
7. 发生未知结果时持久暂停自动恢复；即使之后 READY 或切模式，也不自动重放历史。用户核对后重新启用自动恢复才解除暂停。
8. Broker fallback 只接受写事务明确返回的 `BROKER_RETRY_ALLOWED`：该标记证明结构化权限拒绝发生在任何成功或未知写入之前。结果文本和空结果都不能授权重试。写入 watcher 空业务结果，即使清理已确认，也保留需要核对的恢复日志。
9. 业务预检使用 `OperationArguments.preflight`，完整保留类型、动作和键白名单，但允许 Runtime 尚未捕获的 SIM 身份缺席。取得身份后，官方/内置适配器及私有服务继续使用严格 `validate`；不放松服务端校验。
10. 模式切换在任何 disconnect/关闭副作用之前，先在纯状态机内原子进入 SWITCHING；失败恢复旧状态。显式连接同样原子进入 CONNECTING，阻止继承事务上下文的后台子协程在检查与副作用之间抢写。

## 测试证据

独立纯 Kotlin/JUnit 命令：`/tmp/tensorims-backend-tests.sh`，使用已缓存编译器和 JUnit，在 `/tmp` 生成类，不启动 Gradle。

- 状态机首轮 RED：9 项失败，`/tmp/tensorims-backend-red.log`；随后 GREEN。
- 日志/共享锁 RED：新增 5 项失败，`/tmp/tensorims-backend-red2.log`；14 项 GREEN，`/tmp/tensorims-backend-green2.log`。
- 并发 active lease 与超时禁止继续派发回归：16 项 GREEN，`/tmp/tensorims-backend-green4.log`。
- 重启/清理/同模式重连策略 RED：新增 6 项中 2 项按预期失败，`/tmp/tensorims-backend-red5.log`；22 项 GREEN，`/tmp/tensorims-backend-green5.log`。
- 严格 SIM 结果 RED：新增 4 项中 2 项按预期失败，`/tmp/tensorims-backend-red6.log`；最终已与全套 33 项共同 GREEN，`/tmp/tensorims-backend-green-confirmed.log`。
- `git diff --check` 已检查，无空白错误。

整合者负责同一共享工作树的最终 `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`。早期整合编译只出现隐藏 `UserHandle.identifier` 访问问题，现已改为当前 UID / 100000；后续完整编译与 lint 以整合者最终日志为准。离线 lint 缓存缺失属于构建环境问题，不能算作验证成功。

## 尚需验收

- 无模拟器或真机授权；未宣称真实 IMS、权限委托、双服务共存、多用户、重启恢复或文件系统 fsync 的设备侧行为验证通过。
- 中文业务提示与现有英文诊断沿用当前项目习惯；系统/平台异常的逐条本地化不在本次后端范围内。
- 日志损坏且无法证明旧操作目标/阶段时，保持恢复阻止；不会提供忽略日志直接写入的危险入口。
- 本任务不提交、不推送；所有变更由整合者统一审核及本地提交。

## 第二轮安全审查修复（源码及纯测试已验证）

- 接入完整 `OperationArguments.preflight`，解决 legacy/VoLTE/IMS reset 在身份捕获之前被严格校验拒绝的问题。身份捕获完成后再出站严格验证；对应身份要求策略测试由桥接任务维护。
- 新增 3 项纯状态机回归：关闭之前冻结写门、关闭失败保留旧模式、连接与活跃操作互斥。
- 新增 `BrokerRetryPolicy` 及 4 项回归：空结果不重放、错误文本不足以 fallback、明确写前权限拒绝允许同模式重试、未知/清理失败/委托占用优先阻止。
- 写入空业务结果标为 OPERATION_INDETERMINATE，保留终态与清理证据以便操作级只读核对，不再重试。
- 整合者分配单一时段后，使用 256 MiB JVM 堆顺序验证：真实源码 33/33 GREEN；隔离 `/tmp` 副本故意删除冻结并放宽 fallback，33 项中 7 项按预期失败（`/tmp/tensorims-backend-red-regressions.log`）；随后真实源码再次 33/33 GREEN（`/tmp/tensorims-backend-green-confirmed.log`）。共享源码没有临时回退。
- 全量 Android 编译、测试和 lint 仍由整合者统一运行；纯 Kotlin 通过不等于真机验证。

### 非阻断诊断限制

私有业务结果可用 `DIAGNOSTIC_WARNING` 区分可选 IMS 注册诊断失败和关键持久化回读失败。当前 facade 在前者保持 `imsRegistered = null`，UI 显示未知，不补为 false，也不把已验证的持久化写入误报失败；该额外 warning 文本尚未单独传入模型/UI。此为诊断详细程度限制，关键回读失败仍走恢复安全门。
