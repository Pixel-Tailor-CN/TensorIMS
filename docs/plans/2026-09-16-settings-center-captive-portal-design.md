# TensorIMS 设置中心与 Captive Portal 配置设计

## 背景

当前 `MainActivity` 同时承担系统信息、Shizuku 状态、自动恢复、SIM 选择、IMS 功能草稿、应用/重置操作、持久化 VoLTE、IMS 状态查看、日志入口和提示说明。`Feature` 已包含多项字符串与布尔配置，继续在主页面追加开关会导致页面越来越长，也会把不同作用域的功能混在一起。

这次调整不只为新增 Captive Portal 地址修改，而是先建立一个可继续扩展的设置中心结构：SIM 级 IMS 配置、设备级网络配置和即时高级工具分别进入不同页面，主页面只负责状态概览、操作对象选择和导航。

## 目标

1. 把首页从“所有功能堆叠页”改成“状态 + SIM + 功能入口”的轻量控制中心。
2. 把现有 IMS Feature 按用户心智模型分组，后续新增 IMS 开关不再增加首页长度。
3. 新增“系统网络”一级入口，并在其中提供 Captive Portal HTTP/HTTPS 地址修改。
4. 把持久化 VoLTE、IMS 实时状态、重启 IMS、重置运营商配置和日志统一放到“高级工具”。
5. 保留现有业务语义：IMS 配置仍按 SIM 工作，自动恢复仍只恢复 IMS 配置，持久化 VoLTE 仍是独立即时操作。
6. 不把 SettingsProvider 写入成功描述成“系统一定已经使用新的 Captive Portal 地址”。

## 非目标

- 不新增底部导航栏；当前功能规模仍适合“首页 -> 分类 -> 详情”的层级导航。
- 不把 Captive Portal 变成 SIM 级配置，也不纳入 `Feature`、SIM 历史或自动恢复。
- 不提供关闭网络验证、修改 `captive_portal_mode`、fallback URL、other URL 列表等额外网络调优项。
- 不自动断开 Wi-Fi、切换飞行模式或强制重建网络，只提示用户重新连接网络以触发新的检测流程。
- 不硬编码某个“推荐国内地址”作为默认值；“系统默认”表示删除 TensorIMS 写入的覆盖值，由系统自行选择默认/资源配置。

## 信息架构

```text
TensorIMS 首页
├─ 设备与权限状态
│  └─ 设备详情（Dialog）
├─ SIM 选择
├─ IMS 配置
├─ 系统网络
│  └─ Captive Portal
├─ 高级工具
│  ├─ IMS 当前状态
│  ├─ 持久化 VoLTE
│  ├─ 重启 IMS
│  ├─ 重置运营商配置
│  └─ 应用日志
└─ Shizuku 就绪后自动应用配置
```

`LogcatActivity` 保持独立 Activity；其余新增页面使用单 Activity 内的 Compose Navigation，避免为每个设置页创建新的 Activity，同时保留同一份 SIM 选择状态与现有 `MainViewModel`。

## 首页

首页只回答三个问题：当前设备环境是否可操作、当前选中哪张 SIM、用户要进入哪类功能。

```text
TensorIMS                                    GitHub

┌──────────────────────────────────────────┐
│ Pixel 10 Pro · Android 17                │
│ ● Shizuku 已就绪                         │
│                                  设备详情 │
└──────────────────────────────────────────┘

SIM 卡
┌──────────────────────────────────────────┐
│ 中国移动 · SIM 1                     ▾   │
└──────────────────────────────────────────┘

配置
┌──────────────────────────────────────────┐
│ ☎ IMS 配置                           >   │
│ VoLTE、VoWiFi、VoNR、5G 等               │
├──────────────────────────────────────────┤
│ 🌐 系统网络                          >   │
│ Captive Portal 等设备级网络设置           │
├──────────────────────────────────────────┤
│ 🛠 高级工具                          >   │
│ 状态、即时操作与诊断                     │
└──────────────────────────────────────────┘

Shizuku 就绪后自动应用配置                 [●]
```

### 设备状态卡

正常状态只显示设备型号、Android 版本和 Shizuku 状态。点击“设备详情”打开 Dialog，展示应用版本、系统 build、安全补丁和 GitHub 入口。

Shizuku 未运行、版本过旧或未授权时，状态卡扩大并显示对应操作。状态必须同时使用图标/文字，不能只依赖颜色。

### SIM 选择

SIM 卡选择仍支持“所有 SIM 卡”和单卡。首页只负责选择，不再在同一卡片内放“查看系统配置”“重启 IMS”等操作。

IMS 配置入口在未选择 SIM 时禁用并显示“请先选择 SIM”；系统网络始终可进入；高级工具可进入，但其中依赖单卡的操作按现有规则禁用。

## IMS 配置页

IMS 配置页只处理 `Feature` 草稿和“应用配置”。现有功能按用途分组，而不是继续放进单个长 Card。

```text
< IMS 配置
  中国移动 · SIM 1                 历史 / 更多

通话
VoLTE                                      [●]
VoWiFi                                     [●]
漫游时 VoWiFi                              [ ]
VoNR                                       [●]
视频通话                                   [●]
Cross-SIM Calling                          [ ]
UT 补充业务                                [●]

网络
5G NR                                      [●]
5G 信号强度阈值                            [●]

显示
5G+ 图标                                   [●]
Enhanced 4G LTE                            [●]
隐藏 LTE+ 数据图标                         [ ]
LTE 显示为 4G                              [ ]

高级覆盖
运营商名称                                  当前值 >
IMS User Agent                             当前值 >

提示说明...

┌──────────────────────────────────────────┐
│                应用更改                   │
└──────────────────────────────────────────┘
```

布尔配置使用整行可点击的 `ListItem + Switch`；字符串配置使用列表项进入编辑 Dialog，避免两个长 `OutlinedTextField` 抢占列表空间。

“应用更改”固定在页面底部 `bottomBar`，用户不必滚到列表末尾才能提交。加载历史和恢复默认草稿放到 TopAppBar 的菜单中；危险的“重置运营商配置”不再与日常“应用配置”等权展示，移动到高级工具。

现有 `Tips()` 中与 IMS/运营商配置相关的说明移动到 IMS 配置页底部。

## 系统网络页

系统网络是设备级设置的扩展容器，不依赖 SIM。

```text
< 系统网络

联网检测
┌──────────────────────────────────────────┐
│ Captive Portal                       >   │
│ 系统默认 / 自定义                         │
└──────────────────────────────────────────┘
```

首版只有 Captive Portal 一项是允许的。后续新增 Private DNS 辅助、网络验证诊断等设备级功能时继续放在这里，而不是回到首页堆按钮。

## Captive Portal 详情页

```text
< Captive Portal

Android 使用联网检测地址判断当前网络是否可访问
互联网。该设置属于实验性功能，部分系统版本可能优先
使用 NetworkStack 资源配置。

检测地址
○ 系统默认
● 自定义

HTTP 地址
http://example.com/generate_204

HTTPS 地址
https://example.com/generate_204

更改后建议重新连接当前网络以触发新的检测流程。

┌──────────────────────────────────────────┐
│                  保存                     │
└──────────────────────────────────────────┘
```

### 编辑规则

- “系统默认”：保存时删除 `captive_portal_http_url` 和 `captive_portal_https_url` 的 SettingsProvider 覆盖值。
- “自定义”：HTTP 与 HTTPS 两个地址都必须填写。
- HTTP 字段只接受绝对 `http://` URL，HTTPS 字段只接受绝对 `https://` URL，且必须包含 host。
- 输入前后空格在保存前去除；不自动修改路径、域名或协议。
- 保存按钮在请求过程中禁用并显示进度。
- 写入后重新读取 SettingsProvider，只有读回值与目标一致时才提示“已写入系统设置”。
- 成功提示使用“已写入系统设置；重新连接网络后验证是否生效”，不使用“已生效”。

### 系统兼容边界

当前 AOSP NetworkStack 仍会读取 `captive_portal_http_url` 与 `captive_portal_https_url`，但 `NetworkMonitor#getSettingFromResource` 明确优先使用 NetworkStack 的资源配置，其次才使用 SettingsProvider 值。因此：

1. TensorIMS 能确认“设置值是否成功写入/删除”，不能仅凭 SettingsProvider 回读确认 NetworkMonitor 实际探测 URL。
2. Pixel 某个系统版本若通过 RRO/模块资源提供非空 `config_captive_portal_http_url` 或 `config_captive_portal_https_url`，SettingsProvider 覆盖可能被忽略。
3. UI 和 README 都必须保留这一限制说明。
4. Android 13～17 的实际效果需要真机验证，至少覆盖项目当前可用 Pixel 测试机；验证结果写回实施计划。

参考 AOSP：

- <https://android.googlesource.com/platform/packages/modules/NetworkStack/+/refs/heads/main/src/com/android/server/connectivity/NetworkMonitor.java>

## 高级工具页

```text
< 高级工具
  中国移动 · SIM 1

状态与诊断
IMS 当前状态                              >
应用日志                                  >

即时操作
持久化 VoLTE                              > / 展开区域
重启 IMS                                  >

重置
重置运营商配置                            >
```

“IMS 当前状态”继续复用现有实时读取逻辑和状态 Dialog；“应用日志”继续打开 `LogcatActivity`。

持久化 VoLTE 保持“独立即时操作，不属于 Feature 配置草稿”的语义，移动到高级工具后不改变数据模型、备份文件或自动恢复规则。

“重启 IMS”和“重置运营商配置”均要求单张 SIM。重置运营商配置保留当前先恢复持久化 VoLTE 原始值再清 CarrierConfig 的流程，并增加明确确认 Dialog；“所有 SIM 卡”选择下禁用。

## 导航与状态管理

- 使用 `androidx.navigation:navigation-compose`，保留一个 `MainActivity`。
- 路由固定为 `home`、`ims_config`、`system_network`、`captive_portal`、`advanced_tools`。
- 不引入 Kotlin Serialization；路由不携带复杂对象。
- 当前选中 SIM 由 `MainActivity` 顶层状态持有并传入各页面；保存 `subId` 而不是保存完整 `SimSelection`，SIM 列表刷新后通过现有 reconcile 逻辑重新匹配。
- `MainViewModel` 继续负责 IMS、Shizuku、SIM 与持久化 VoLTE；新增 `SystemNetworkViewModel` 单独负责 Captive Portal 的读取、编辑、保存和错误状态，避免继续扩大 `MainViewModel` 的职责。
- 所有特权写入仍通过 `ShizukuProvider -> Instrumentation -> shell permission delegation`，UI 不直接访问 `Settings.Global`。

## Captive Portal 特权实现

新增 `CaptivePortalSettingsModifier` Instrumentation，同时负责读取、写入与清除两个 Settings.Global key：

- `captive_portal_http_url`
- `captive_portal_https_url`

使用字符串常量而不是依赖可能隐藏/变更可见性的 `Settings.Global.CAPTIVE_PORTAL_*` 编译期字段，并在代码旁用中文注释标明对应 AOSP key 和兼容原因。

写入/清除后立即读回并返回结果；Instrumentation 的失败统一转换为现有 `PrivilegedError` 文本。`ShizukuProvider` 新增对应 suspend 接口，继续复用现有 Instrumentation 串行互斥和 Shizuku 状态检查。

恢复系统默认只删除上述两个 key，不写入 Google URL，也不改动 NetworkStack resource、DeviceConfig、fallback URL 或 portal mode。

## Material 3 与可访问性

- 一级分类使用 Card 内的 `ListItem`，整行可点击，触控目标不小于 48dp。
- 二级设置使用 Material 3 `ListItem`、`Switch`、`RadioButton`、`OutlinedTextField`、`TopAppBar` 和 `Scaffold.bottomBar`。
- 不使用颜色作为唯一状态信息；Shizuku、错误、成功均同时提供文字。
- 错误提示靠近对应输入框；HTTP/HTTPS 错误分别显示。
- 页面标题和返回按钮使用系统预测返回兼容的 Navigation Compose 默认返回栈，不自定义手势拦截。
- 继续支持深色模式和动态颜色，不写死页面背景色。

## 文案原则

- IMS 页面使用“应用更改”，强调这是草稿提交。
- Captive Portal 使用“系统默认 / 自定义”“已写入系统设置”“重新连接网络后验证是否生效”。
- 不用“优化网络”“加速网络”等无法保证的营销表述。
- 高风险操作使用明确对象，例如“重置中国移动 SIM 1 的运营商配置”。

## 验证标准

### 编译与静态检查

- `./gradlew.bat :app:assembleDebug --stacktrace --console=plain`
- `./gradlew.bat lint --stacktrace --console=plain`
- `git diff --check`

### JVM 测试

Captive Portal URL 规范化/校验属于纯 Kotlin 逻辑，新增单元测试覆盖：

- 合法 HTTP URL。
- 合法 HTTPS URL。
- 协议错误。
- 缺少 host。
- 空字符串。
- 前后空格被 trim。

Compose 页面与 SettingsProvider 特权写入不为了形式引入 Robolectric；遵循仓库现有规则，以编译和真机验证为主。

### 真机验证

至少在一台支持设备上验证：

1. 首页状态、SIM 选择和三个分类入口。
2. IMS 配置应用、历史恢复、默认草稿和现有自动恢复行为没有回归。
3. 高级工具中的 IMS 状态、持久化 VoLTE、重启 IMS、重置运营商配置和日志入口。
4. Captive Portal 读取当前覆盖值。
5. 写入合法 HTTP/HTTPS 地址并确认 SettingsProvider 回读一致。
6. 重新连接网络后观察联网验证行为和相关 NetworkStack 日志，记录是否实际使用新地址。
7. 选择“系统默认”后确认两个覆盖 key 被删除，并再次重新连接网络验证。
8. Shizuku 未运行、未授权、写入失败时不误报成功。

如果真机确认某个 Pixel/Android 版本的 NetworkStack 资源覆盖始终压过 SettingsProvider，本功能仍保留为实验性工具，但 README 必须写明该版本验证结果，不能把 provider 写入成功描述为有效覆盖。
