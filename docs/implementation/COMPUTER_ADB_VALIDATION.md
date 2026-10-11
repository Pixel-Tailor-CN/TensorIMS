# 电脑 ADB 启动内置服务

## 运行方式

1. 在“特权模式”页选择内置 Shizuku，生成当前安装包的一次性启动等待。
2. 在电脑上通过 USB 或无线 ADB 连接设备并完成 Android 的授权。
3. 在等待对话框关闭前，执行应用显示的完整 `adb shell` 命令。
4. 应用完成安装身份检查、单次 claim 和服务握手后显示就绪。

命令直接运行当前安装目录中的 `libtensorims_starter.so`，只接收 `--user <id>`。应用更新后安装目录可能变化，需要重新生成命令。等待最长约 110 秒；等待取消、超时或应用进程退出后，本轮授权失效，在没有新的用户显式等待时，旧命令不能启动新服务。安装路径未变化时，同一短命令可以用于新的显式等待；一次性的是本轮授权，不是路径字符串。Android 端只接受真实 shell UID 2000 或 root UID 0，并检查当前包、用户、版本、签名、APK 路径、native library 路径、后端 epoch 和一次性 challenge。启动服务沿用现有私有 Provider、会话 Binder 与握手，不经过官方 Shizuku 静态 Binder。

本地 C++ PIE 只负责校验固定参数和安装路径、清理危险环境变量、以当前 APK 为 classpath 启动固定 `ComputerBootstrapMain`。Kotlin 入口通过受限 Provider claim 获取短时启动凭证，然后复用 `EmbeddedServerMain` 的认证服务创建与回传。无线 ADB、USB ADB 和电脑端 ADB 最终汇合到相同的私有服务握手。

## 分享与等待

等待对话框提供“发送”“复制命令”和“取消”。“发送”通过系统分享 chooser 以 `ACTION_SEND`、`text/plain` 传递完整当前命令；不会传递 challenge 或其他状态，也不取消、重置或延长原等待。关闭 chooser 后返回现有等待，仍受原始约 110 秒期限约束。

### 分享补充验收

- 独立审查核验四文件快照及两 flavor 实际编译字节码，确认分享类型、唯一命令文本、异常处理、复制敏感标记及取消回调，未发现阻塞问题。
- 管理员在 API 37 模拟器分别验证两包：打开系统分享面板、返回后命令与等待保留、复制不关闭对话框、取消关闭等待，以及返回后执行命令进入 `Ready`。
- tensor 额外验证分享面板停留后等待仍按原约 110 秒期限结束，没有重新计时；临时缩小至约 360 dp 宽度时三个按钮均完整可见、可操作，并已恢复模拟器原尺寸。
- 未选择或向真实分享收件者发送内容；中文及大字体的实际渲染留待后续布局优化验证。默认和模拟器双 flavor debug 构建通过，未新增 Compose/Intent 测试基础设施。
- 本轮产物为批准临时目录中的 `share-manager-ui-result.json`、分享面板及普通／窄屏截图、公开签名证书比对记录；未读取签名密钥或配置。

## ABI 与构建

- 默认 debug/release 产物仅包含 `arm64-v8a` 原生启动器；保留原有 ARM64 Pairing JNI 库。
- 模拟器专用 debug 开关为 `-PtensorimsNativeStarterEmulator=true`，仅增加 x86_64 原生启动器；启用时必须显式传入完整限定的 `:app:assembleTensorDebug` 和/或 `:app:assembleLegacyDebug` 任务。Gradle 会拒绝 release、flavor 聚合、简称及其他任务请求。模拟器 APK 保留 ARM64 Pairing JNI，但没有 x86_64 Pairing JNI，因此不用于手机无线配对验收。
- APK 的 ARM64 ABI 目录名为 `arm64-v8a`，但 Android 安装后的 `nativeLibraryDir` 与 ELF 自身路径使用 ISA 名 `arm64`；Kotlin 路径校验和 CMake 原生路径后缀分别按安装 ISA 处理。模拟器 x86_64 的 ABI 和 ISA 名相同。
- `tensor` 与 `legacy` 使用相同统一 Kotlin namespace，但分别根据当前 `applicationId` 查找 Provider、APK 和 native library，不共用服务或会话。

## 2026-10-11 本地验收

环境为 Windows、Android API 37 x86_64 模拟器 `emulator-5554`。本次验证结果：

- 两 flavor 默认双包 debug 构建通过；两 flavor 模拟器专用构建通过。
- 两 flavor 的定向电脑启动、安装身份和 bridge policy 单测通过；两 flavor debug lint 通过。
- tensor 与 legacy APK 分别安装后，从各自当前安装路径运行启动器，界面均进入 `Ready`；服务进程 UID 为 shell（2000）。
- 对有效等待执行完成后，服务握手成功；针对无等待的重复启动、无效用户参数和额外参数的拒绝路径在 tensor 包验证通过。
- 将模拟器专用开关用于 release 构建时，Gradle 在签名配置加载前拒绝该请求。
- 全量单测在两个 flavor 各有 2 个既存 `OperationJournalTest` 失败：Windows 上目录 `FileChannel.open` 抛出 `AccessDeniedException`，调用路径为既有 `OperationJournal.syncDirectory`。其他定向用例通过；本次没有为通过 Windows 主机测试改变 Android 持久化安全行为。

### ARM64 安装 ISA 修复复验

- 两 flavor 的 `ComputerAdbCommandTest` 各 4 项通过：真实安装 ISA `arm64` 与模拟器 `x86_64` 接受，伪造的 `arm64-v8a` 安装目录拒绝。
- 默认与模拟器专用双 flavor debug 构建通过。默认 APK 仅包含 `lib/arm64-v8a/libtensorims_starter.so`；模拟器 APK 额外包含 `lib/x86_64/libtensorims_starter.so`。两种构建均保留 `lib/arm64-v8a/libadb.so`。
- 新构建 ARM64 与 x86_64 启动器均为 PIE（ELF `DYN`，带 `INTERP`），所有 `LOAD` 段按 `0x4000` 对齐；ELF 路径校验 ISA 字面值分别是 `arm64` 和 `x86_64`。这确认 APK ABI 名仍为 `arm64-v8a`，未与安装 ISA 混用。
- 模拟器开关下 `:app:assembleLegacy`、`:app:assembleTensorD`、以及混合 Debug/Release 请求均在配置期拒绝。实体 ARM64 Pixel 运行仍未验证。

### 管理员最终模拟器集成与独立复审

- 独立审查者核验累计 32 文件冻结快照；ARM64 安装 ISA 问题与两项管理员 finding 均已关闭，无遗留 Critical/Important。通过的是代码与关键边界审查，不代表实体设备验收。
- 管理员比对已安装 APK 与新 debug APK 的公开签名证书，确认一致后使用覆盖安装刷新两包，未卸载、清除数据或读取签名密钥/配置。
- 新快照的两个 flavor 均从界面生成短命令，分别通过 Windows CMD、PowerShell 和实际 TCP ADB 连接进入 `Ready`，界面确认运行身份为 shell（UID 2000）。TCP 使用模拟器 `127.0.0.1:5555`，不等同于手机无线调试 TLS 配对或物理 USB。
- 两包均验证取消后拒绝、约 110 秒等待超时后拒绝、应用进程结束后拒绝，以及没有等待时重复启动被 claim 拒绝；已有正常连接未受重复命令影响。新显式等待可重复使用同一安装路径，并可在超时后重新成功启动。
- 两包同时保持就绪；单独结束 tensor 主进程后，legacy 的连接仍保持就绪。验证未执行 IMS、CarrierConfig、持久化 VoLTE 或 Captive Portal 写入，也未停止官方 Shizuku。
- 原始验收产物位于本批次批准临时目录：`manager-integration-result.json`、公开证书比对记录及两包最终就绪截图。验证脚本曾因日志缓冲区计数、CMD 宿主引号和更新后的超时文案产生断言错误；修正验证脚本后完成复验，未将这些错误描述为产品缺陷或伪报通过。
- 修复后全量单测未重新运行；既有 Windows 目录同步失败仍保留。实际 release/R8 未执行，签名工作流留待另行授权。

当前没有可用的 arm64-v8a 实体 Pixel。真实设备上的 USB ADB、Android 无线调试授权、Wi-Fi/TLS、ARM64 Pairing JNI 和 IMS/CarrierConfig 功能仍需按 [设备验收记录](DEVICE_ACCEPTANCE.md) 与无线验证说明完成；模拟器结果不替代这些验收。
