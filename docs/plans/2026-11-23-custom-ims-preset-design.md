# IMS 自定义配置方案（单一预设）设计

## 意图与目标

用户在 IMS 配置页调好一套适合自己的开关与选项后，希望把它保存为自己专属的 **1 个**预设方案，方便在换卡、改乱配置或重置后一键填回草稿（仍需用户手动点击底部“应用更改”才写入系统）。

保存动作与底部“应用更改”分离，由独立按钮显式触发，避免平时临时修改个别开关时误覆盖用户精心维护的预设方案。

## 范围边界

- **目标**：
  - 全局仅维护 **1 个**自定义预设（「我的配置」），独立保存在 `custom_ims_preset` `SharedPreferences`（两包各自独立，不干扰 `sim_config_<subId>` 自动恢复历史与重置阻止标记）。
  - 通过独立按钮主动将**当前界面生效值与草稿合并后的配置（`state.values`）**保存为「我的配置」，无论当前是否已点击“应用更改”均可保存；已存在时允许显式覆盖更新。
  - 点击「我的配置」仅填充草稿（经 `stageTargetEdits` 校验），底部“应用更改”面板滑出，用户点击“应用更改”后才写入系统。
  - 右上角菜单在存在自定义预设时提供“删除我的配置”入口（含二次确认弹窗）。
- **非目标**：
  - 不支持创建多个预设、自定义名称或独立表单页面。
  - “应用更改”不再自动覆盖「我的配置」；后台自动恢复与单卡重置同样不修改或删除「我的配置」。
  - 不参与开机/后端就绪自动恢复。
  - 持久化 VoLTE 与 Captive Portal 不纳入此预设。

## 方案对比与决策

1. **方案 1（已选定）：顶部预设栏独立保存/更新按钮 + 首位「我的配置」预设 Chip**
   - 优点：与底部“应用更改”完全解耦；无论当前有无待应用草稿（底栏是否滑出），用户都能在预设区域直接看到保存/更新入口；视觉直观，不增加额外页面。
   - 取舍：预设栏多 1 个操作按钮，仍通过现有 `horizontalScroll` 平滑承载。
2. **方案 2：与“应用更改”共用按钮，应用成功后自动覆盖**
   - 未选原因：用户明确希望由独立按钮控制保存，避免临时改开关冲掉预设。
3. **方案 3：把保存按钮放进底部“应用更改”面板**
   - 未选原因：底部面板仅在存在未应用草稿时展示，用户无法把当前已生效（无草稿）的配置保存为预设。

## 关键设计与交互

### 数据模型与校验

- 在 `ImsConfigPreset.kt` 中新增纯函数 `sanitizeCustomPreset(targets: Map<Feature, FeatureValue>): Map<Feature, FeatureValue>`：
  - 仅保留通过 `CarrierConfigChoices.isValidTarget(feature, value)` 校验的合法项；
  - 字符串项执行 `trim()` 并过滤空白值；
  - 若 `Feature.FIVE_G_NR` 明确为 `false`，移除 `Feature.FIVE_G_NR_MODE`。
- 在 `ConfigurationRepository.kt` 中增加 `custom_ims_preset` 偏好读写：
  - `loadCustomPreset(): Map<Feature, FeatureValue>?`：逐项按 `FeatureValueType` 读取并经 `sanitizeCustomPreset` 校验，空时返回 `null`。
  - `saveCustomPreset(targets: Map<Feature, FeatureValue>): Map<Feature, FeatureValue>?`：经 `sanitizeCustomPreset` 过滤，非空则写入并返回实际保存结果。
  - `clearCustomPreset()`：清空 `custom_ims_preset`。

### 界面与状态流

- `ImsConfigViewModel` 维护 `customPreset: StateFlow<Map<Feature, FeatureValue>?>`：
  - 初始化时从 `repository.loadCustomPreset()` 加载；
  - 新增 `saveCustomPreset()`：读取当前 `state.values`（要求 `ready && !applying`），经 `repository.saveCustomPreset(...)` 保存并更新 `customPreset` 与轻提示（`notice`）；
  - 新增 `clearCustomPreset()`：清空持久化并更新 `customPreset` 与轻提示；
  - `apply()` 不自动覆盖 `customPreset`。
- `ImsConfigScreen` 顶部预设栏：
  - **尚未保存时**：
    ```text
    [ 推荐配置 ]  [ 国内 5G ]  [ 国内 LTE ]  [ 全部开启 ]  [ + 保存为我的配置 ]
    ```
  - **已保存时**：
    ```text
    [ ★ 我的配置 ]  [ 推荐配置 ]  [ 国内 5G ]  [ 国内 LTE ]  [ 全部开启 ]  [ ↺ 更新我的配置 ]
    ```
  - 点击 `★ 我的配置`：调用 `onPreset(customPreset)` 填进草稿（单卡支持含字符串在内的全部合法项，全卡模式自动跳过字符串项并提示）；
  - 点击 `+ 保存为我的配置`：直接把当前页面配置（`state.values`）保存为「我的配置」并提示已保存；
  - 点击 `↺ 更新我的配置`：弹出简短确认对话框（避免误点覆盖），确认后覆盖保存；
  - 右上角菜单保留“加载已保存配置”和“重新读取系统配置”，在 `customPreset != null` 时额外显示“删除我的配置”（带确认弹窗）。

### 留待实现时落地的细节

- 补充中英文字符串资源（`values/strings.xml` 与 `values-zh-rCN/strings.xml`）。
- 针对 `sanitizeCustomPreset` 及自定义预设通过 `stageTargetEdits` 套用（单卡/全卡、字符串跳过、无效项过滤、5G NR 关闭联动）补充纯 Kotlin 单元测试。
- 同步更新 `AGENTS.md` 约定与双语 `README.md` / `README_CN.md`。
