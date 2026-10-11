# 内置服务原生 ADB 薄启动器设计

- 日期：2026-10-11
- 状态：设计与实施计划均已获用户批准，进入 Herdr 执行
- 契约版本：v1
- 范围：以安装目录内的原生可执行启动器替换电脑 ADB 的 Base64 长命令
- 协同：Herdr 管理员协调、一个执行者实施、一个独立审查者审查

## 1. 用户目标与已批准方向

用户希望通过连接电脑的 USB ADB 或无线 ADB 启动 TensorIMS 的内置服务，主要目标是让电脑端命令短，同时避免执行位于可修改外部存储中的脚本。用户已选择参考新版官方 Shizuku 的原生启动器方式，明确不需要另一套完整 Shizuku，要求先写设计约束，再通过 Herdr 执行。

本设计保留现有“用户在应用中生成命令并主动等待”的流程，不增加无人等待的永久启动授权。短命令只含系统安装目录中的可执行文件路径和目标 Android 用户，不含 APK 路径、签名、版本或一次性挑战。

正常形式：

```text
adb shell /data/app/<当前安装目录>/lib/<设备架构目录>/libtensorims_starter.so --user <用户ID>
```

`<...>` 是说明占位符，实际界面显示当前安装信息生成的完整命令。命令路径在升级后可能改变，不能承诺永久固定。电脑连接多台设备时，在 `adb` 后添加 `-s <设备序列号>`。USB 与网络 ADB 共用入口。

与 Base64 方案不同，短命令本身不携带本轮令牌：安装未变化时，同一条路径命令可以用于未来用户主动开启的新等待。过期的是本轮等待、已领取挑战与迟到交付，不是路径字符串。不得宣称“旧短命令在下一轮等待也无法执行”；也不声称能按同一 shell UID 区分用户何时输入了路径命令。

## 2. 当前实现与基线保护

已有未提交实现包括：

- `EmbeddedLauncher.startComputer` 注册一次性挑战，等待约 110 秒；通过 `ComputerAdbCommand` 编码现有 `BootstrapCommand`，展示 Base64 命令。
- `MainViewModel` 持有等待任务与仅内存的命令状态；电脑等待阶段恢复调用方 Job 的取消能力，保留 `OperationCoordinator` 对业务事务的 `NonCancellable` 保护。
- 特权模式页展示生成、复制与取消入口；剪贴板标记敏感。
- `EmbeddedBridgeProvider` 仅允许真实 shell/root UID 交付私有服务 Binder。
- `EmbeddedConnection` 校验挑战、安装身份、模式、能力、服务 UID 与代际；失败、取消或超时拒绝迟到握手。
- 两 flavor 共用第一方 namespace，各自使用当前 applicationId 的 Provider 与安装身份。

实施基线为 Git HEAD `025a948b6b4557329c32015e10e8f1ee6af96b7b` 加上当前工作区全部已有差异。派单时记录完整清单与摘要；不得 reset、清理、遗漏未跟踪文件或把前一轮改动视为无关用户垃圾。审查必须覆盖累计电脑启动链路，而不是只看原生代码增量。

## 3. 方案取舍

| 方案 | 命令长度 | 安全与维护代价 | 决定 |
| --- | --- | --- | --- |
| Base64 内联固定引导命令 | 长 | 不依赖外部脚本，但不满足用户主要目标 | 替换电脑入口 |
| 外部目录临时 `start.sh` | 短 | 新增可修改文件以 shell 权限执行的风险与目录兼容问题 | 不采用 |
| 安装目录内原生薄启动器 | 仅路径与用户参数 | 新增 NDK 构建、打包与有限引导领取协议 | 采用 |

参考证据：官方 Shizuku `v13.6.0` 的 `Starter.kt` 直接生成 `adb shell <nativeLibraryDir>/libshizuku.so`；CMake 使用 `add_executable` 而非普通共享库；`starter.cpp` 使用 `fork / setsid / exec app_process`。本项目参考机制，不引入官方服务、授权数据库或全套启动器逻辑。优先独立实现最小代码；如复制上游代码，必须保留并补齐对应许可说明。

## 4. 组件与调用链

```text
用户点击生成电脑启动命令
  -> MainViewModel
  -> PrivilegeRuntime.startEmbedded / OperationCoordinator
  -> EmbeddedLauncher 准备仅内存的一次性电脑启动等待
  -> UI 展示系统安装目录内的启动器命令

电脑执行命令（USB 或网络 ADB）
  -> 原生 libtensorims_starter.so（shell/root）
  -> 当前安装 APK 中的固定 ComputerBootstrapMain
  -> 受控 Provider 领取本次电脑启动挑战与必要元数据
  -> 复用现有 EmbeddedServerMain 的服务构建/交付逻辑
  -> 原有握手确认
  -> PrivilegeRuntime 刷新或恢复核对
  -> UI 就绪
```

新原生文件虽名为 `.so`，实际上是具有入口点的 Android PIE 可执行程序，以该命名让 Android 包管理器安装到受保护的 native library 目录。它不由 `System.loadLibrary` 加载，不包含业务服务或通用提权逻辑。

### 4.1 原生启动器职责

原生代码只负责：

1. 校验 `getuid()` 为 0 或 2000。
2. 严格解析唯一外部参数 `--user <非负十进制整数>`；拒绝未知、重复、缺失、负值、溢出与附加参数。用户 ID 必须能够参与 Android UID 的安全计算。
3. 从真实自身可执行路径定位所属安装目录；拒绝从 `/sdcard`、`/data/local/tmp` 或其他可写副本运行。路径必须指向标准 `/data/app` 安装布局中的固定启动器文件，同目录 APK 必须存在。不要执行从用户参数提供的路径。
4. 清理影响启动的环境输入，设置固定 APK 的 `CLASSPATH`，用明确 argv 执行系统 `/system/bin/app_process` 和固定 Java/Kotlin 引导类；不拼接 `sh -c`，不接受任意类名或命令。至少不继承任意 `LD_PRELOAD`、`LD_LIBRARY_PATH`、`CLASSPATH` 与 Java 注入选项。
5. 有界地派生进程、脱离电脑终端并处理 HUP、标准输入输出和父进程退出；仅操作自身派生的进程。参数、路径、派生及 exec 等原生层可检测失败必须非零退出并输出英文的稳定阶段错误，不能打印挑战或其他敏感材料。父子间应通过有界的 exec 成败通知避免把 exec 失败误报为成功。

禁止按进程名杀服务、扫描其他提权服务、复制到 `/data/local/tmp`、更改系统目录权限或加载官方 Shizuku 配置。独立服务的退出仍由现有认证 Binder、客户端死亡、租约及受控关闭管理。

原生层的安装路径核对不替代 Android 包管理器身份核对；它负责避免短命令引入任意文件执行入口。安装目录保护不保证抵御 root、系统攻破或用户安装了恶意签名包。

脱离终端后的 Kotlin 领取或握手失败可能晚于原生父进程退出；电脑端零退出码只能说明引导程序已提交，不能等于服务就绪。该阶段失败由有界退出、英文阶段日志和应用未就绪状态体现。以应用内已认证 Ready 为最终成功依据，不为转发完整后台日志新增复杂 IPC 机制。

### 4.2 APK 内固定引导入口

新增固定 `ComputerBootstrapMain`，运行在 shell/root 的 `app_process` 中：

- 利用现有隐藏 API helper 思路创建目标用户系统 Context，读取本 flavor 当前安装记录。
- 核对 classpath、APK 路径、nativeLibraryDir / 启动器真实路径、当前应用 UID、版本和签名，与正在执行的安装一致。
- applicationId 来自当前 APK 的 `BuildConfig.APPLICATION_ID`，不能由电脑参数指定，也不能在两个 flavor 之间猜测或回退。
- 只向当前安装包、目标用户的固定 Provider 请求电脑启动领取，不用命令行 `content call` 输出挑战。
- 获取挑战后沿用现有服务构建与 Binder 交付。可提取小型公共 bootstrap helper，避免重复 ActivityThread / Provider 兼容逻辑；不能大范围重构后端。
- Looper 只准备一次。现有无线与 root 六参数入口仍可使用；新电脑入口不得改变它们的鉴权或生命周期语义。
- 新固定入口必须加入 R8 keep 规则；只保留必要类和成员，不能关闭混淆或用全包保留掩盖入口问题。

Native 先加载 APK 再进行 Kotlin 核对的可接受前提是该 APK 来自自身受系统管理的安装目录；不能从外部存储或用户传入路径加载任意 APK。发现布局不受支持时报告失败，不退回任意路径。

## 5. 一次性电脑启动领取协议

增加固定 Provider method，例如 `claimComputerBootstrap`。它不是通用命令接口，不返回脚本或任意操作能力，不自动创建授权。

### 5.1 等待记录

`EmbeddedLauncher` 在原有启动门禁内创建仅内存等待记录：

- 当前 challenge 与独立 attempt/generation；
- 当前安装身份摘要、目标用户和运行时 epoch；
- 单调时钟 deadline，约 110 秒；
- 仅电脑路径允许领取的标记；
- 是否已被领取及领取者的真实 shell/root UID。

无线与 root 启动不得被该方法领取，即使它们此时也有待交付挑战。进程初始化、Provider 请求或保存的 EMBEDDED 模式本身不能创建电脑等待记录。电脑等待、领取和最终交付共享同一个有效期，不因领取刷新或延长 deadline。

### 5.2 领取条件与状态转换

```text
NONE -> PREPARED -> CLAIMED -> ACCEPTED
           |           |
           +-----------+-> REVOKED（取消、超时、进程结束、身份/epoch变化、失败）
```

领取必须逐项满足：

1. 调用者的真实 Binder UID 为 shell 2000 或 root 0，不信任 extras 中的 UID。
2. 当前模式仍为 EMBEDDED，用户已通过门禁主动进入电脑等待。
3. 当前 epoch、用户、安装记录与准备时一致。
4. 存在未领取、未过期的电脑等待，没有已认证服务或未完成业务阻塞。
5. 请求元数据与 Provider 自己从包管理器读取的当前安装信息匹配。

检查与 `PREPARED -> CLAIMED` 必须在同一受控同步状态中完成，防止两个电脑命令领取同一轮。领取只发放一次，领取后崩溃或失败需要用户重新生成；不自动重新发放挑战。原 `BootstrapLifecycle.consume/accept` 的最终交付一次性校验仍保留，领取不能等于服务已认证。

最终交付还需核对 challenge、generation、deadline、电脑领取者 UID、epoch、安装身份、服务能力与存活 Binder。取消和握手竞争必须按同一代际裁定；旧领取、旧死亡通知和旧完成回调不能清除或接受新等待。

Provider 在领取时不得尝试重新获取 `OperationCoordinator` 的锁：等待任务已持有该门禁，再入会死锁。门禁保护准备/取消与模式切换，Provider 通过短时同步等待状态及运行时快照验证，只返回有限元数据。Binder 回调中不得执行业务写入。

shell/root 是 Android 授权后的信任主体；协议限制显式启动时机和安装一致性，不声称能够区分两个同 UID 的 ADB 客户端或阻止已取得 root 的攻击者。

### 5.3 撤销与异常

- 成功、取消、超时、主进程结束后清除电脑领取授权；没有挑战文件或备份需要删除。
- 取消保留上一轮已验证的可取消等待实现，不能把原生启动等待塞回无法取消的 `NonCancellable` 区域。
- 模式或 epoch 变化必须拒绝旧领取与交付；不静默切换官方/内置模式。
- 当前模式 UNSET / OFFICIAL、无等待、重复领取、错误用户、安装变更、失败后重放均明确拒绝。
- 尚未握手的失败子进程不得无限驻留；所有失败在有界时间内退出。
- 领取失败或新启动器不可用时，不自动退回 Base64 长命令或外部脚本，以免隐藏核心目标失败。保留手机无线与 root 入口，提示用户检查安装或重新生成。

## 6. 打包、架构与构建约束

### 6.1 正式产物

- 正式 `tensorRelease` / `legacyRelease` 继续仅 `arm64-v8a`。
- 两个 flavor 都包含薄启动器，业务 namespace 保持统一，不复制后端。
- 原生代码通过本项目的最小 CMake/NDK 接线构建，不引入大型第三方框架。
- NDK 与 CMake 版本集中维护在 `gradle/libs.versions.toml`，选择本机已安装并经实际构建验证的版本。当前可见 NDK 为 `29.0.14206865`、`30.0.16248370`，CMake 为 `3.22.1`、`4.1.2`；实施计划确定具体组合，不自动下载或升级工具链。
- 使用 Android 原生库的提取打包方式，使启动器存在于 `ApplicationInfo.nativeLibraryDir` 且可直接执行；核对 APK、安装后文件权限与 ELF 属性，不能只假定 `.so` 名字会被正确安装。
- 既有 `libadb.so` 和 `PairingContext` JNI ABI、许可不变；打包方式变化必须检查既有无线配对库仍在包内且可加载。不能借改打包删减官方/内置双模式。

### 6.2 当前 x86_64 模拟器

当前 `emulator-5554` 为 API 37、x86_64，支持 ART 层 ARM64 转译，但独立 ARM64 ELF 启动器不能据此认定可执行。

为验证增加一个用户已批准的显式、默认关闭本地 Gradle 开关，例如 `-PtensorimsNativeStarterEmulator=true`：

- 只对 debug 产物添加 `x86_64` 启动器并保留 ARM64；普通 debug 默认仍遵守原 ABI 约束。
- release 检测到该开关必须拒绝构建，防止误发布模拟器配置。
- 若既有无线配对库只有 ARM64，模拟器包不宣称支持 x86_64 手机内配对；架构验证限定为电脑启动器及现有不依赖该 JNI 的私有服务链路。
- UI 从安装信息取 nativeLibraryDir，不能自行拼装 `arm64` 或 `x86_64` 路径。

执行者必须先验证 native PIE 的构建、打包、安装后执行以及引导领取这一关键链路，再扩展文案与清理。若 AGP/PackageManager 混合 ABI、独立 ELF 或 Provider 兼容成为阻塞，报告证据并调整获批实现策略；不得增加通用测试平台、改变正式 ABI 或把 ARM64 转译当作独立原生程序成功证据。

## 7. UI 与用户行为

复用现有电脑 ADB 卡片与对话框：

- 点击后注册新的内存等待，展示短命令；不能只显示路径而没有正在等待的授权。
- 复制保留敏感剪贴板标记，虽然短命令不再直接包含挑战。
- 保留连接授权、多设备 `-s`、保持应用运行、取消、超时与重新生成说明。
- 说明命令路径可能因升级改变，且每次启动必须先在应用开启等待；不承诺永久启动授权。
- 已就绪、锁定、恢复门禁不允许启动时沿用已有 UI 门禁。
- 失败不能显示就绪；只有原握手与运行时核对成功才显示 Ready。

正常电脑命令不再出现 Base64、大段签名/挑战或任意 shell 管道。只允许启动器路径、用户选择以及 ADB transport 选择。

## 8. 不在本次范围

- 无应用等待的后台或永久启动授权。
- 完整官方 Shizuku 服务、权限数据库、任意 shell 命令 API 或跨包借用。
- 外部 `start.sh`、`/data/local/tmp` 启动器缓存或持久化挑战。
- 修改 SIM、CarrierConfig、持久化 VoLTE、Captive Portal 或自动恢复业务。
- 修复既有 Windows 目录 fsync 单测问题。
- 变更发布签名、自动提交、推送、PR、发布或部署。

## 9. 验收与验证分工

### 用户可见验收

1. 两包电脑入口均显示短的原生启动器命令，无 Base64 载荷。
2. 从真实 UI 创建等待，经电脑 ADB 执行后显示 Ready、shell UID 2000、授权已验证。
3. 无等待、错误用户/包、重复领取、取消、超时、进程结束、身份变更或旧轮次交付不能启动或接受服务。
4. 两包各自启动器和 Provider 绑定自己的安装身份，不能跨包领取；独立进程不控制官方 Shizuku。
5. Windows CMD、PowerShell 和模拟器 TCP ADB 传输验证成功；不扩大成通用网络验证基础设施。
6. 正式 release ABI 与既有配对库打包约束不退化；默认双 flavor debug 编译通过。

### 执行者

- 针对性纯逻辑验证参数解析、领取/撤销/代际边界；项目不强制新增单元测试，但新增安全策略适合纯逻辑时优先测试。
- 构建默认 ARM64 双 flavor debug 和显式模拟器开关双 flavor debug；核对安装后的 native 文件与 UID。
- 有界验证关键引导链路，记录失败退出、Provider 错误和已知限制；不打印挑战。
- 运行相关 lint，并检查 R8 固定入口 keep 规则与 release ABI 配置；无正式签名配置时不能用 debug 签名绕过 release 要求。

### 独立审查者

- 基于冻结的累计差异，独立检查鉴权、授权创建、领取一次性、epoch/代际、取消竞态、双包与多用户边界。
- 独立验证关键拒绝反例、安装目录执行限制、未知参数拒绝及 release 架构约束；避免重复全仓构建。
- 确认没有复制官方按进程名杀服务、清理公共目录或扩大文件权限等行为。

### 管理员

- 接受执行者产物后冻结快照，启动独立审查；核实 findings 并通过原执行者返修。
- 必要集成验证：最终两个模拟器包的真实 UI 生成与启动、CMD/PowerShell、TCP ADB、取消/超时与双包隔离。
- Critical / Important 清零且必要验证闭环后才接受；未验证项不能藏在“编译通过”中。

### 已知基线限制

前一轮两个 flavor 各有 207 个全量单测，均有 2 个未改动的 `OperationJournalTest` 用例在 Windows `FileChannel.open` 打开目录时出现 `AccessDeniedException`。不得把全量单测称为通过，也不得为通过宿主机测试放宽 Android 持久化安全行为。记录用例：`unfinishedWriteSurvivesProcessReconstructionWithoutReplayData`、`terminalIsDurableUntilReadbackAllowsExplicitClear`。

当前仅有模拟器；真实 Pixel 的物理 USB 授权、无线调试 TLS 配对、Wi-Fi 链路和实际 ARM64 原生运行不能由 x86_64 模拟器替代。若无真实 ARM64 设备，本批次以 ARM64 构建/ELF 检查和 x86_64 设备运行分别报告，不声称已做真实 Pixel 全量验收。

## 10. 实施顺序与协同门槛

1. 用户审阅并批准本设计与模拟器 debug 开关边界。
2. 管理员生成 `docs/plans/2026-10-11-native-adb-starter-plan.md`，确认执行计划与 Herdr 模型/档位/权限授权。
3. 创建一个 Herdr 执行者，先做原生打包、受控领取与模拟器执行的关键路径检查点；保留已有差异。
4. 关键路径成立后完成电脑入口替换与必要回归，提交结果文件，不提交 Git。
5. 管理员冻结累计实现快照后启动独立审查者；返修仍复用同一执行者与审查者。
6. 管理员完成剩余集成验证与 finding 验收，更新 `AGENTS.md` 和电脑启动验证记录。

角色任务和结果使用 `HERDR-COLLAB/2`；先登记 `herdr_watch` 再非阻塞派单。批次运行产物置于批准的本机临时目录，正式 `docs/plans/` 只存用户可审阅的设计与计划。审批前不启动付费角色实施；角色选型授权不代替设计/计划批准。
