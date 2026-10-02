# Task 5：双模式界面实施报告

## 结果与范围

已在既有 Compose 导航中加入显式首次模式选择与固定「特权模式」设置入口。界面、主 ViewModel 与兼容状态模型不再调用或监听官方 Shizuku 单例；现有业务页通过来自所选后端的兼容状态继续工作。未修改业务历史、自动恢复持久化、VoLTE 原值备份或私有服务认证实现。

### 首次选择与迁移

- 首次未配置时展示同等样式的「官方 Shizuku」「内置 Shizuku」按钮，不预选；取消、外部点击或返回保持 UNSET
- 明确说明选择不等于授权或启动；取消标记通过 `rememberSaveable` 跨旋转保持，重新进入设置始终可以选择
- 选择成功或失败均进入模式设置以展示当前真实模式与结果，不因官方拒权而选择内置
- 新身份迁移警告在首次选择和首页/设置展示；提醒关闭旧版自动恢复、先通过旧版恢复各 SIM 持久化 VoLTE 原值、保留旧版及原值备份，不承诺私有数据自动迁移，禁止两版同时启用自动恢复
- 迁移提示只在用户点击「我已了解」后保存已读，不删除或访问旧应用数据

### 状态与模式切换

- 设置固定显示选择的模式、实际连接状态、shell/root UID、授权结果与服务版本
- 首页状态与自动恢复、SIM 缺失、持久化 VoLTE、IMS 配置门禁文案改为后端中立
- 模式切换需单独确认，说明本应用历史、草稿、原值备份保留，以及离开内置模式时仅停止空闲且已验证归本应用所有的服务，不停止官方服务
- CONNECTING、BUSY、SWITCHING、RECOVERY_REQUIRED 或本地动作未结束时禁止模式切换；点击入口及提交瞬间都重新判断，不排队保存一次未来切换
- `ShizukuStatus` 仅为旧业务 UI 兼容类型。已认证 BUSY 保留页面快照与草稿，实际 `BackendStatus.isReady` 仍仅在 READY；所有新模式动作另外检查实时门禁
- MainViewModel 保留最近 SIM 列表以免短暂断连/切换清空编辑上下文；读取响应验证 epoch，旧模式响应不覆盖新列表

### 内置连接与恢复

- 无线调试说明区分配对端口、六位配对码和连接端口；端口与配对码不静默截短，无效输入不能提交
- 开发者选项仅由用户明确点击打开；UI 不写全局 ADB 设置
- 配对码仅在普通 Compose 内存和进行中的启动协程内短暂使用，提交即清空；不使用 `rememberSaveable`、日志或首选项保存配对码。端口、对话框状态可跨旋转恢复
- root、无线连接启动和配对全部由 Activity 级 ViewModel 执行，经 `PrivilegeRuntime.startEmbedded` 统一门禁；重复点击、返回或旋转不重新提交
- 显示配对、root、无线启动进度和安全失败消息；页面离开后仍显示动作继续的说明
- 恢复态默认禁止新启动和普通写入。只在运行时明确授予 `canStartEmbeddedForRecovery` 或 `canRequestOfficialPermissionForRecovery` 能力后开放手动重连，以免重启后无法完成核对
- 仅在 `canRecoverPersistentVolte` 为真时展示专门原值恢复按钮，需再次确认「恢复上次保存的原值，不重放开启操作」，调用 `recoverPersistentVolte()`，不绕到普通写入

## 文件边界

- 新增 `ui/BackendUiLogic.kt`、`ui/BackendActionState.kt`
- 新增 `ui/components/BackendStatusUi.kt`、`ui/screens/BackendSettingsScreen.kt`
- 修改 `ui/MainActivity.kt`、导航路由、首页、设备状态卡、自动恢复卡、持久化 VoLTE 卡及 IMS 门禁提示
- 修改 `viewmodel/MainViewModel.kt`、兼容模型 `model/Shizuku.kt`
- 新增英文与简体中文 `backend_strings.xml`
- 新增 `ui/BackendUiLogicTest.kt`

公共接口按实施计划使用；额外恢复能力接口已与后端工作单元及整合者明确协调。启动器自带的错误消息由启动器工作单元负责独立本地化。

## 验证证据

使用缓存 Kotlin 2.4.20 编译器及 JUnit 4.13.2，直接编译真实 `BackendModels.kt`、`Shizuku.kt`、`BackendUiLogic.kt` 与测试，无 Android 替身模型；输出隔离在 `/tmp/tensorims-ui-test-classes`。

- `ui-tests-red.log`：先添加测试，确认缺失 `backendUiActions` 等实现导致失败
- `ui-recovery-red.log`：先添加原值恢复确认策略测试，确认缺失能力判断导致失败
- `ui-snapshot-red.log`：先添加已认证 BUSY 与未知启动身份区别测试，确认兼容映射未实现导致失败
- `ui-recovery-reconnect-red.log`：先添加恢复期安全重连例外测试，确认缺失 capability 参数导致失败
- `ui-tests-green.log`：12 项 JUnit 测试通过，覆盖未选择、拒权、重复点击、事务忙碌、恢复、取消/过期确认、端口/配对码、BUSY 快照及恢复重连例外
- 静态资源检查：80 个后端字符串实际引用在中英资源均存在，两种语言键集合一致，无同名字符串冲突
- `git diff --check` 通过
- 静态搜索：UI 与 MainViewModel 中无 `rikka.shizuku` 导入、Binder 监听、`pingBinder`、`checkSelfPermission` 或直接权限请求

整合构建及完整测试由整合者统一运行，尚不在本报告中宣称 Compose 编译、lint 或 APK 构建通过。未连接模拟器/真机，不声称触屏、旋转恢复、root、无线配对或 IMS 功能通过真机验收。

## 待整体验收

1. 运行 `:app:testDebugUnitTest`、`:app:assembleDebug`、lint，检查编译及资源生成
2. 真实设备验证首次取消/返回、旋转、进程重建、快速重复点击、不自动启动/授权
3. 分别在官方拒权、内置无线配对失败/root 拒绝、运行中切换、恢复态重启重连和原值恢复确认场景检查按钮与信息
4. 验证中英文、大字体、小屏幕和屏幕阅读器下两个初始选择仍同等可达
5. 验证切换前后的 IMS 编辑草稿、历史与 noBackup 原值备份保持

## 实施判断

- 旧 `ShizukuStatus` 类型保留为兼容层，避免无关的大范围业务页重写；首页和设置直接显示原始后端状态。代价是名字仍含旧后端名称，需要后续维护者理解其兼容用途
- 配对码不跨旋转保存，以避免敏感短期凭据进入持久化状态；用户旋转未提交的配对页后需要重新输入代码
- 无独立设备/UI 自动化环境，交付纯逻辑测试与静态证据，真实 Compose 交互行为留给验收矩阵，不据此声称设备安全验证完成

## 独立审查后修复（2026-10-01 16:24 UTC）

针对独立审查 `tensorims-ui-build-review.md` 的 UI-1、UI-2，两项实际清稿缺陷已修改源码，尚待运行测试及整合编译。接到修复任务时整合者设置统一 JVM/Gradle 内存占用暂停；本轮严格没有启动 JVM、编译器或 Gradle，不把新增测试描述为已通过。

### UI-1：Captive Portal 自动重读覆盖脏草稿

- 将系统快照、输入草稿及输入版本整理为纯 Kotlin `ui/CaptivePortalDraftState.kt`
- 自动重新进入页面或重新读取默认只更新系统快照，脏草稿保持；无脏草稿时才以新系统值初始化输入
- 系统网络列表和 Captive Portal 页的主动刷新按钮，在存在未保存输入时弹出明确丢弃确认；取消不发出丢弃请求
- 用户确认丢弃仅针对请求时的草稿版本，读取过程中任何新输入都会增加版本，迟到结果只更新快照而不覆盖新输入
- 请求记录所选后端 epoch；切换后旧代际结果不更新系统快照和草稿；旧读取结束且新后端已就绪时可重新读取
- 保存后的核对也经过相同版本合并，避免保存期间的新输入被旧写入的回读覆盖
- 读取和保存忙碌标记在启动协程前同步设置，避免重复点击产生覆盖顺序不定的请求

### UI-2：失败读卡伪装空列表并自动改选

- MainViewModel 改用 core 新增 `ShizukuProvider.readSimInfoResult`，保留旧 facade 给其他调用者使用
- 新纯函数 `completeSimListRead` 区分成功、失败与旧 epoch 结果
- 失败保留最近一次成功的显示列表及当前选择来源，错误单独发布；首页显示可读错误和「当前为最近成功快照，已保留选择及未应用配置」说明
- 成功返回零张真实 SIM 时才发布真正空列表，不插入伪造的「所有 SIM」选项
- 旧模式的成功或失败响应均不覆盖当前列表/错误；新的成功读取清除旧错误

### 新增回归（待运行）

`ui/DraftRetentionTest.kt` 共 12 项：首次初始化、自动进入/模式切换保稿、显式确认丢弃、自动读取中的晚编辑、确认丢弃后的晚编辑、旧 epoch Captive Portal 回包、失败保留系统快照、系统默认模式下未保存 URL、READY 下失败读卡保卡、成功零卡、旧 epoch 读卡成功/失败、重读成功清错。

本轮已执行静态 XML 无重复检查、调用者/导入检查及 `git diff --check`，均通过。可在解除内存暂停后运行 `/tmp/tensorims-ui-regression-tests.sh`，以隔离的 256 MiB JVM 编译真实纯 Kotlin 源文件并执行原有 12 项 UI 决策和新增 12 项草稿测试；该脚本目前只准备，尚未执行。正式整体验证仍应包括 `:app:testDebugUnitTest`、debug 构建与 lint。

### 修复验证补充（2026-10-01 16:27 UTC）

整合者解除暂停并仅允许本工作单元运行隔离纯 JVM 回归后，已完成验证：

- RED：在 `/tmp/tensorims-ui-before` 隔离副本中，用新纯函数接口适配修复前真实 ViewModel 行为：成功读取无条件 `stateFromStoredSettings` 覆盖输入、不校验草稿版本/epoch；SIM 列表 facade 丢弃失败错误后无条件插入「所有 SIM」。未替换、回滚或暂时破坏共享生产源码
- `ui-draft-regression-red.log`：24 项测试运行，7 项预期失败，明确复现脏草稿覆盖、迟到回包覆盖、旧 epoch 更新、失败读卡丢卡以及伪造所有 SIM
- GREEN：编译并运行实际修复后生产 helper 和测试，`ui-draft-regression-green.log` 显示 **24/24 通过**（原有 UI 决策 12 项 + 新增草稿/SIM 保留 12 项）
- 两次运行均使用隔离输出目录及 `-Xmx256m`；没有运行 Gradle、连接设备或提交
- 修复后再次执行 `git diff --check` 和中英资源引用检查，均通过

上述结果替代前节「待运行」状态。完整 Android/Compose 编译、整合测试和设备验证仍由整合者执行，本报告不推导已有 APK 或真实设备通过。
