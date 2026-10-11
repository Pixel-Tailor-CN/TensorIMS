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

## 启动布局优化与 360dp 窄屏适配

2026-10-11 在批次 `b-startup-layout-20261011` 下对“特权模式”页面与 `ComputerAdbDialog` 命令行弹窗完成了视觉层级、操作体验及小屏适配微调：

- **弹窗视觉与操作层级优化**（`ComputerAdbDialog.kt`）：
  - 命令文本使用 `Surface(surfaceContainerHighest)` 独立代码块容器包裹，提供 12dp 统一内边距与圆角，字体采用等宽 Monospace，强化终端命令属性。
  - 操作按钮建立清晰的 Material 3 视觉层级：次要退出操作为 `TextButton`（取消），次要分享操作为 `OutlinedButton`（发送），主要复制操作为高权重 filled `Button`（复制命令）。
  - 按钮容器采用 `FlowRow` 搭配 `Arrangement.spacedBy(8.dp, Alignment.End)`，在宽屏及中文窄屏下自然居右对齐同行排列，在空间受限时自适应折行。
  - 解释说明与过期提示分离为主要正文与次要注释（`onSurfaceVariant`），字号与行距层级更加分明。
- **状态卡片与诊断清理**（`BackendStatusUi.kt`、`BackendSettingsScreen.kt`）：
  - 在 `BackendErrorGuidance` 中过滤 `EMBEDDED_UNAVAILABLE`，避免内置模式初始未启动的断开状态下误显红色诊断信息。
  - 未连接状态使用中性正文色（`onSurface`），展开连接详情使用统一圆角 `Surface(surfaceContainerHighest)` 容器规整呈现。
  - 顶部操作按钮（收起详情、切换模式、刷新连接）增加 `spacedBy(8.dp, 4.dp)` 间距，官方模式引导按钮与无线调试操作按钮规范为全宽样式。
- **360dp 窄屏与中英双语自适应验证**：
  - 在 API 37 模拟器通过 `wm density 480`（宽度精确对应 360dp）以及 `cmd locale set-app-locales` 分别对中文（zh-CN）与英文（en-US）环境完成实际界面渲染核验。
  - 首轮 360×800dp 中文窄屏下三按钮可同行居右，英文主按钮“Copy command”自动折行至第二行。该结果不覆盖所有屏高；后续 360×640dp 独立检查发现等待说明裁剪，已由下述 FIX 修复。
  - 验证后已通过 `wm density reset` 恢复 420 density，并通过清空 locale 恢复系统语言，已停止测试应用进程，未卸载或清除应用数据。


### 2026-10-11 短屏命令弹窗滚动修复（FIX attempt 2）

- `ComputerAdbDialog` 的步骤、命令块和等待说明现在共用一个最高 360dp 的可滚动正文区域；命令块仍保留自身滚动与文本选择，底部取消、发送、复制操作留在正文之外。未改动等待时限、取消/发送/复制回调或命令生成流程。
- 在 `emulator-5554`（API 37、x86_64）以 `wm size 1080x1920`、`wm density 480`、`font_scale=1.0` 验证，实际视口为 360×640dp。英文（`en-US`）弹窗正文存在溢出时可滚动到完整步骤、命令、约 110 秒等待说明及安装路径复用提示；滚动前后取消、发送、复制按钮均保持可见。中文（`zh-CN`）同尺寸下完整正文与三按钮首屏可见。两种语言都通过实际截图和 UI hierarchy 检查。
- 交互核对：复制后对话框仍打开；发送打开系统分享 chooser，返回后等待仍在原对话框，本次未选择收件人或发送；取消关闭等待并返回特权模式页。未执行生成的 ADB 命令。模拟器原显示为 1080×2400、420 density、应用语言为空；验证结束已恢复这些值并停止测试应用。
- 双 flavor 默认 debug 与 x86_64 模拟器专用 debug 构建均通过，原始 Gradle 日志和退出码位于本批次临时目录 `fix-a2/build-default-a2.log`、`fix-a2/build-default-a2.exit`、`fix-a2/build-emulator-a2.log`、`fix-a2/build-emulator-a2.exit`。安装前比较现有 tensor 包与新 APK 的公开签名证书 SHA-256 一致（`5c3aa6771a9e54e7f4f4ebd2252403b3b888a3c8662cea4794463793fb6fef33`）；未读取签名密钥或签名配置。
- 本轮截图：修复前英文短屏弹窗为 `review-validation-a1/02-short-en-dialog-stable.png` 与 `03-short-en-dialog-swipe.png`；修复后英文首屏、滚动后分别为 `fix-a2/05-dialog-top.png`、`06-dialog-scrolled.png`，中文首屏为 `fix-a2/11-zh-dialog-top.png`。页面对照为 `screen-privilege.png`（attempt 2 前）与 `fix-a2/02-privilege.png`、`10b-zh-privilege-page.png`（修复后）。原截图未覆盖或重命名。
- 更正 attempt 1 结果中的两处截图索引误标：`baseline-screenshots/screen-choice.png` 实际是“Choose a privilege mode”模式选择对话框；`baseline-screenshots/screen-adb-dialog.png` 实际是特权模式页面（可见无线配对和电脑 ADB 卡片），不是命令弹窗。上述历史文件和结果保持原样；弹窗修复前证据采用独立审查记录 `review-validation-a1/02-short-en-dialog-stable.png`、`03-short-en-dialog-swipe.png`。
- 执行者本轮屏幕交互仅覆盖 tensor 模拟器包；两包编译通过不构成 legacy 运行时 UI 证据。legacy 的独立运行验证由以下管理员最终检查补齐。实体 Pixel、ARM64 及真实无线/USB 连接仍未验证。

### 独立审查与管理员最终验证

- GPT-6.1-Sol 代码复审通过，确认有限高度整体滚动及精确发送／复制／取消回调不变；Antigravity/Gemini 独立 UI 验收通过，实际覆盖 tensor 的 360×640dp 中英文正文滚动、分享返回、复制、取消和 1.15 倍字体抽查。`LAYOUT-A1-01` 已在代码与实际 UI 两侧闭环。
- 管理员分别比对 tensor、legacy 已安装包与新 debug APK 的公开证书，一致后覆盖安装；两包都验证复制保留对话框、系统分享面板返回仍保留命令与等待、取消结束等待，以及新显式等待执行命令进入 `Ready`（shell UID 2000）。未选择或发送给真实分享收件人。
- legacy 另在 360×640dp 英文环境实际验证正文可滚动到完整等待及安装路径复用说明，三个操作按钮始终可见、可操作；已查看实际截图，不以 XML 字符串存在或编译成功替代可见性检查。
- 管理员验证期间系统权限面板曾阻挡页面；拒绝非电脑启动所需权限后完成验证，未自动授予权限。模拟器显示尺寸与 density 已恢复原值；未执行 IMS、CarrierConfig、持久化 VoLTE 或 Captive Portal 写入，未读取签名密钥或配置，未卸载或清除数据。
- 本批次临时产物：`review-result-a2.json`（代码审查）、`ui-review-result-a1.json`（UI 验收）、`manager-final-ui-result.json`（两包最终检查）、两包就绪截图与 legacy 短屏滚动截图。除本段实际验证记录及历史措辞澄清外，审查后未修改可执行源码。
- 实体 ARM64、物理 USB、无线 TLS/JNI 配对与实际 release/R8 保持未验证；全量单测未重跑，既有 Windows `OperationJournalTest` 目录同步失败未修改。本轮没有覆盖全部后端状态或全部辅助技术场景。

### 2026-10-11 页脚操作布局修正（FIX attempt 3）

按用户进一步澄清，attempt 2 中记录的“取消 TextButton、发送 OutlinedButton、复制 Filled Button”仅作为历史说明保留；本次以标准 AlertDialog 的平面 `TextButton` 统一三个操作。页脚从左到右为“发送”，右侧分组为“取消”“复制命令”，中间使用弹性留白分开。命令正文仍限制最大高度 360dp 并可滚动；没有更改发送、复制、取消回调和约 110 秒等待逻辑。

本次用 API 37 x86_64 `emulator-5554` 对更新后的 tensor debug 包进行了实际界面检查。默认 emulator-native debug 与双 flavor 默认 debug 编译均通过，日志及退出码保存在任务临时目录 `fix-a3/build-default-a3.log` / `.exit`、`fix-a3/build-emulator-a3.log` / `.exit`（均为 0）。安装前核对了设备现有 tensor 包和新 debug APK 的公开签名证书 SHA-256，均为 `5c3aa6771a9e54e7f4f4ebd2252403b3b888a3c8662cea4794463793fb6fef33`；未读取签名私钥或签名配置。使用 `adb install -r` 原位更新，没有清数据或卸载。

| 界面 | 屏幕 | 检查结果 |
| --- | --- | --- |
| English | 411×914dp 正常视口 | `fix-a3/15-en-normal-dialog.png` 显示 Send 在左、Cancel 与 Copy command 在右；平面文字按钮均可见。 |
| 简体中文 | 411×914dp 正常视口 | `fix-a3/18-zh-normal-dialog.png` 显示“发送”在左、“取消”与“复制命令”在右。 |
| English | 360×640dp 窄视口 | `fix-a3/09-after-dialog-en-scrolled.png` 检查正文可滚动到底，等待时限及取消、超时、进程退出说明可读，页脚固定可见；UI dump 显示按钮文字 bounds（不是完整点击区域）为 Send `[171,1595][268,1655]`、Cancel `[383,1595][515,1655]`、Copy command `[611,1595][912,1655]`。 |
| 简体中文 | 360×640dp 窄视口 | `fix-a3/21-zh-narrow-dialog-top.png` 显示窄屏完整弹框和正确左右分组；UI dump 显示按钮文字 bounds（不是完整点击区域）为“发送”`[176,1506][262,1567]`、“取消”`[552,1506][638,1567]`、“复制命令”`[742,1506][912,1567]`。 |

交互检查：English 窄屏复制后仍显示等待弹框；发送打开 Android 系统分享面板，未选接收者或发送内容，返回后按取消结束等待。简体中文窄屏复制后弹框仍显示，按取消后弹框关闭。截图及 UI dump 位于 `fix-a3/`，例如 `10-copy-keeps-dialog.xml`、`11-send-chooser.png`、`12-cancelled.xml`、`22-zh-narrow-after-copy.png`、`23-zh-narrow-after-cancel.xml`。执行者本轮没有执行生成命令，也没有启动服务或触发 IMS 等业务配置写入。

验收结束后已恢复模拟器 `wm size reset`、`wm density reset`、字体比例 `1.0`，将 tensor 应用语言恢复为空列表（跟随系统），并停止测试应用。当前设备系统语言为 `en-US`。上述结果仅覆盖 API 37 x86_64 模拟器的 tensor UI；真实 Pixel/ARM64、legacy 实机 UI、真实接收者分享和服务启动仍未在本次执行中验证。既有 attempt 2 历史记录不变。

### 页脚调整的最终审查与双包运行验证

- Sol 的代码复审 `review-result-a3.json` 通过，确认三个标准 `TextButton` 位于对话框原生操作区：发送靠左，取消与复制靠右；正文滚动、分享、剪贴板敏感标记、取消和等待期限保持不变。
- Gemini 实际核对正常／360×640dp 窄屏的中英文布局与交互，按钮点击区域高度为 48dp。初次报告错误列入了非本快照文件及哈希，管理员拒绝采信；后续仅修正报告与协议字段，保留原错误记录，独立重核真实五文件哈希，最终 `ui-review-result-a3.json` / `ui-review-report-a3.md` 通过，报告完整性问题 `UI-A2-REPORT-01` 已闭环。未重复构建或 UI 测试来掩盖错误。
- 管理员另以本轮 APK 分别覆盖更新 tensor、legacy，安装前公开证书比对一致。两包都通过复制保留弹框、分享面板返回保留命令／等待、取消关闭，以及新显式等待执行命令进入 `Ready`（shell UID 2000）。没有向真实接收者发送命令，没有执行 IMS 或其他业务配置写入。
- legacy 另验证了 360×640dp 英文弹框：发送在左，取消／复制在右，正文滚动后完整显示约 110 秒等待与安装路径复用提示；管理员查看了实际截图。尺寸与 density 恢复原值。产物位于本批次临时目录 `manager-final-a3/`，包括 `manager-final-ui-result.json`、两包就绪截图及 `manager-legacy-short-scrolled.png`。
- 审查后没有修改可执行源码，只补充本段实际验证记录并澄清文字 bounds 与触控区域的区别。当前页脚轮次未验证大字体、完整辅助技术矩阵或全部后端状态；实体 ARM64、物理 USB、无线 TLS/JNI 配对、实际 release/R8 与既有全量单测限制保持不变。本轮布局尚未提交。
