package app.mystery0.ims.tensor.model

import app.mystery0.ims.tensor.ConfigurationRepository
import org.junit.Assert.*
import org.junit.Test

class TargetConfigTest {
    private fun bool(value: Boolean) = FeatureValue(value, FeatureValueType.BOOLEAN)
    private fun integer(value: Int) = FeatureValue(value, FeatureValueType.INTEGER)

    @Test
    fun `VoWiFi 开关和旧历史目标转换不再覆盖名称格式`() {
        val formatKeys = TargetConfigMapper.keys(Feature.VOWIFI_NAME_FORMAT)
        for (enabled in listOf(true, false)) {
            val writes = TargetConfigMapper.writes(Feature.VOWIFI, bool(enabled), emptyMap())
            assertTrue(writes.keys.intersect(formatKeys).isEmpty())
            assertEquals(enabled, writes["carrier_wfc_ims_available_bool"])
        }
        val old = legacyTargets(mapOf(Feature.VOWIFI to bool(true)))
        assertEquals(mapOf(Feature.VOWIFI to bool(true)), old)
        assertTrue(old.flatMap { (feature, value) -> TargetConfigMapper.writes(feature, value, emptyMap()).keys }
            .intersect(formatKeys).isEmpty())
    }

    @Test
    fun `名称仅在明确选择时同步语音数据与飞行模式且零是实际格式`() {
        val current = TargetConfigMapper.read(mapOf("wfc_spn_format_idx_int" to 6,
            "wfc_data_spn_format_idx_int" to 0, "wfc_flight_mode_spn_format_idx_int" to 1), 34)
        val state = ImsEditorState(subId = 1, snapshots = listOf(TargetConfigSnapshot(1, "a", current,
            recovery = mapOf(Feature.VOWIFI_NAME_FORMAT to ConfigRecovery.CHOICE))))
        assertTrue(state.edits.isEmpty())
        for (selected in listOf(0, 1, 4, 6)) {
            val staged = stageTargetEdits(state, mapOf(Feature.VOWIFI_NAME_FORMAT to integer(selected)))
            assertFalse(staged.skipped)
            assertEquals(mapOf("wfc_spn_format_idx_int" to selected,
                "wfc_data_spn_format_idx_int" to selected, "wfc_flight_mode_spn_format_idx_int" to -1),
                TargetConfigMapper.writes(Feature.VOWIFI_NAME_FORMAT,
                    staged.edits.getValue(Feature.VOWIFI_NAME_FORMAT), emptyMap()))
        }
        assertEquals(ConfigRecovery.CHOICE, TargetConfigMapper.recovery(Feature.VOWIFI_NAME_FORMAT,
            mapOf("wfc_spn_format_idx_int" to 1)))
    }

    @Test
    fun `格式辅助项漂移或缺失时保留显示但不续存旧格式历史`() {
        val feature = Feature.VOWIFI_NAME_FORMAT
        val complete: Map<String, Any> = mapOf("wfc_spn_format_idx_int" to 1,
            "wfc_data_spn_format_idx_int" to 1, "wfc_flight_mode_spn_format_idx_int" to -1,
            "carrier_ussd_method_int" to 2)
        val unrelatedEdit = mapOf(Feature.ALWAYS_SHOW_DATA_RAT_ICON to bool(true))
        val previous = mapOf(feature to integer(1), Feature.USSD_METHOD to integer(2))
        for (raw in listOf(
            complete + ("wfc_data_spn_format_idx_int" to 4),
            complete + ("wfc_flight_mode_spn_format_idx_int" to 1),
            complete - "wfc_data_spn_format_idx_int",
            complete - "wfc_flight_mode_spn_format_idx_int",
            complete + ("wfc_data_spn_format_idx_int" to "1"),
            complete + ("wfc_flight_mode_spn_format_idx_int" to -1L),
        )) {
            val snapshot = TargetConfigSnapshot(1, "a", TargetConfigMapper.read(raw, 34),
                wfcNameFormatConsistent = TargetConfigMapper.isWfcNameFormatConsistent(raw))
            assertEquals(integer(1), snapshot.values[feature])
            assertFalse(snapshot.wfcNameFormatConsistent)
            assertEquals(mapOf(Feature.USSD_METHOD to integer(2)) + unrelatedEdit,
                confirmedTargetHistory(previous, unrelatedEdit, snapshot.historyValues))
        }
        val withoutMetadata = TargetConfigSnapshot(1, "a", TargetConfigMapper.read(complete, 34))
        assertFalse(withoutMetadata.wfcNameFormatConsistent)
        assertFalse(feature in withoutMetadata.historyValues)
    }

    @Test
    fun `完整格式组合可续存历史而明确重选能重新保存漂移的格式`() {
        val feature = Feature.VOWIFI_NAME_FORMAT
        val complete: Map<String, Any> = mapOf("wfc_spn_format_idx_int" to 1,
            "wfc_data_spn_format_idx_int" to 1, "wfc_flight_mode_spn_format_idx_int" to -1)
        val snapshot = TargetConfigSnapshot(1, "a", TargetConfigMapper.read(complete, 34),
            recovery = mapOf(feature to ConfigRecovery.CHOICE),
            wfcNameFormatConsistent = TargetConfigMapper.isWfcNameFormatConsistent(complete))
        val previous = mapOf(feature to integer(1))
        assertTrue(snapshot.wfcNameFormatConsistent)
        assertEquals(previous, confirmedTargetHistory(previous, emptyMap(), snapshot.historyValues))
        assertFalse(TargetConfigMapper.isWfcNameFormatConsistent(complete - "wfc_spn_format_idx_int"))
        assertFalse(TargetConfigMapper.isWfcNameFormatConsistent(complete + mapOf(
            "wfc_spn_format_idx_int" to 9, "wfc_data_spn_format_idx_int" to 9)))
        val drifted = snapshot.copy(wfcNameFormatConsistent = false)
        val selected = stageTargetEdits(ImsEditorState(subId = 1, snapshots = listOf(drifted)), previous)
        assertFalse(selected.skipped)
        assertEquals(previous, confirmedTargetHistory(previous, selected.edits, drifted.historyValues))
    }

    @Test
    fun `预设保留名称和 USSD 草稿而只有全部开启会选中新 RAT`() {
        val manual = mapOf(Feature.VOWIFI_NAME_FORMAT to integer(4), Feature.USSD_METHOD to integer(3),
            Feature.ALWAYS_SHOW_DATA_RAT_ICON to bool(false))
        val values = Feature.entries.associateWith { feature -> FeatureValue(feature.defaultValue, feature.valueType) }
        val state = ImsEditorState(subId = 1, edits = manual, snapshots = listOf(TargetConfigSnapshot(1, "a", values,
            recovery = values.keys.associateWith { feature ->
                if (feature.valueType == FeatureValueType.INTEGER) ConfigRecovery.CHOICE else ConfigRecovery.SWITCH
            })))
        for (preset in ImsConfigPreset.entries) {
            val presetValues = imsConfigPreset(preset)
            assertFalse(Feature.VOWIFI_NAME_FORMAT in presetValues)
            assertFalse(Feature.USSD_METHOD in presetValues)
            val staged = stageTargetEdits(state, presetValues).edits
            assertEquals(manual[Feature.VOWIFI_NAME_FORMAT], staged[Feature.VOWIFI_NAME_FORMAT])
            assertEquals(manual[Feature.USSD_METHOD], staged[Feature.USSD_METHOD])
            assertEquals(bool(preset == ImsConfigPreset.ALL_ENABLED), staged[Feature.ALWAYS_SHOW_DATA_RAT_ICON])
        }
    }

    @Test
    fun `USSD 路径和 RAT 显示不联动网络模式或 IMS 开关`() {
        for (selected in 0..3) {
            assertEquals(mapOf("carrier_ussd_method_int" to selected),
                TargetConfigMapper.writes(Feature.USSD_METHOD, integer(selected), emptyMap()))
        }
        for (enabled in listOf(true, false)) {
            assertEquals(mapOf("always_show_data_rat_icon_bool" to enabled),
                TargetConfigMapper.writes(Feature.ALWAYS_SHOW_DATA_RAT_ICON, bool(enabled), emptyMap()))
        }
    }

    @Test
    fun `整数快照保留未知系统值而缺键及错误类型不补默认`() {
        val actual = TargetConfigMapper.read(mapOf("wfc_spn_format_idx_int" to 9,
            "carrier_ussd_method_int" to -1, "always_show_data_rat_icon_bool" to false), 34)
        assertEquals(mapOf(Feature.VOWIFI_NAME_FORMAT to integer(9), Feature.USSD_METHOD to integer(-1),
            Feature.ALWAYS_SHOW_DATA_RAT_ICON to bool(false)), actual)
        assertTrue(TargetConfigMapper.read(emptyMap(), 34).isEmpty())
        assertTrue(TargetConfigMapper.read(mapOf("wfc_spn_format_idx_int" to "1",
            "carrier_ussd_method_int" to 1L, "always_show_data_rat_icon_bool" to 1), 34).isEmpty())
        assertEquals(mapOf(Feature.VOWIFI to bool(true)), confirmedTargetHistory(emptyMap(),
            mapOf(Feature.VOWIFI to bool(true)), actual + (Feature.VOWIFI to bool(true))))
    }

    @Test
    fun `各卡不同的整数保持混合状态但允许用户明确批量选择`() {
        val feature = Feature.USSD_METHOD
        val state = ImsEditorState(subId = -1, snapshots = listOf(
            TargetConfigSnapshot(1, "a", mapOf(feature to integer(0)), recovery = mapOf(feature to ConfigRecovery.CHOICE)),
            TargetConfigSnapshot(2, "b", mapOf(feature to integer(9)), recovery = mapOf(feature to ConfigRecovery.CHOICE)),
        ))
        assertTrue(feature in state.supported)
        assertNull(state.common[feature])
        assertEquals(mapOf(feature to integer(1)), stageTargetEdits(state, mapOf(feature to integer(1))).edits)
        val unreadable = state.copy(snapshots = listOf(state.snapshots.first(), state.snapshots.last().copy(values = emptyMap())))
        assertTrue(stageTargetEdits(unreadable, mapOf(feature to integer(1))).skipped)
    }

    @Test
    fun `未知选项与伪装数据类型不能进入草稿或特权写入`() {
        for ((feature, bad) in listOf(
            Feature.VOWIFI_NAME_FORMAT to integer(2),
            Feature.USSD_METHOD to integer(4),
            Feature.USSD_METHOD to FeatureValue("1", FeatureValueType.INTEGER),
            Feature.VOWIFI_NAME_FORMAT to bool(true),
            Feature.ALWAYS_SHOW_DATA_RAT_ICON to FeatureValue(1, FeatureValueType.BOOLEAN),
        )) {
            val current = if (feature.valueType == FeatureValueType.BOOLEAN) bool(false) else integer(0)
            val state = ImsEditorState(subId = 1, snapshots = listOf(TargetConfigSnapshot(1, "a",
                mapOf(feature to current), recovery = mapOf(feature to TargetConfigMapper.recovery(feature, emptyMap())))))
            val staged = stageTargetEdits(state, mapOf(feature to bad))
            assertTrue(staged.skipped)
            assertTrue(staged.edits.isEmpty())
            assertThrows(IllegalArgumentException::class.java) { TargetConfigMapper.writes(feature, bad, emptyMap()) }
        }
    }

    @Test
    fun `旧历史既不补新增选项也不迁移旧协议未写过的值`() {
        val newFeatures = setOf(Feature.VOWIFI_NAME_FORMAT, Feature.USSD_METHOD, Feature.ALWAYS_SHOW_DATA_RAT_ICON)
        assertTrue(ConfigurationRepository.historyFeatures(1, setOf(Feature.VOWIFI.name))
            .intersect(newFeatures).isEmpty())
        assertTrue(ConfigurationRepository.historyFeatures(1, newFeatures.map { it.name }.toSet()).isEmpty())
        assertEquals(mapOf(Feature.VOWIFI to bool(true)), legacyTargets(mapOf(
            Feature.VOWIFI to bool(true), Feature.VOWIFI_NAME_FORMAT to integer(6),
            Feature.USSD_METHOD to integer(1), Feature.ALWAYS_SHOW_DATA_RAT_ICON to bool(true),
        )))
    }

    @Test
    fun `新历史只读取明确保存的整数而不会补齐另一选项`() {
        assertEquals(listOf(Feature.VOWIFI_NAME_FORMAT),
            ConfigurationRepository.historyFeatures(2, setOf(Feature.VOWIFI_NAME_FORMAT.name, "_config_version")))
        assertEquals(listOf(Feature.USSD_METHOD),
            ConfigurationRepository.historyFeatures(2, setOf(Feature.USSD_METHOD.name)))
    }

    @Test
    fun `显示相同仍保留预设和恢复默认的完整目标`() {
        val values = mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_THRESHOLDS to bool(false))
        val state = ImsEditorState(subId = 1, snapshots = listOf(TargetConfigSnapshot(1, "a", values,
            recovery = mapOf(Feature.FIVE_G_NR to ConfigRecovery.SWITCH,
                Feature.FIVE_G_THRESHOLDS to ConfigRecovery.SYSTEM_DEFAULT))))
        assertEquals(values, stageTargetEdits(state, values).edits)
    }

    @Test
    fun `重读刷新系统基线并保留同卡草稿而换卡丢弃`() {
        val snapshot = TargetConfigSnapshot(1, "a", mapOf(Feature.VONR to bool(true), Feature.VOLTE to bool(true)),
            recovery = mapOf(Feature.VONR to ConfigRecovery.SWITCH, Feature.VOLTE to ConfigRecovery.SWITCH))
        val previous = ImsEditorState(subId = 1, snapshots = listOf(snapshot), edits = mapOf(Feature.VONR to bool(false)))
        val changed = snapshot.copy(values = mapOf(Feature.VONR to bool(true), Feature.VOLTE to bool(false)))
        val refreshed = refreshedTargetState(previous, listOf(changed))
        assertEquals(previous.edits, refreshed.edits)
        assertEquals(bool(false), refreshed.values[Feature.VOLTE])
        assertTrue(refreshedTargetState(previous, listOf(changed.copy(identity = "b"))).edits.isEmpty())
        assertTrue(refreshedTargetState(previous, listOf(changed.copy(values = emptyMap()))).edits.isEmpty())
        val failed = refreshedTargetState(previous, listOf(TargetConfigSnapshot(1, error = "Unavailable")))
        assertEquals(previous.edits, failed.edits)
        assertFalse(failed.ready)
        assertEquals(previous.edits, refreshedTargetState(failed, listOf(changed)).edits)
    }

    @Test
    fun `关闭语音功能明确下发 false 但不隐藏 VoNR 入口`() {
        assertEquals(mapOf("vonr_enabled_bool" to false), TargetConfigMapper.disabledValues(Feature.VONR))
        assertEquals(mapOf("carrier_default_wfc_ims_roaming_enabled_bool" to false),
            TargetConfigMapper.disabledValues(Feature.VOWIFI_ROAMING))
    }

    @Test
    fun `仅支持一种 NR 模式也显示开启而空数组表示关闭`() {
        for (modes in listOf(intArrayOf(1), intArrayOf(2))) {
            assertEquals(bool(true), TargetConfigMapper.read(mapOf("carrier_nr_availabilities_int_array" to modes), 34)[Feature.FIVE_G_NR])
        }
        assertEquals(bool(false), TargetConfigMapper.read(mapOf("carrier_nr_availabilities_int_array" to intArrayOf()), 34)[Feature.FIVE_G_NR])
    }

    @Test
    fun `缺键与不支持的 VoNR 不被推断成默认值`() {
        assertTrue(TargetConfigMapper.read(emptyMap(), 34).isEmpty())
        assertNull(TargetConfigMapper.read(mapOf("vonr_enabled_bool" to true), 33)[Feature.VONR])
    }

    @Test
    fun `旧历史的 false 和未实际写入的 NR 优化不迁移为目标`() {
        val targets = legacyTargets(mapOf(Feature.VONR to bool(false), Feature.VOLTE to bool(true),
            Feature.FIVE_G_NR to bool(false), Feature.FIVE_G_PLUS_ICON to bool(true)))
        assertEquals(mapOf(Feature.VOLTE to bool(true)), targets)
    }

    @Test
    fun `混合值和缺失值不冒充共同关闭值`() {
        assertTrue(commonValues(listOf(mapOf(Feature.VONR to bool(true)), mapOf(Feature.VONR to bool(false)))).isEmpty())
        assertTrue(commonValues(listOf(mapOf(Feature.VONR to bool(false)), emptyMap())).isEmpty())
        assertEquals(mapOf(Feature.VONR to bool(false)), commonValues(listOf(mapOf(Feature.VONR to bool(false)), mapOf(Feature.VONR to bool(false)))))
    }

    @Test
    fun `Enhanced LTE 不再覆盖独立的 LTE 加号设置`() {
        assertFalse(TargetConfigMapper.enabledValues(Feature.ENHANCED_4G_LTE).containsKey("hide_lte_plus_data_icon_bool"))
    }

    @Test
    fun `参数恢复只接受完整且类型正确的系统默认值`() {
        val key = "5g_nr_ssrsrp_thresholds_int_array"
        val defaults = mapOf(key to intArrayOf(-110, -90, -80, -65))
        assertEquals(ConfigRecovery.SYSTEM_DEFAULT, TargetConfigMapper.recovery(Feature.FIVE_G_THRESHOLDS, defaults))
        assertEquals(ConfigRecovery.RESET_REQUIRED, TargetConfigMapper.recovery(Feature.FIVE_G_THRESHOLDS, emptyMap()))
        assertEquals(ConfigRecovery.RESET_REQUIRED, TargetConfigMapper.recovery(Feature.FIVE_G_THRESHOLDS, mapOf(key to false)))
        val writes = TargetConfigMapper.writes(Feature.FIVE_G_THRESHOLDS, bool(false), defaults)
        assertArrayEquals(intArrayOf(-110, -90, -80, -65), writes[key] as IntArray)
    }

    @Test
    fun `字符串即使有系统默认值也要求重置而不能清空伪恢复`() {
        assertEquals(ConfigRecovery.RESET_REQUIRED, TargetConfigMapper.recovery(Feature.IMS_USER_AGENT,
            mapOf("ims.ims_user_agent_string" to "Default")))
        assertThrows(IllegalArgumentException::class.java) {
            TargetConfigMapper.writes(Feature.IMS_USER_AGENT, FeatureValue("", FeatureValueType.STRING), emptyMap())
        }
    }

    @Test
    fun `Enhanced LTE 关闭生成三个布尔目标且不干预图标`() {
        assertEquals(mapOf("editable_enhanced_4g_lte_bool" to false,
            "enhanced_4g_lte_on_by_default_bool" to false, "hide_enhanced_4g_lte_bool" to true),
            TargetConfigMapper.writes(Feature.ENHANCED_4G_LTE, bool(false), emptyMap()))
    }

    @Test
    fun `旧 Enhanced LTE 隐式显示 LTE 加号的意图也必须保留`() {
        assertEquals(bool(false), legacyTargets(mapOf(Feature.ENHANCED_4G_LTE to bool(true),
            Feature.HIDE_LTE_PLUS_DATA_ICON to bool(false)))[Feature.HIDE_LTE_PLUS_DATA_ICON])
    }

    @Test
    fun `混合状态只有用户明确编辑的项目会进入下发目标`() {
        val state = ImsEditorState(subId = -1, snapshots = listOf(
            TargetConfigSnapshot(1, "a", mapOf(Feature.VONR to bool(true), Feature.VOLTE to bool(true)),
                recovery = mapOf(Feature.VONR to ConfigRecovery.SWITCH, Feature.VOLTE to ConfigRecovery.SWITCH)),
            TargetConfigSnapshot(2, "b", mapOf(Feature.VONR to bool(false), Feature.VOLTE to bool(true)),
                recovery = mapOf(Feature.VONR to ConfigRecovery.SWITCH, Feature.VOLTE to ConfigRecovery.SWITCH)),
        ))
        val edited = stageTargetEdits(state, mapOf(Feature.VONR to bool(false)))
        assertEquals(mapOf(Feature.VONR to bool(false)), edited.edits)
        assertFalse(edited.skipped)
    }

    @Test
    fun `预设不能把无法恢复的项目关闭但可以撤销未应用的开启草稿`() {
        val feature = Feature.FIVE_G_PLUS_ICON
        val on = ImsEditorState(snapshots = listOf(TargetConfigSnapshot(1, "a", mapOf(feature to bool(true)),
            recovery = mapOf(feature to ConfigRecovery.RESET_REQUIRED))))
        assertTrue(stageTargetEdits(on, mapOf(feature to bool(false))).skipped)
        assertTrue(stageTargetEdits(on, mapOf(feature to bool(false))).edits.isEmpty())
        val pending = on.copy(snapshots = listOf(on.snapshots.single().copy(values = mapOf(feature to bool(false)))),
            edits = mapOf(feature to bool(true)))
        assertTrue(stageTargetEdits(pending, mapOf(feature to bool(false))).edits.isEmpty())
    }

    @Test
    fun `旧历史合并仅保留回读仍一致的项目而新 false 保持禁用含义`() {
        assertEquals(mapOf(Feature.VONR to bool(false)), confirmedTargetHistory(
            mapOf(Feature.VOLTE to bool(true)), mapOf(Feature.VONR to bool(false)),
            mapOf(Feature.VOLTE to bool(false), Feature.VONR to bool(false))))
    }
}
