# 内置服务原生 ADB 薄启动器实施计划

> **面向 Herdr 执行者：** 按自身 `herdr-executor` Skill 与 `HERDR-COLLAB/2` 执行以下任务；不递归启动子角色。项目不强制新增单元测试，新增安全策略适合纯逻辑时使用针对性测试；运行产物放批次临时目录。复选框仅为任务跟踪，不能代替验证证据。

**状态：** 设计与本文均已获用户批准；按本批次 Herdr 契约实施。

**目标：** 两个 flavor 的电脑 ADB 入口改为安装目录内的原生可执行启动器短命令，保留显式等待、一次性挑战和现有服务安全边界。

**架构：** 最小 C++ 启动器通过明确 argv 启动当前 APK 中的固定 `ComputerBootstrapMain`。该入口从当前安装包 Provider 领取正在等待的一次性挑战，复用原私有服务构建与交付。客户端继续通过统一门禁管理等待与取消。

**技术栈：** Kotlin / Android 13+ / C++17 / CMake 3.22.1 / NDK 29.0.14206865 / AGP 9.4.1。

**设计：** [2026-10-11-native-adb-starter-design.md](2026-10-11-native-adb-starter-design.md)

## 全局约束

- 正式 `tensorRelease`、`legacyRelease` 仅 `arm64-v8a`；普通 debug 默认同样保持 ARM64。
- 默认关闭 `tensorimsNativeStarterEmulator` Gradle 开关；开启后仅 debug 允许 ARM64 与 x86_64，release 变体不可构建，不能误发布验证产物。
- 两包仍包含官方与内置完整双模式；当前 applicationId 为身份源，第一方 namespace 不变。
- 命令不包含挑战或 Base64，不写外部脚本、不复制启动器到 `/data/local/tmp`、不扩大目录权限、不按进程名杀服务。
- 电脑入口只有 `--user <非负十进制整数>` 一个外部参数；不接受任意 APK、包名、类名、命令或环境注入。
- 等待约 110 秒，领取与交付共享单调时钟 deadline。短路径命令可在未来的新等待复用，但旧领取挑战和旧交付必须被拒绝。
- 领取不创建授权，不可领取无线/root 入口挑战；没有用户主动电脑等待就拒绝。
- Provider 不重新获取等待任务持有的协调器锁；同步有限等待状态，保留取消能力与事务保护。
- 保留全部已有工作区改动；不提交、推送、发布或删除工作树。
- 代码注释、文档和提交信息使用中文；日志与原生错误输出使用英文。
- 不为修复既有 Windows `OperationJournalTest` 目录 fsync 失败改变持久化逻辑。
- 实施中的必要小型接线可在批准范围内调整；更改鉴权、正式 ABI、依赖、核心验收或扩建验证基础设施须先报告裁定。

## 审查重点

1. 无等待或手机无线启动时执行短命令：必须拒绝领取，不能隐式建立电脑授权。（任务 2、4）
2. 已领取后取消、超时或开始新轮次：旧交付必须拒绝，旧清理不能撤销新等待。（任务 2、3、4）
3. 两个同时执行的 shell 命令：同一轮只能一个领取成功；领取失败不能重发相同挑战。（任务 2、4）
4. APK 升级、错误用户或另一个 flavor：执行文件、classpath、包管理器记录、Provider 和当前等待必须一致。（任务 1、2、4）
5. x86_64 模拟器开关与旧 ARM64 配对 JNI：验证包可运行电脑入口，但不得误宣称手机配对兼容或进入正式发布。（任务 1、4）

## 文件结构与职责

### 新建

- `app/src/main/cpp/CMakeLists.txt`：只构建独立 PIE 启动器。
- `app/src/main/cpp/native_starter.cpp`：UID、参数、安装路径、环境、派生与固定 app_process argv。
- `app/src/main/java/app/mystery0/ims/tensor/embedded/ComputerBootstrapMain.kt`：shell/root 固定电脑引导入口，核对安装、领取挑战、进入原服务逻辑。
- `app/src/main/java/app/mystery0/ims/tensor/embedded/BootstrapPlatform.kt`：集中复用创建目标用户 Context 与定向 Provider call 的 Android 13+ 隐藏 API 兼容处理。
- `app/src/main/java/app/mystery0/ims/tensor/bridge/ComputerBootstrapLease.kt`：电脑等待、领取及撤销的纯 Kotlin 策略。
- `app/src/test/java/app/mystery0/ims/tensor/bridge/ComputerBootstrapLeaseTest.kt`：安全状态反例；若运行限制导致不用 JVM 测试，必须给真实替代证据。

### 修改

- `gradle/libs.versions.toml`、`app/build.gradle.kts`：集中工具链版本、externalNativeBuild、可执行库提取、显式模拟器 debug 开关。
- `app/proguard-rules.pro`：固定新 main 入口保留。
- `bridge/BridgeProtocol.kt`：有限领取 method / 字段常量；不扩展业务操作白名单。
- `embedded/EmbeddedBridgeProvider.kt`、`embedded/EmbeddedConnection.kt`：真实 UID、安装、epoch、领取与最终交付校验。
- `embedded/EmbeddedServerMain.kt`：提取小型共用平台 helper 与已核验服务入口，不改变无线/root 六参数入口。
- `embedded/EmbeddedLauncher.kt`：生成短命令、准备电脑等待并按本轮撤销。
- `embedded/adb/ComputerAdbCommand.kt` 与对应测试：替换 Base64 编码为路径与用户参数校验。
- `viewmodel/MainViewModel.kt`、现有电脑启动 UI / 中英文资源：仅必要文案与状态接线，保留取消任务与剪贴板标记。
- `AGENTS.md`、`docs/implementation/COMPUTER_ADB_VALIDATION.md`：记录最终架构与真实验证。

除上述文件外，允许实现目标所必需的同目录小型 helper、针对性测试和构建接线；新增范围由管理员记录，不能顺带修改 IMS 业务。

## 任务 1：原生启动器构建、打包与安全入口

**接口：**

- 外部入口：`libtensorims_starter.so --user <userId>`。
- 固定 app_process 入口：`app.mystery0.ims.tensor.embedded.ComputerBootstrapMain`，内部参数为用户 ID、真实启动器路径、所属 APK 路径；这些不是外部可注入参数。
- 命令构造：`ComputerAdbCommand.create(starterPath: String, userId: Int): String`，只返回 `adb shell <安装目录固定文件> --user <userId>`。

- [ ] 在版本目录增加 `android-ndk = "29.0.14206865"` 与 `android-cmake = "3.22.1"`，配置 C++17 和静态 C++ 运行库，避免新增独立共享运行库依赖。
- [ ] 新 CMake 用 `add_executable` 构建 PIE，输出文件名 `libtensorims_starter.so` 并接入 AGP native 输出目录；开启 native 提取打包方式。不得把普通共享库冒充可执行启动器。
- [ ] 原生入口先检查 shell/root UID，严格解析唯一 `--user`，范围限定为 Android UID 安全计算可支持的用户 ID；拒绝未知/重复参数、负数、非十进制、缺失与溢出。
- [ ] 从 `/proc/self/exe` 真实路径定位当前安装的 `base.apk`。检查固定文件名、标准 `/data/app` native 安装布局与可读 APK；拒绝外部副本。处理路径长度、realpath/readlink 失败及 inode/文件类型核验，不执行用户提供路径。
- [ ] 构造明确 argv，使用固定 `/system/bin/app_process`、当前安装 APK classpath、固定 `ComputerBootstrapMain` 与三个内部参数。清理可影响子进程的注入环境；不启动 shell 解释器。
- [ ] 派生后忽略 HUP、setsid、定向标准输入输出；通过 CLOEXEC 管道向父进程有界通知 exec 成败。原生层能检测的失败非零退出；Kotlin 后台失败不能伪装成服务 Ready。
- [ ] 增加默认关闭的 `-PtensorimsNativeStarterEmulator=true`：只允许 debug 添加 x86_64。禁止该配置产生 release 变体；显式 release 请求必须失败而不是悄悄生成 x86_64 正式包。
- [ ] 先运行默认双 flavor debug 构建，检查 APK 每包包含 ARM64 启动器与原 `libadb.so`，无 x86_64；用 NDK `llvm-readelf` 核对入口点、机器架构与加载段属性，兼容当前 16 KB page size 模拟器要求。
- [ ] 构建模拟器开关双 flavor debug 包并检查两种启动器存在；装入仅有的测试模拟器，核对 `nativeLibraryDir` 文件类型/权限与实际 x86_64 ELF。不可覆盖签名不同的真实设备包。

**命令：**

```powershell
.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain
.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug -PtensorimsNativeStarterEmulator=true --stacktrace --console=plain
```

**检查点：** CMake 构建、APK 打包与安装目录可执行成立。此时新固定 Kotlin 入口若尚未接线，只能记录入口缺失，不称为服务启动成功。不要提前删除已验证的电脑启动 UI；任务 2 完成才验证完整链路。

## 任务 2：受控一次性领取与原服务接线

**纯策略接口：**

```kotlin
class ComputerBootstrapLease {
    fun prepare(challenge: String, generation: Long, epoch: Long,
                identityDigest: String, deadline: Long)
    fun claim(now: Long, epoch: Long, identityDigest: String,
              callerUid: Int): String?
    fun permitsDelivery(challenge: String, generation: Long, now: Long,
                        epoch: Long, identityDigest: String, callerUid: Int): Boolean
    fun revoke(generation: Long)
}
```

上述策略由 `EmbeddedConnection` 的同一锁保护；策略本身不调用 Android、Provider 或操作协调器。`identityDigest` 是包管理器身份的稳定摘要，包含 applicationId、目标用户/UID、版本、签名、APK 与 nativeLibraryDir，不来自外部请求自述。`claim` 不修改 deadline，最多成功一次。按 generation 撤销，旧撤销不能清除新记录。

**平台接口：**

```kotlin
internal object BootstrapPlatform {
    fun systemContextForUser(userId: Int): Context
    fun callProvider(method: String, extras: Bundle, userId: Int): Bundle
}
```

定向 authority 来自 `BridgeProtocol.AUTHORITY`，不是函数外部参数。获取 Provider 后在 finally 释放引用；复用当前 Android 13+ 反射签名，不新增按系统版本猜测的硬编码。

**服务复用接口：**

- `EmbeddedServerMain.main(args: Array<String>)` 保留现有六参数语义。
- 提取 `EmbeddedServerMain.runAuthenticatedBootstrap(context: Context, identity: InstalledIdentity, challenge: String)`，由已完成各自入口校验的六参数入口和新电脑入口调用；负责构建、交付与服务 loop。
- `ComputerBootstrapMain.main(args: Array<String>)` 严格核对三项内部参数、真实 UID、classpath、nativeLibraryDir、APK/版本/签名与目标用户，再领取挑战并进入公共服务入口。

- [ ] 添加纯策略反例：无 prepare 时 claim 为 null；同一轮首次 claim 成功、第二次为 null；非 shell/root 拒绝；错误 epoch/身份拒绝；deadline 边界到期拒绝；claim 不延长有效期。
- [ ] 添加撤销与交付反例：领取后取消不可交付；新轮次开始后旧 challenge/generation 不可交付；旧 `revoke` 不删除新授权；领取 UID 与交付 UID 不同被拒绝。
- [ ] 实现有限状态策略并将电脑 lease 与 `BootstrapLifecycle` generation 在 `EmbeddedConnection` 同锁下绑定。现有无线/root `expect` 不准备电脑 lease。
- [ ] 添加 `BridgeProtocol.COMPUTER_CLAIM_METHOD` 与必要 Bundle 字段，Provider 依据 `Binder.getCallingUid()` 校验并在同一轮 claim；允许字段必须明确，不返回任意命令或操作。
- [ ] `EmbeddedConnection` 在电脑准备时捕获当前安装、用户、epoch 和约 110 秒 deadline。claim 与最终 accept 都检查当前运行时和安装记录；不再入协调器锁，不因 Provider 唤醒应用而授权。
- [ ] 提取平台 helper，确保 Context / Looper 准备只发生一次；现有六参数入口保持原校验，新入口完成自己安装核验后才调用公共服务函数。
- [ ] 新电脑入口向当前 flavor Provider 请求领取，不能用 `content call` 将挑战输出到电脑终端；失败有界退出，日志仅记录稳定阶段与异常类型。
- [ ] 添加新 main 的 R8 keep 规则，保留现有 `EmbeddedServerMain` 与第三方 JNI 规则。
- [ ] 按任务 3 的最小必要接线注册电脑等待，在模拟器实际执行安装目录原生启动器，验证 Provider 领取、原有握手与 shell 服务运行。此任务与任务 3 由同一个执行者串行推进，不在半成品接线时要求独立审查。

**针对性测试命令：**

```powershell
.\gradlew.bat :app:testTensorDebugUnitTest --tests '*ComputerBootstrapLeaseTest' --tests '*BridgePolicyTest' :app:testLegacyDebugUnitTest --tests '*ComputerBootstrapLeaseTest' --tests '*BridgePolicyTest' --stacktrace --console=plain
```

**检查点：** 新原生入口到现有服务的完整引导可在 x86_64 模拟器成功；无电脑等待时不能领取。若此核心目标失败，优先提交 ISSUE 与真实错误证据，不以 UI/文档完成替代。

## 任务 3：替换长命令与保持客户端撤销安全

**接口：**

- `EmbeddedLauncher.startComputer(onCommand: (String) -> Unit): String?` 保留签名。
- `EmbeddedConnection` 增加准备电脑等待的受控函数，返回本轮 generation / 取消句柄；结束只能清理自己的代际。
- `MainViewModel.startEmbeddedComputer()`、`cancelEmbeddedComputer()`、`computerAdbCommand: StateFlow<String?>` 保持 UI 接口，不持久化启动授权。

- [ ] 将 `ComputerAdbCommand` 改为任务 1 的路径/用户短命令构造；实际路径来自当前 `ApplicationInfo.nativeLibraryDir` 的固定文件，用户来自当前安装 UID，而不是硬编码 0。
- [ ] 替换旧 Base64 单测，验证只产生固定 native 命令、拒绝非安装目录路径/错误文件名/控制字符/可注入 shell 元字符和非法用户 ID；不要保留没有消费者的 Base64 helper。
- [ ] `startComputer` 不再调用常规 `start` 的内联命令执行分支；保留无线/root `BootstrapCommand` 用法。电脑准备、展示、等待、取消/超时/异常及成功清理以本轮 generation 绑定。
- [ ] 保留 ViewModel 捕获调用方 Job 并仅在协调器内部电脑等待阶段恢复取消的实现，finally 清空命令 UI。快速点击不能创建两个 Job 或清理错代际。
- [ ] 保留 UI 门禁、复制敏感标记、约 110 秒有效期、取消与超时提示。文案解释每次先开启应用等待，安装升级后可能需复制新路径；不能说路径字符串是一次性令牌。
- [ ] 不提供自动 Base64 fallback；native 文件缺失、权限或安装核验失败时展示真实错误，仍可显式选用原有手机无线或 root 入口。
- [ ] 删除不再使用的 Base64 电脑入口代码和对应测试期望，更新 `AGENTS.md`、电脑启动验证文档；保留前一轮实际证据作为历史，并区分新短命令语义。

**针对性测试：** 双 flavor 的 `ComputerAdbCommandTest`、`ComputerBootstrapLeaseTest`、`BridgePolicyTest`、`BootstrapCommandTest`。每个 Gradle test task 各自指定 `--tests`，避免过滤只应用到最后一个 task。

## 任务 4：冻结快照、独立审查与最终验收

- [ ] 执行者运行默认双 flavor debug 编译、模拟器开关双 flavor debug 编译、针对性测试与双 flavor lint。记录命令、退出码、产物、快照和实际运行目标。
- [ ] 运行一次当前快照的双 flavor全量单测，完整记录结果，允许既有 Windows 两项失败继续作为已知限制；新失败必须调查。全量失败不能写成 PASS。
- [ ] 验证默认包只有 ARM64；显式模拟器开关下请求 `:app:assembleTensorRelease` / `:app:assembleLegacyRelease` 必须拒绝，没有产生模拟器 release。用配置/任务与 APK 检查分别报告，不拿 dry-run 冒充 release 运行验证。
- [ ] 检查新固定入口 R8 keep 的代码与配置；实际 release/R8 构建受既有签名配置限制时标 NOT_VERIFIED，不新增或替换正式签名。必要检查记录负责人，由管理员决定是否需要进一步闭环。
- [ ] 执行者发布 RESULT，列出增量与累计新建/修改/删除文件及哈希、已完成/阻塞/NOT_VERIFIED。不修改正式冻结快照。
- [ ] 管理员核实结果完整后创建一个独立审查者；审查者只读审查累计差异，并独立运行重点反例；与下一轮写入及构建不得交叉。
- [ ] 重点反例包括：普通应用 UID 调用领取、无等待、无线/root 待启动时领取、错误 user、未知原生参数、外部复制启动器、两次 claim、取消/超时、旧代际交付和两包身份不匹配。能在真实设备入口执行的反例优先，不以另一个语言镜像实现代替实际入口。
- [ ] 管理员核实 findings 后统一发 FIX，执行者有界返修，独立审查者复审。未授权 reviewer 直接派发返修，不开启双路调度。
- [ ] 管理员验证最终两包真实 UI 生成短命令，经 Windows CMD / PowerShell 执行后 Ready、shell UID 2000；模拟器 TCP ADB 入口同样就绪，并撤销新增连接。
- [ ] 验证取消后无等待不能启动，重新开启等待可使用同安装路径命令；超时与应用进程结束拒绝旧授权；已领取旧挑战不能交付到新轮次。两包服务进程独立，无 IMS 写入、无自动恢复。
- [ ] 根据累计 finding 与证据接受：Critical / Important 清零，必要设备验证闭环；真实 ARM64、物理 USB、无线 TLS 配对与实际 Wi-Fi 未测时明确限制，不称全设备验收。

## Herdr 执行契约

- 管理员：当前主会话，主 tab `w6:t1`，label `内置ADB壳`。
- 执行者授权：Codex，模型 `gpt-6-luna`，`model_reasoning_effort="high"`，`-s danger-full-access -a never`，仅本批次。
- 独立审查者授权：Codex，模型 `gpt-6.1-sol`，同档位与 CLI 权限，仅本批次；只读是角色行为约束。
- 用户已指定 Herdr 执行，不再次询问执行方法；未授权备用模型、CLI 或自动降级。
- 一个实现 cwd 一个写入者。管理员只写方案/调度记录；执行者工作期间管理员不修改产品代码、不并行构建。
- 本机临时目录：`C:/Users/myste/AppData/Local/Temp/tensorims-native-adb-herdr-20261011/`，保存批次状态、基线摘要、ASSIGN / RESULT / REVIEW、日志和快照。
- 首轮执行任务包含任务 1–3 和任务 4 的执行者验证，观察预算 45 分钟。关键链路阻塞应提前提交 ISSUE，不为等待预算耗尽才报告。
- 独立审查观察预算 25 分钟；返修依据具体 findings 设置有界预算。同一阻塞连续两次无实质进展时按管理员收敛规则处理，不靠换任务或 attempt 重置。
- 每轮先登记匹配 native session 的 `herdr_watch`，再通过 `/2` 非阻塞派单；结果落盘唤醒管理员。没有活动角色时不声称后台实施。

## 计划自检与批准条件

- 设计第 1–3、7 节由任务 3 覆盖；第 4 节由任务 1–2 覆盖；第 5 节由任务 2–3 覆盖；第 6 节由任务 1、4 覆盖；第 8–10 节由全局约束与任务 4 覆盖。
- 命令构造、三个内部参数、Lease 策略、固定 main 与平台 helper 的引用保持一致。
- 真实 APP 成功、纯逻辑反例、原生 ELF、发布 ABI 和真实设备未测范围分别报告，不互相替代。
- 用户批准本文后，管理员沿已批准 Herdr 路线启动执行者；稳定快照形成后启动审查者，不预先空等。
