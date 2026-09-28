package io.github.vvb2060.ims.model

import org.junit.Assert.*
import org.junit.Test

class TargetConfigTest {
    private fun bool(value: Boolean) = FeatureValue(value, FeatureValueType.BOOLEAN)

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
