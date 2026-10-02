# 私有桥接与显式会话实施报告

日期：2026-10-01。范围：实施计划任务 3；只修改云端工作树，不连接设备，不推送。

## 已实现的路径

- `EmbeddedServerMain` 是可由当前 APK 的 `app_process` classpath 加载的实际 Java 入口。仅允许 root/shell UID；按指定 Android 用户读取 PackageManager 当前安装 UID、当前签名摘要、版本和 APK 路径，核对启动参数及编译版本。启动参数中的证书不能替换安装记录信任根。
- 通过 framework 的定向 `getContentProviderExternal` / `call` 交付私有 Binder，没有广播发现、客户端扫描、官方管理器路径或官方授权数据库。释放外部 Provider 引用使用目标用户参数。
- `EmbeddedBridgeProvider` 先检查真实 Binder 调用 UID 为 0/2000，再消费本次启动挑战；只有当前选择内置模式才接纳。应用随后核对协议、挑战、实例、运行 UID、版本、安装签名、APK 路径与固定能力集合。
- `IEmbeddedServer` 只提供握手、固定枚举执行、状态及按实例关闭。请求包含协议版本、operationId、epoch、到期时间与受约束参数；拒绝重放、旧 epoch、超期、未知枚举、未知参数与任意桥接元数据。
- `IPrivilegeSession` 只提供 begin/end、getSlotIndex、resetIms、isImsRegistered。最后一个方法是相对原计划的最小接口补充：原有 IMS 能力和 VoLTE 查询需要该固定读取，不因此开放任意 Binder。
- `OfficialPrivilegeSession` 与内置服务共用显式会话实现；官方 Binder 包装只存在于官方适配器。所有业务 Instrumentation 继承 `SessionInstrumentation`，从本次参数取得会话，不再读取 Shizuku 静态 Binder 或自行包装 phone/subscription 服务。
- 参数校验同时应用于官方和内置：只读 VoLTE 只允许 query，只读 Captive Portal 只允许 read；对应写入/恢复动作固定。CarrierConfig 新协议按 Feature 类型校验；历史协议只接受原 buildBundle 产生的键及类型。桥接元数据不会进入 CarrierConfig。
- SIM 写入要求提供单卡/全卡身份摘要（新 CarrierConfig 协议本身的摘要同样有效）；委托建立后、业务操作之前重新核对全部目标。原持久化 VoLTE 备份目录、回滚和恢复行为保留；回滚失败、恢复中断或写后回读不明会明确返回 OPERATION_INDETERMINATE，让 Runtime 保留恢复记录。

## 生命周期与安全保证

- 会话固定 operationId、epoch、目标 UID、操作和 subId；窄 telephony 方法限制操作种类及目标，resetIms 的 slot 必须先由本次 getSlotIndex 解析。
- 权限集合按操作明确列举；无 null 全权限委托。只有成功取得的本次委托才清理。系统全局委托冲突保持 `DELEGATION_BUSY`，不清理其他应用的委托，也不触发 Broker 权限 fallback。
- begin 的 Binder 传输异常不能证明未取得委托，按未知状态处理；清理失败不盲目重试旧版无 UID 清理。旧无参和新带 UID 的 framework 停止兼容继续集中封装。
- 会话过期阻止新业务方法，但本次 operationId 的 end/最终清理仍被允许。服务租约不强杀执行中的操作。
- Watcher 完成必须匹配 operationId 与 epoch；旧 watcher 不能清除新请求的 active 状态。客户端只接收当前 id/epoch 回调。
- `EmbeddedBackend.execute` 等待终态，不自行按 15 秒取消；Runtime 负责向 UI 报告超时。服务死亡或清理不明抛出 `OperationUncertainException`，不假定写入未发生。
- 应用 Binder 死亡后，服务等待运行操作结束和清理，再按空闲租约退出。握手后尚未确认连接也有有界租约，避免验证失败时留下无限期空闲进程。
- 关闭需当前安装调用者和匹配实例，且只能空闲关闭；关闭请求先原子撤销新业务，再延迟退出当前进程。租约到期的退出也先原子撤销。没有按进程名、旧 PID 文件、`shizuku` 名称或官方服务执行 kill。
- `EmbeddedConnection.expect` 先摘除旧死亡 Binder、监听器和元数据，启动代际隔离旧死亡通知与迟到握手，避免它们撤销新挑战。
- 启动取消后的 `EmbeddedConnection.clear` 尝试关闭已认证空闲实例；忙或关闭不确定时保留引用，不能遗忘旧操作。

## 本任务验证

已在本任务实际运行：

`./tools/test-bridge-policy.sh`

初轮结果：20 项 JUnit 测试通过（本轮审查修复后为下述 31 项），0 失败。初始缺失实现及追加生命周期接口时分别观察到红灯，随后实现转绿。

覆盖：不同 UID/用户/签名；错误/过期/重放挑战；协议/操作白名单；请求过期/旧 epoch/operationId 重放；死亡租约不终止运行操作；清理失败阻止请求与关闭；未取得委托不清理；清理未知保留；超期仍允许清理；只读 action 拒绝写入；未确认握手租约；活跃客户端空闲保留；迟到完成不影响新操作；关闭/租约先撤销再退出；回滚/恢复/回读不确定性；死亡后重启代际与迟到回调。

证据：`bridge-test-red.log`、`bridge-additional-red.log`、`bridge-shutdown-red.log`、`bridge-recovery-red.log`、`bridge-connection-red.log`、`bridge-test-green.log`。

另运行 `python tools/check_bridge_safety.py`，先由两处历史完整参数日志触发红灯，去除日志载荷后转绿；证据为 `bridge-static-red.log` 与 `bridge-static-green.log`。

最后检查 `git diff --check` 未报空白问题。

静态检查：`privileged/` 不再包含 Shizuku、ShizukuBinderWrapper 或 ServiceManager；私有 AIDL 和服务无通用 transact、命令执行、任意组件或进程名清理接口。编译、完整单元测试和 lint 由整合任务统一运行；本报告不以纯策略测试替代 Android 编译或设备验证。

## 未验证与发布门槛

- 没有模拟器或真实 Pixel 运行证据，未声称 root/无线 ADB 私有服务启动、IMS、电话能力、Binder 回调或双服务共存已通过。
- ActivityThread、IActivityManager、IContentProvider、shell permission delegation 的隐藏 API，以及权限最小集合，必须在 Android 13–16 与 Android 17 预览各自核对。
- 旧版无 UID 的 stopDelegateShellPermissionIdentity 仍存在平台级归属竞态；本实现仅在本次取得委托后调用，不宣称能隔离其他 shell 进程或恶意 root。
- 必须进行当前用户/工作资料、签名或 APK 更新、运行中应用死亡、迟到 watcher、全局委托被其他官方客户端占用、清理失败与模式切换的设备验收。
- 服务按固定会话保存有限请求重放记录；达到 4096 条时拒绝新请求，需要空闲关闭并重新显式启动，不能把记录淘汰后允许旧请求重放。

## 实施裁定

1. 为原有注册状态读取增加 `isImsRegistered` 固定方法，未增加通用 Binder；否则无法在移除业务静态 Shizuku 后保持原查询行为。
2. 单卡/全卡身份元数据由 Runtime 捕获，两后端共用即时校验；参数白名单只额外允许这两个严格类型的元数据键。
3. 无真实设备时交付实现与可复现策略测试、明确保留设备验收，不把编译或单元测试描述成服务共存成功。


## 独立审查修复补充（2026-10-01 16:36 UTC）

### 已修复的具体缺陷

1. 新增 `OperationArguments.preflight(operation, arguments)`：类型、action、键白名单、已有摘要格式与作用域全部验证，但不提前要求 Runtime 尚未捕获的摘要。`validate` 仍在官方/内置/服务外发入口强制完整 SIM 写入身份。两层共享 `IdentityRequirementPolicy`。
2. 新增 `CarrierWriteProgress` 与 `CarrierBatchRunner`。只在实际 overrideConfig 调用抛结构化 SecurityException 且没有此前成功/不明写入时，设置 `BROKER_RETRY_ALLOWED`。写调用返回后的身份/raw/snapshot 读取失败（包括权限撤销）、传输结果不明、第二卡失败或回读不一致均标记 OPERATION_INDETERMINATE，不能整批 Broker 重放。
3. legacy 与 Broker 共用 `LegacyConfigurationOperation`，逐卡核对原目标身份，保持原稀疏键和 false 省略语义；写后最多四秒回读真正 CarrierConfig 值，仅逐卡匹配才标记 verified。部分成功带已验证 subId 和观察快照返回，不能报全局成功。
4. ImsModifier 与 Broker 所有新旧路径统一在 onStart 工作线程执行；onCreate 仅保存显式会话/参数并 start，避免双卡回读阻塞应用主线程。
5. 持久化 VoLTE 区分关键持久值与 IMS 注册诊断。写后 opt-in/用户开关/恢复记录读取失败标记未知；仅诊断失败通过 DIAGNOSTIC_WARNING 单列，不推翻持久写入成功。恢复原值的备份删除延后到最后一次原始值及前后 SIM 身份验证之后；即时回滚后也保留原值记录，避免末次读取失败丢失依据。
6. Captive Portal 的最终输出快照移入同一回滚事务；末次读取失败会恢复原值，回滚失败仍保留不确定性。

### reset 的明确限制

当前 framework 接口没有可关联本次请求的 override 删除完成 token。`overrideConfig(null)` 的正常返回及任意合并配置快照不能证明覆盖已经删除；CarrierConfig change listener 也没有 operationId，初始异步回调和同 subId 的独立事件不能被误当作本次清除的证据。因此本实现实际发出清除请求并读取当前快照后，仍以未确认结果返回，保持自动恢复暂停，不记录“重置成功”或写入 reset-history 阻止标记。原持久化 VoLTE 先恢复、再请求 CarrierConfig 清除的顺序没有改变。

用户收到的专门消息：已尝试清除运营商配置覆盖，但系统没有提供可独立核验的删除完成证据，未记为重置成功。请先核对当前配置，自动恢复保持暂停。

这是首版功能限制，不能在交付时称所有 reset 路径已具备可证明成功。后续若获得设备/特定 framework 的可关联完成证据，可扩展适配器的 VERIFIED 分支，不能用框架默认值冒充运营商默认值。

审查所引用的主要源码：
- https://android.googlesource.com/platform/frameworks/base/+/8699887ebcd53cedeb7102e53ee32bb667026182/telephony/java/android/telephony/CarrierConfigManager.java
- https://android.googlesource.com/platform/packages/services/Telephony/+/12f8a1b303124976bb9eafe30c9f11fdbc5a71d1/src/com/android/phone/CarrierConfigLoader.java

### 本轮验证与范围

在整合者解除暂停并分配唯一 JVM 时段后，使用 256m heap：临时缺陷策略变体运行 31 项产生 7 项预期失败，正式策略运行 31/31 通过。证据：`bridge-review-mutation-red.log`、更新后的 `bridge-test-green.log`。这些变体仅在临时目录运行，未覆盖生产代码。静态 IPC/日志门和 `git diff --check` 均通过。完整 Android 编译及 lint 仍由整合者对本轮最终源码运行。

本轮源码范围（均在 app/src/main/java/app/mystery0/ims/tensor 下）：
- bridge/BridgePolicy.kt、BridgeProtocol.kt
- privilege/OperationArguments.kt
- privileged/CarrierConfigInvocation.kt、LegacyConfigurationOperation.kt（新增）
- privileged/ImsModifier.kt、BrokerInstrumentation.kt、TargetConfigurationOperation.kt
- privileged/PersistentVolteModifier.kt、CaptivePortalSettingsModifier.kt、SessionInstrumentation.kt
- 对应 BridgePolicyTest、tools/test-bridge-policy.sh 和本报告


### BE-2 最终输出快照一致性修复

legacy 普通配置先检查当前值；不匹配继续有界重试，匹配后由 `reader.snapshot(expected)` 再验证最终输出快照本身，只有这份验证过的快照可返回 VERIFIED。第二次读取若变动或失败，按写后未知保留恢复记录。reset 快照仍明确 UNPROVABLE。新增静态契约检查已观察红→绿，证据为 `bridge-final-snapshot-red.log` / `bridge-final-snapshot-green.log`；本次未运行 JVM/Gradle，Android 集成重跑由整合者进行。
