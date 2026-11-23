# IMS 自定义配置方案（单一预设）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 允许用户在 IMS 配置页通过独立按钮将当前页面配置保存为全局唯一的「我的配置」自定义预设，置顶显示在预设栏首位供一键填回草稿，并支持覆盖更新与删除。

**Architecture:** 在 `ImsConfigPreset.kt` 增加纯函数 `sanitizeCustomPreset` 过滤并规整自定义预设目标；在 `ConfigurationRepository.kt` 新增独立的 `custom_ims_preset` `SharedPreferences` 读写接口，与 `sim_config_<subId>` 自动恢复历史完全解耦；`ImsConfigViewModel` 维护全局 `customPreset: StateFlow<Map<Feature, FeatureValue>?>` 并提供保存/删除动作；`ImsConfigScreen` 在顶部预设栏渲染首位 `★ 我的配置` 与独立的保存/更新按钮，并在右上角菜单提供删除入口。

**Tech Stack:** Kotlin 2.4.21 / Jetpack Compose + Material 3 / Android SharedPreferences / JUnit 4。

**Spec:** [docs/plans/2026-11-23-custom-ims-preset-design.md](2026-11-23-custom-ims-preset-design.md)

## Global Constraints

- 全局仅允许 1 个自定义预设，两包（`tensor` / `legacy`）各自独立保存在 `custom_ims_preset`，不得写入 `sim_config_<subId>`，不得参与自动恢复。
- 保存动作与底部“应用更改”完全独立；`apply()`、自动恢复和重置配置均不得覆盖或删除自定义预设。
- 点击「我的配置」仅修改草稿（经 `stageTargetEdits` 校验），必须由用户手动点击底部“应用更改”才写入系统。
- 注释使用中文，日志 message 使用英文；字符串同时维护 `values/strings.xml` 与 `values-zh-rCN/strings.xml`。
- 变更完成后至少运行 `.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain` 验证双 flavor 编译，并运行 `TargetConfigTest` 验证纯 Kotlin 逻辑。

## Review Focus

- **空有效配置保存**：全卡模式下若各卡不同导致 `state.values` 过滤后为空，点击保存不能清空或覆盖已有「我的配置」，应提示没有可保存的配置项。
- **关闭 5G NR 与非法整数选项**：`FIVE_G_NR = false` 时必须移除 `FIVE_G_NR_MODE`；系统读到的未知整数值（如 `-1` 或非法枚举值）及空白字符串必须在保存与读取时过滤，不污染预设。
- **单卡字符串项跨全卡套用**：单卡保存含 `CARRIER_NAME` / `IMS_USER_AGENT` 的预设后，切到“所有 SIM”点「我的配置」，`stageTargetEdits` 应跳过字符串项、套用其余合法项并提示跳过，且不修改原预设。
- **切卡或后端状态变化时的弹窗收敛**：`selectedSim?.subId`、`backendEpoch` 或 `shizukuStatus` 变化时，覆盖/删除确认弹窗应自动关闭。
- **损坏偏好容错**：`custom_ims_preset` 若包含未知 key 或类型错乱条目，`loadCustomPreset()` 应安全忽略异常项而不抛异常，全部无效时返回 `null`。

---

### Task 1: 自定义预设纯模型过滤与校验

**Files:**
- Modify: `app/src/main/java/app/mystery0/ims/tensor/model/ImsConfigPreset.kt`
- Test: `app/src/test/java/app/mystery0/ims/tensor/model/TargetConfigTest.kt`

**Interfaces:**
- Consumes: `Feature`, `FeatureValue`, `FeatureValueType`, `CarrierConfigChoices.isValidTarget`, `stageTargetEdits`
- Produces: `fun sanitizeCustomPreset(targets: Map<Feature, FeatureValue>): Map<Feature, FeatureValue>`

- [ ] **Step 1: 在 `TargetConfigTest.kt` 添加自定义预设过滤与套用的失败测试**

```kotlin
    @Test
    fun `自定义预设过滤非法值空白字符串及关闭 5G 时的 NR 模式`() {
        val raw = mapOf(
            Feature.VOLTE to bool(true),
            Feature.FIVE_G_NR to bool(false),
            Feature.FIVE_G_NR_MODE to integer(1),
            Feature.VOWIFI_NAME_FORMAT to integer(99),
            Feature.USSD_METHOD to integer(2),
            Feature.CARRIER_NAME to FeatureValue("  China Mobile  ", FeatureValueType.STRING),
            Feature.IMS_USER_AGENT to FeatureValue("   ", FeatureValueType.STRING),
        )
        assertEquals(
            mapOf(
                Feature.VOLTE to bool(true),
                Feature.FIVE_G_NR to bool(false),
                Feature.USSD_METHOD to integer(2),
                Feature.CARRIER_NAME to FeatureValue("China Mobile", FeatureValueType.STRING),
            ),
            sanitizeCustomPreset(raw),
        )
        assertEquals(
            mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1)),
            sanitizeCustomPreset(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1))),
        )
        assertTrue(sanitizeCustomPreset(mapOf(Feature.IMS_USER_AGENT to FeatureValue("  ", FeatureValueType.STRING))).isEmpty())
    }

    @Test
    fun `含字符串的自定义预设在单卡可完整套用而在全卡安全跳过字符串`() {
        val preset = sanitizeCustomPreset(mapOf(
            Feature.VOLTE to bool(true),
            Feature.CARRIER_NAME to FeatureValue("CMCC", FeatureValueType.STRING),
        ))
        val snap1 = TargetConfigSnapshot(1, "a",
            mapOf(Feature.VOLTE to bool(false), Feature.CARRIER_NAME to FeatureValue("", FeatureValueType.STRING)),
            recovery = mapOf(Feature.VOLTE to ConfigRecovery.SWITCH, Feature.CARRIER_NAME to ConfigRecovery.RESET_REQUIRED))
        val snap2 = TargetConfigSnapshot(2, "b",
            mapOf(Feature.VOLTE to bool(false), Feature.CARRIER_NAME to FeatureValue("", FeatureValueType.STRING)),
            recovery = mapOf(Feature.VOLTE to ConfigRecovery.SWITCH, Feature.CARRIER_NAME to ConfigRecovery.RESET_REQUIRED))

        val single = stageTargetEdits(ImsEditorState(subId = 1, snapshots = listOf(snap1)), preset)
        assertFalse(single.skipped)
        assertEquals(preset, single.edits)

        val all = stageTargetEdits(ImsEditorState(subId = -1, snapshots = listOf(snap1, snap2)), preset)
        assertTrue(all.skipped)
        assertEquals(mapOf(Feature.VOLTE to bool(true)), all.edits)
    }
```

- [ ] **Step 2: 运行定向单元测试确认编译/测试失败**

Run: `.\gradlew.bat :app:testTensorDebugUnitTest --tests "app.mystery0.ims.tensor.model.TargetConfigTest" --stacktrace --console=plain`
Expected: FAIL（`sanitizeCustomPreset` 尚未定义）

- [ ] **Step 3: 在 `ImsConfigPreset.kt` 实现 `sanitizeCustomPreset`**

在 `app/src/main/java/app/mystery0/ims/tensor/model/ImsConfigPreset.kt` 添加 `sanitizeCustomPreset(targets: Map<Feature, FeatureValue>): Map<Feature, FeatureValue>`：
- 仅保留通过 `CarrierConfigChoices.isValidTarget(feature, value)` 的条目；
- `FeatureValueType.STRING` 执行 `(value.data as String).trim()` 并过滤空白；
- 若 `targets[Feature.FIVE_G_NR]?.data == false`，过滤掉 `Feature.FIVE_G_NR_MODE`。

- [ ] **Step 4: 重新运行定向单元测试确认通过**

Run: `.\gradlew.bat :app:testTensorDebugUnitTest --tests "app.mystery0.ims.tensor.model.TargetConfigTest" --stacktrace --console=plain`
Expected: PASS

---

### Task 2: 持久化存储与 `ImsConfigViewModel` 状态调度

**Files:**
- Modify: `app/src/main/java/app/mystery0/ims/tensor/ConfigurationRepository.kt`
- Modify: `app/src/main/java/app/mystery0/ims/tensor/viewmodel/ImsConfigViewModel.kt`
- Test: `app/src/test/java/app/mystery0/ims/tensor/model/TargetConfigTest.kt`（如有纯序列化/解析边界函数则同步覆盖）

**Interfaces:**
- Consumes: `sanitizeCustomPreset`（Task 1）
- Produces:
  - `ConfigurationRepository.loadCustomPreset(): Map<Feature, FeatureValue>?`
  - `ConfigurationRepository.saveCustomPreset(targets: Map<Feature, FeatureValue>): Map<Feature, FeatureValue>?`
  - `ConfigurationRepository.clearCustomPreset()`
  - `ImsConfigViewModel.customPreset: StateFlow<Map<Feature, FeatureValue>?>`
  - `ImsConfigViewModel.saveCustomPreset()`
  - `ImsConfigViewModel.clearCustomPreset()`

- [ ] **Step 1: 在 `ConfigurationRepository.kt` 增加独立的 `custom_ims_preset` 读写与清理**

在 `ConfigurationRepository` 中：
- 新增 `private val customPresetPrefs by lazy { context.getSharedPreferences("custom_ims_preset", Context.MODE_PRIVATE) }`（保持与 `Application.onCreate` 首次解锁保护兼容，仅在用户进入界面时访问）。
- `loadCustomPreset(): Map<Feature, FeatureValue>?`：从 `customPresetPrefs.all` 按 `Feature.entries` 安全提取匹配类型（`Boolean` / `String` / `Int`，类型不符直接忽略，不抛 `ClassCastException`），经 `sanitizeCustomPreset` 校验后为空返回 `null`。
- `saveCustomPreset(targets: Map<Feature, FeatureValue>): Map<Feature, FeatureValue>?`：先经 `sanitizeCustomPreset` 过滤，若为空直接返回 `null`（不覆盖既有预设）；非空则清空并写入对应键值，返回实际保存的 `Map`。
- `clearCustomPreset()`：清空 `customPresetPrefs`。

- [ ] **Step 2: 在 `ImsConfigViewModel.kt` 暴露自定义预设状态流及保存/删除方法**

- 添加 `private val _customPreset = MutableStateFlow(repository.loadCustomPreset())` 与 `val customPreset = _customPreset.asStateFlow()`。
- `saveCustomPreset()`：
  - 若 `!current.ready || current.applying || ConfigurationOperations.busy.value || !shizukuReady` 直接返回；
  - 调用 `repository.saveCustomPreset(current.values)`；
  - 若返回非空：更新 `_customPreset.value = saved`，并设置 `_state.value = current.copy(notice = R.string.custom_preset_saved)`；
  - 若返回 `null`：保留既有 `_customPreset.value`，设置 `_state.value = current.copy(notice = R.string.custom_preset_nothing_to_save)`。
- `clearCustomPreset()`：
  - 若 `current.applying || ConfigurationOperations.busy.value` 直接返回；
  - 调用 `repository.clearCustomPreset()`，置 `_customPreset.value = null`，设置 `_state.value = current.copy(notice = R.string.custom_preset_deleted)`。
- 确认 `apply()`、`reloadAfterReset()` 与 `load()` 不修改或清空 `_customPreset`。

---

### Task 3: `ImsConfigScreen` / `MainActivity` 独立保存按钮、UI 交互、双语资源与文档

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/java/app/mystery0/ims/tensor/ui/screens/ImsConfigScreen.kt`
- Modify: `app/src/main/java/app/mystery0/ims/tensor/ui/MainActivity.kt`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `README_CN.md`

**Interfaces:**
- Consumes: `ImsConfigViewModel.customPreset`, `ImsConfigViewModel.saveCustomPreset`, `ImsConfigViewModel.clearCustomPreset`（Task 2）
- Produces: 完整的 IMS 自定义单一预设用户交互与双语说明

- [ ] **Step 1: 在中英文 `strings.xml` 新增文案资源**

中文（`app/src/main/res/values-zh-rCN/strings.xml`）：
- `preset_custom`: `我的配置`
- `preset_save_custom`: `保存为我的配置`
- `preset_update_custom`: `更新我的配置`
- `preset_delete_custom`: `删除我的配置`
- `preset_update_custom_confirm`: `将用当前页面显示的配置覆盖已保存的“我的配置”。保存后不会立即写入系统。`
- `preset_delete_custom_confirm`: `确定删除已保存的“我的配置”？不会影响当前已应用的系统配置。`
- `custom_preset_saved`: `已保存为“我的配置”。`
- `custom_preset_deleted`: `已删除“我的配置”。`
- `custom_preset_nothing_to_save`: `当前没有可保存的有效配置项。`

英文（`app/src/main/res/values/strings.xml`）：
- `preset_custom`: `My preset`
- `preset_save_custom`: `Save as my preset`
- `preset_update_custom`: `Update my preset`
- `preset_delete_custom`: `Delete my preset`
- `preset_update_custom_confirm`: `Overwrite your saved "My preset" with the configuration currently shown on this page. Saving does not write to the system immediately.`
- `preset_delete_custom_confirm`: `Delete your saved "My preset"? This does not affect configurations already applied to the system.`
- `custom_preset_saved`: `Saved as "My preset".`
- `custom_preset_deleted`: `Deleted "My preset".`
- `custom_preset_nothing_to_save`: `No valid configuration values available to save.`

- [ ] **Step 2: 更新 `ImsConfigScreen.kt` 与 `MainActivity.kt`**

- `ImsConfigScreen` 新增参数：
  - `customPreset: Map<Feature, FeatureValue>?`
  - `onSaveCustomPreset: () -> Unit`
  - `onClearCustomPreset: () -> Unit`
- 新增本地弹窗状态（均以 `selectedSim?.subId, backendEpoch, shizukuStatus` 为 key，随环境变化自动收起）：
  - `confirmUpdateCustomPreset`
  - `confirmDeleteCustomPreset`
- 顶部预设栏 `Row`：
  - 当 `customPreset != null` 时，首位展示 `AssistChip`（`leadingIcon` 使用 `Icons.Rounded.Bookmark` 或 `Icons.Rounded.Star`，`label` 为 `preset_custom`），点击调用 `onPreset(customPreset)`；
  - 紧接 4 个内置预设（`RECOMMENDED`、`CHINA_5G`、`CHINA_LTE`、`ALL_ENABLED`）；
  - 末尾展示独立保存/更新 `AssistChip`：
    - `customPreset == null`：图标 `Icons.Rounded.BookmarkAdd`（或 `Icons.Rounded.Save`），文案 `preset_save_custom`，点击直接调用 `onSaveCustomPreset()`；
    - `customPreset != null`：图标 `Icons.Rounded.BookmarkAdded`（或 `Icons.Rounded.Save`），文案 `preset_update_custom`，点击将 `confirmUpdateCustomPreset = true`。
- 右上角更多菜单 `DropdownMenu`：
  - 保留 `load_saved_configuration` 与 `ims_reload`；
  - `customPreset != null` 时增加 `preset_delete_custom`，点击置 `confirmDeleteCustomPreset = true`。
- 补充 `confirmUpdateCustomPreset` 与 `confirmDeleteCustomPreset` 两个 `AlertDialog`。
- 在 `MainActivity.kt` 中收集 `imsConfigViewModel.customPreset.collectAsStateWithLifecycle()` 并传入 `ImsConfigScreen`。

- [ ] **Step 3: 同步更新 `AGENTS.md`、`README.md` 与 `README_CN.md`**

- `AGENTS.md`：简要记录单一自定义预设的存储边界（`custom_ims_preset`、不参与自动恢复、独立保存按钮、不随应用/重置自动覆写或删除）。
- `README.md` / `README_CN.md`：在预设列表里补充「我的配置 / My preset」简短说明。

- [ ] **Step 4: 全量校验构建与单元测试**

Run:
```powershell
.\gradlew.bat :app:testTensorDebugUnitTest --tests "app.mystery0.ims.tensor.model.TargetConfigTest" --stacktrace --console=plain
.\gradlew.bat :app:assembleTensorDebug :app:assembleLegacyDebug --stacktrace --console=plain
```
Expected: 单元测试与双 flavor debug 构建全部 PASS。
