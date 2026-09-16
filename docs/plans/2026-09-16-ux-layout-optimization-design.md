# TensorIMS 页面布局与 UX 体验优化设计

## 背景

在前期设置中心拆分重构后，TensorIMS 已经形成了清晰的四级导航结构（首页控制台 -> IMS 配置 / 系统网络 / 高级工具）。但在实际使用与视觉交互上，仍存在以下体验痛点：
1. **首页门禁状态与次要信息混同**：Shizuku 状态作为应用特权能力的核心前置条件，当前以普通单行文本呈现，状态不正常时视觉警示与操作引导弱；SIM 卡选择组件占用垂直空间且表现传统。
2. **IMS 配置心智模型与操作效率**：开关仅代表“本次将写入的配置草稿”而非“系统当前实际状态”，此核心提示目前淹没在最底部的纯文本小字中；顶部两颗旋转图标（读取已保存 vs 恢复默认草稿）视觉同质化高、含义模糊；缺少针对中国大陆用户高频场景的“推荐配置预设（Presets）”快捷键。
3. **Captive Portal 输入成本高**：切换自定义时需手动输入长绝对 URL，移动端输入易错且痛苦，缺少常用知名服务器快速填充。
4. **高级工具破坏性操作未有效隔离**：重置配置等破坏性动作与常规只读/维护操作混排；IMS 状态弹窗存在硬编码 `Color.Red`，破坏 Material 3 主题连续性且不利于无障碍体验；持久化 VoLTE 卡片文字堆砌密集，缺少指标化概览。

## 优化目标

1. **首页状态仪表盘化**：将 Shizuku 状态提升为自适应容器状态卡片（Hero Status Card），正常状态低调融入，异常状态突出警示并明确主操作；优化 SIM 卡选择呈现。
2. **IMS 配置心智强化与快速预设**：
   - 顶置“配置草稿说明横幅”，明确告知用户开关含义及需要点击应用生效；
   - 顶部 Action 按钮去歧义，采用清晰的弹出菜单或带文字辅助的操作；
   - 提供“推荐配置”（一键开启 VoLTE / VoWiFi / VoNR / 5G NR / 5G 信号阈值）等快捷预设 Chip；
   - 对通话、网络、显示、高级覆盖四大板块进行容器分组（Card Grouping），提升视觉层次。
3. **Captive Portal 体验增强**：
   - 增加常用知名服务器一键填入 Chips（Google 官方、V2EX、MIUI、vivo、华为等）；
   - 输入框增加清空与剪贴板粘贴辅助操作；
   - 自定义输入区域加入展开过渡动效；
   - 实验性设置提示使用带警告语义的容器。
4. **高级工具视觉分级与色彩规范**：
   - 将常规安全操作（查看状态、日志、重启）与破坏性操作（重置运营商配置）进行视觉隔离；
   - 彻底移除硬编码 `Color.Red`，使用 Material 3 语义色系统与状态胶囊 Badge；
   - 重构 `PersistentVolteCard`，指标结构化排版，优化按钮与说明布局。

## 详细设计方案

### 1. 首页 (HomeScreen)

- **Shizuku 状态横幅（Hero Status Card）**：
  - `READY`：以 `surfaceContainer` 背景呈现，带绿色圆点/状态标签及“已连接”，不抢占视觉注意力；
  - `NOT_RUNNING` / `NO_PERMISSION` / `NEED_UPDATE`：以 `errorContainer` / `tertiaryContainer` 高对比背景呈现，带清晰的图标、解释和主操作按钮（如“请求权限”或“刷新”）。
  - 设备基本信息（型号、Android 版本）保持清晰轻量，右侧保留“设备详情”触发点。
- **SIM 卡选择组件现代化**：
  - 增强 SIM 卡选项的视觉边界与卡片感，支持点击选中状态的即时高亮；
  - 保持空状态与刷新操作的易触达性。

### 2. IMS 配置页 (ImsConfigScreen)

- **顶置草稿说明横幅 (Draft Notice Banner)**：
  - 在功能列表最顶部增加提示横幅：告知开关控制本次应用内容，修改后需点击底部“应用配置”写入系统。
- **顶部操作栏优化**：
  - 将容易混淆的 `Icons.Rounded.History` 与 `Icons.Rounded.SettingsBackupRestore` 整合入下拉菜单或直观操作项，附带明确文字提示，防止误按。
- **快捷推荐配置预设 (Presets)**：
  - 在横幅下方提供横向快捷 Chips：
    - `推荐配置`：一次性将 VoLTE、VoWiFi、VoNR、5G NR、5G 信号阈值设为开启；
    - `全部开启` / `重置草稿` 等快捷辅助。
- **卡片化分组容器 (Card Sections)**：
  - 通话、网络、显示、高级覆盖四个部分改用 `OutlinedCard` 包裹，内含分割线，使滚动时板块结构分明，避免连续长列表带来的视觉疲劳。

### 3. Captive Portal 配置页 (CaptivePortalScreen)

- **知名服务器快捷填充 Chips**：
  - 在自定义模式下，提供快捷预设选项：
    - Google 官方：`http(s)://connectivitycheck.gstatic.com/generate_204`
    - V2EX 节点：`http(s)://captive.v2ex.co/generate_204`
    - 小米 / MIUI：`http(s)://connect.rom.miui.com/generate_204`
    - vivo：`http(s)://wifi.vivo.com.cn/generate_204`
    - 华为 / HarmonyOS：`http(s)://connectivitycheck.platform.hicloud.com/generate_204`
  - 点击即可同步填充 HTTP 与 HTTPS 地址。
- **输入框交互微调**：
  - 加入右侧清除文本图标（Trailing Icon）以及快捷粘贴功能。
- **动画与警示**：
  - 切换到自定义时使用 `AnimatedVisibility` 平滑展开；
  - 实验性功能提示使用警告语义容器。

### 4. 高级工具页 (AdvancedToolsScreen) 与持久化 VoLTE

- **分区管理**：
  - 卡片 1（状态诊断与维护）：查看 IMS 状态、重启 IMS、查看应用日志。
  - 卡片 2（危险操作区 Danger Zone）：重置运营商配置。使用浅红背景或红色警告标题，配合二次确认。
- **状态弹窗 (ImsStatusDialog) 无障碍优化**：
  - 废弃 `Color.Red`；
  - 采用主题的 `colorScheme.primary`（可用/已注册）与 `colorScheme.error` / `colorScheme.outline`（未就绪/不可用）；
  - 辅以状态胶囊或对号/叉号图标，保证在色弱或深色模式下的极佳辨识度。
- **持久化 VoLTE 卡片 (PersistentVolteCard)**：
  - 将 Opt-In、用户开关、IMS 注册状态以并排指标胶囊（Key-Value Pill）呈现；
  - 操作按钮明确分为主要（启用）与次要（恢复原设置、刷新）；
  - 将冗长的限制与更新说明收敛至层次清晰的辅助文本区。

## 兼容性与技术约束

- 严格遵循 `AGENTS.md` 约定的信息架构与技术规范：
  - 不改变已有的特权 Instrumentation 交互与 ViewModel 流程；
  - 所有新字符串均在 `values/strings.xml` 与 `values-zh-rCN/strings.xml` 中配置中英文支持；
  - 维持现有 Compose + Material 3 架构，运行命令验证编译与单元测试。
