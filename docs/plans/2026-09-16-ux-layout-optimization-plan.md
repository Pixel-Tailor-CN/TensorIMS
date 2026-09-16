# TensorIMS 页面布局与 UX 体验优化实施计划

## 实施步骤

1. **多语言文本与资源准备**
   - 在 `res/values/strings.xml` 和 `res/values-zh-rCN/strings.xml` 中增加新所需的文本：
     - 预设服务器名称（Google、V2EX、小米、vivo、华为等）；
     - IMS 配置草稿说明横幅文本、快捷推荐预设 Chip 文本（推荐配置、全部启用等）；
     - 高级工具分区标题（常规维护、危险操作）；
     - 粘贴与清除辅助文本。

2. **首页与状态卡片优化**
   - 重构 `DeviceStatusCard.kt`：
     - 根据 Shizuku 状态自适应渲染 Hero Status 容器；
     - 突出显示就绪/未运行/未授权/需更新状态，并将关键操作作为主要 CTA 展示；
     - 设备型号与系统版本优化排版。
   - 优化 `HomeScreen.kt` 的 SIM 选择卡片视觉：
     - 单选行增强选中底色与卡片边界，提升点按与识别体验。

3. **IMS 配置页重构**
   - 修改 `ImsConfigScreen.kt`：
     - 顶部增加草稿心智提示 Banner（提示修改后需点击底部按钮应用，非系统实时状态）；
     - 顶部 App Bar 增加更多菜单 `DropdownMenu`，收拢已保存配置读取与恢复默认草稿，附带清晰文字说明；
     - 增加推荐预设 FilterChips（如“推荐配置”一键开启核心五项）；
     - 各分类（通话、网络、显示、高级覆盖）改用 `OutlinedCard` 容器包裹。

4. **Captive Portal 配置页优化**
   - 修改 `CaptivePortalScreen.kt`：
     - 增加快捷预设服务器 Chips，点击一键填充 HTTP 与 HTTPS 地址；
     - 输入框增加清除按钮与剪贴板一键粘贴按钮；
     - 自定义输入区域加入 `AnimatedVisibility` 动效；
     - 实验性功能提示采用带警告图标的强调容器。

5. **高级工具与持久化 VoLTE 优化**
   - 修改 `AdvancedToolsScreen.kt`：
     - 分为常规操作与危险区域两个卡片，危险操作“重置配置”采用警示色与明确分组；
     - 优化 `ImsStatusDialog`：移除硬编码 `Color.Red`，使用主题语义色与状态 Badge。
   - 优化 `PersistentVolteCard.kt`：
     - 状态行改为指标卡片/胶囊网格；
     - 按钮排布与说明文本分层。

6. **构建与验证**
   - 运行单元测试：`.\gradlew.bat :app:testDebugUnitTest --stacktrace --console=plain`
   - 编译 Debug APK：`.\gradlew.bat :app:assembleDebug --stacktrace --console=plain`
   - 运行 Lint 检查：`.\gradlew.bat lint --stacktrace --console=plain`
