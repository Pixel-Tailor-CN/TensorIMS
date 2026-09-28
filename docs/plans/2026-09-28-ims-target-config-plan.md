# IMS 目标配置编辑器实施计划

> 执行方式：使用 executing-plans 在当前会话逐项实现，完成后进行独立代码审查。用户已明确要求文档完成后继续实现，不另设重复批准步骤。

**目标：** 开关生成明确目标，实时读取初始化，写入回读一致才确认成功，兼容旧版历史语义。

**架构：** 纯 Kotlin 映射与草稿合并模型；共用特权 helper 负责逐卡事务；专用 ViewModel 管理读取、编辑和结果；现有历史仓库保存版本化意图。

**技术：** Kotlin、Compose、StateFlow、Shizuku、CarrierConfigManager，无新框架。

**设计：** [目标配置设计](2026-09-28-ims-target-config-design.md)。

## 全局约束

- Android 13+，VoNR 按 API 34 分界；缺失键不可默认为开启或关闭。
- 文档和注释中文，日志英文；不改依赖版本。
- 不修改持久化 VoLTE、Captive Portal 的业务含义。
- 不使用全卡清空实现单项关闭；不把框架默认冒充运营商默认。
- 用户已确定拆分：布尔显式关闭；可读取完整系统默认参数的项目支持恢复并说明来源；字符串／缺默认值项目仅支持重置恢复，不新增原值备份。

## 重点检查

- 旧历史 false：迁移函数不产生禁用目标；任务 1 测试。
- 仅 SA 或仅 NSA：读取为开启；任务 1 测试。
- 双卡不同值与缺键：混合不落成 false，缺键不允许编辑；任务 1、4 测试与布局验证。
- 权限/服务失败、切卡、身份更换：无虚假成功，过期结果丢弃；任务 2、4 审查。
- 部分失败与重试：仅成功卡保存版本化历史，失败目标保留；任务 3、4 验证。

## 任务 1：定义映射和草稿模型

文件：新增 `model/TargetConfig.kt`、`model/TargetConfigMapper.kt`；新增 `model/TargetConfigTest.kt`。

接口：`TargetConfigMapper.read(values: Map<String, Any>, sdk: Int): Map<Feature, FeatureValue>`；`enabledValues(feature)`；`disabledValues(feature)`；`legacyTargets(config)`；`commonValues(snapshots)`。

- [x] 先写测试：VoNR 关闭生成 false 且不主动修改可见性键；漫游 VoWiFi 显式 false；NR 单一模式可读；旧 false 被省略；混合值不被补默认；Enhanced 4G LTE 不干预 LTE+。
- [x] 运行测试确认缺少新模型而失败，再实现纯映射与状态数据类。
- [x] 运行针对性测试通过。

## 任务 2：实现实时读取和目标写入

文件：新增 `privileged/TargetConfigurationOperation.kt` 与恢复辅助文件；修改 `ImsModifier.kt`、`BrokerInstrumentation.kt`、`ShizukuProvider.kt`。

接口：`ShizukuProvider.readTargetConfig(context, subId): TargetConfigSnapshot`；`applyTargetConfig(context, subId, identity, targets, canStart): TargetConfigSnapshot`。快照同时携带错误和回读值。

- [x] 按用户分类回填设计；增加恢复策略模型与系统默认参数读取，缺失或类型错误时提示重置。
- [x] 新协议使用嵌套目标 Bundle，不与 CarrierConfig 实际键混合；旧协议不变。
- [x] 在特权工作线程完成活动卡检查、读取、写入、有限回读；使用现有统一权限委托。
- [x] Broker 与主路径调用同一 helper；只有权限错误或空结果使用现有 fallback 策略。
- [x] 检查无卡、缺键、超时、身份变化、失败回读和恢复依据缺失。

## 任务 3：版本化历史与自动恢复

文件：修改 `ConfigurationRepository.kt`、`AutoRestoreController.kt`。

接口：增加 `loadTargets(subId)`、`saveTargets(subId, edits)`，`RestoreTarget` 携带语义版本。

- [x] 旧 `load/buildBundle` 保留，旧自动恢复路径保持不变。
- [x] 新历史只保留明确意图，缺少键不补默认；保存成功卡前合并原有效历史。
- [x] 新历史自动恢复通过目标协议回读，失败不写开机成功标记。
- [x] 测试新旧历史合并，包括旧版关联条件（NR 关闭时忽略其优化开关）。

## 任务 4：接入配置编辑器

文件：新增 `viewmodel/ImsConfigViewModel.kt`；修改 `MainActivity.kt`、`ImsConfigScreen.kt`、中英文 strings。

- [x] ViewModel 持有各卡快照、草稿编辑、加载和结果，复用 ConfigurationOperations 串行执行。
- [x] 页面首次进入及 Shizuku 状态变化时读取，读取失败显示重试；不显示历史默认开关。
- [x] 用共同值初始化界面；混合值给启用/关闭选择；未知、不支持和操作中禁用编辑。
- [x] 预设和加载历史仅改变草稿；无历史提示；重新读取替换草稿。
- [x] 固定本次目标与 SIM 身份，逐卡执行并保存成功卡；失败保持待应用；旧异步结果不得覆盖新选择。
- [x] 修改说明与恢复类描述，保留 IMS 实时状态入口。

## 任务 5：验证与交付

- [x] 更新 AGENTS.md 的数据协议、历史版本和页面行为约定。
- [x] 运行 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug lint --stacktrace --console=plain`。
- [x] 模拟器检查 Shizuku 未就绪、布局和导航；读取失败保留草稿由纯 Kotlin 测试覆盖。当前无 Pixel/Shizuku 真机，写入与回读留作设备验收。
- [x] 独立代码审查并处理实质问题；检查 diff 和变更范围。
- [x] 回填本计划进度和验证结果，不自动提交或推送本轮代码。

## 执行记录

- 已调查旧构造器、页面草稿初始化、历史仓库、自动恢复和 AOSP 覆盖合并行为。
- 裁定：本轮沿用用户当前工作区，不切换其分支或自动提交；以本计划作为进度记录，避免额外规划目录。
- 已完成只读入口 `ImsConfigurationReader`、`TargetConfigurationReader`、嵌套协议 `TargetConfigProtocol` 和 Shizuku 读取方法，已注册 Manifest。与设计一致，读取与实际 IMS 能力查询独立。
- 已完成目标编辑器、参数恢复分类、主/Broker 共用写入与回读、v2 稀疏历史及自动恢复；v1 自动恢复保留旧语义。
- 纯 Kotlin 回归覆盖显式关闭、默认参数类型校验、混合值、历史迁移、完整目标保留及刷新失败保护。构建、全部单测及 lint 通过；lint 仍有弃用和未使用资源等警告。
- 独立审查发现并修复：显示值相同不能丢弃完整目标；保留草稿仍须刷新系统基线；临时读取失败不能吞掉草稿。最终回读使用同次原始配置核对并再次检查 SIM 身份。
- 模拟器安装启动成功，检查 IMS 页面在 Shizuku 未运行时展示说明、不显示虚假开关、禁用应用按钮。当前仅有未安装 Shizuku 的 Android 17 模拟器，未验证真实 Pixel 的特权写入及运营商效果。
- 决策已解决：用户要求按可关闭／可读取默认值与仅可重置两类拆分；继续完成任务 2 至 5，不引入原值备份。
