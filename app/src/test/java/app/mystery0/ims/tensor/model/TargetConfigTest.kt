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

    private val nrKey = "carrier_nr_availabilities_int_array"
    private fun nrWrites(targets: Map<Feature, FeatureValue>, current: Map<Feature, FeatureValue> = emptyMap()) =
        TargetConfigMapper.targetWrites(targets, current, emptyMap())[nrKey] as IntArray?

    @Test
    fun `5G NR 开启时按模式写入数组而关闭时无论模式都写空数组`() {
        val expected = mapOf(0 to intArrayOf(1, 2), 1 to intArrayOf(2), 2 to intArrayOf(1))
        for ((mode, array) in expected) {
            assertArrayEquals(array, nrWrites(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(mode))))
            assertArrayEquals(intArrayOf(), nrWrites(mapOf(Feature.FIVE_G_NR to bool(false), Feature.FIVE_G_NR_MODE to integer(mode))))
            // 目标未含开关时沿用当前实际开关：开启则改模式，关闭则不能重新开启 5G。
            assertArrayEquals(array, nrWrites(mapOf(Feature.FIVE_G_NR_MODE to integer(mode)),
                mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(0))))
            assertArrayEquals(intArrayOf(), nrWrites(mapOf(Feature.FIVE_G_NR_MODE to integer(mode)),
                mapOf(Feature.FIVE_G_NR to bool(false), Feature.FIVE_G_NR_MODE to integer(-1))))
        }
    }

    @Test
    fun `未指定模式的 5G 开启与旧版及旧历史完全一致`() {
        for (current in listOf(emptyMap(), mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1)))) {
            assertArrayEquals(intArrayOf(1, 2), nrWrites(mapOf(Feature.FIVE_G_NR to bool(true)), current))
        }
        assertArrayEquals(intArrayOf(), nrWrites(mapOf(Feature.FIVE_G_NR to bool(false))))
        assertArrayEquals(intArrayOf(1, 2), TargetConfigMapper.writes(Feature.FIVE_G_NR, bool(true), emptyMap())[nrKey] as IntArray)
        // 不涉及 NR 的目标不写数组，其他项目照常映射。
        assertEquals(mapOf("carrier_volte_available_bool" to true),
            TargetConfigMapper.targetWrites(mapOf(Feature.VOLTE to bool(true)), emptyMap(), emptyMap()))
        assertThrows(IllegalStateException::class.java) {
            TargetConfigMapper.writes(Feature.FIVE_G_NR_MODE, integer(1), emptyMap())
        }
        for (bad in listOf(integer(3), integer(-1), bool(true))) {
            assertThrows(IllegalArgumentException::class.java) {
                nrWrites(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to bad))
            }
        }
    }

    @Test
    fun `NR 数组读取为开关和模式且未知组合原样保留`() {
        fun read(vararg modes: Int) = TargetConfigMapper.read(mapOf(nrKey to modes), 34)
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(false), Feature.FIVE_G_NR_MODE to integer(-1)), read())
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(0)), read(1, 2))
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(0)), read(2, 1))
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1)), read(2))
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(2)), read(1))
        val other = read(1, 2, 3).getValue(Feature.FIVE_G_NR_MODE).data as Int
        assertEquals(bool(true), read(1, 2, 3)[Feature.FIVE_G_NR])
        assertFalse(CarrierConfigChoices.isSelectable(Feature.FIVE_G_NR_MODE, other))
        assertEquals(listOf(1, 2, 3), TargetConfigMapper.unknownNrAvailabilities(other))
        assertEquals(listOf(3), TargetConfigMapper.unknownNrAvailabilities(read(3).getValue(Feature.FIVE_G_NR_MODE).data as Int))
        assertEquals(bool(false), read(3)[Feature.FIVE_G_NR])
        assertEquals(integer(TargetConfigMapper.NR_MODE_UNREPRESENTABLE), read(1, 99)[Feature.FIVE_G_NR_MODE])
        assertNull(TargetConfigMapper.unknownNrAvailabilities(TargetConfigMapper.NR_MODE_UNREPRESENTABLE))
        assertTrue(TargetConfigMapper.read(emptyMap(), 34).isEmpty())
        assertTrue(TargetConfigMapper.read(mapOf(nrKey to listOf(1, 2)), 34).isEmpty())
    }

    @Test
    fun `NR 回读不区分顺序而其他数组参数仍按顺序比较`() {
        assertTrue(TargetConfigMapper.matches(mapOf(nrKey to intArrayOf(2, 1)), mapOf(nrKey to intArrayOf(1, 2))))
        assertFalse(TargetConfigMapper.matches(mapOf(nrKey to intArrayOf(2)), mapOf(nrKey to intArrayOf(1, 2))))
        assertFalse(TargetConfigMapper.matches(mapOf(nrKey to intArrayOf(1, 2, 2)), mapOf(nrKey to intArrayOf(1, 2))))
        assertFalse(TargetConfigMapper.matches(emptyMap(), mapOf(nrKey to intArrayOf())))
        val thresholds = "5g_nr_ssrsrp_thresholds_int_array"
        assertFalse(TargetConfigMapper.matches(mapOf(thresholds to intArrayOf(-98, -108, -118, -128)),
            mapOf(thresholds to intArrayOf(-128, -118, -108, -98))))
    }

    @Test
    fun `各卡 NR 模式不同显示混合且明确选择可进入草稿`() {
        val feature = Feature.FIVE_G_NR_MODE
        val state = ImsEditorState(subId = -1, snapshots = listOf(1 to intArrayOf(1, 2), 2 to intArrayOf(2)).map { (id, modes) ->
            val values = TargetConfigMapper.read(mapOf(nrKey to modes), 34)
            TargetConfigSnapshot(id, "s$id", values, recovery = values.keys.associateWith { TargetConfigMapper.recovery(it, emptyMap()) })
        })
        assertTrue(feature in state.supported)
        assertNull(state.common[feature])
        assertEquals(bool(true), state.common[Feature.FIVE_G_NR])
        assertEquals(ConfigRecovery.CHOICE, state.recovery(feature))
        val staged = stageTargetEdits(state, mapOf(feature to integer(1)))
        assertFalse(staged.skipped)
        assertEquals(mapOf(feature to integer(1)), staged.edits)
        assertTrue(stageTargetEdits(state, mapOf(feature to integer(3))).skipped)
        assertTrue(stageTargetEdits(state, mapOf(feature to integer(-1))).skipped)
    }

    @Test
    fun `旧历史不补 NR 模式且开启 5G 仍恢复为 NSA 加 SA`() {
        assertFalse(Feature.FIVE_G_NR_MODE in ConfigurationRepository.historyFeatures(1, setOf(Feature.FIVE_G_NR.name)))
        assertTrue(ConfigurationRepository.historyFeatures(1, setOf(Feature.FIVE_G_NR_MODE.name)).isEmpty())
        val old = legacyTargets(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1)))
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true)), old)
        assertArrayEquals(intArrayOf(1, 2), nrWrites(old, mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1))))
    }

    @Test
    fun `新历史保存 NR 模式并在回读一致时续存及恢复`() {
        assertEquals(listOf(Feature.FIVE_G_NR, Feature.FIVE_G_NR_MODE), ConfigurationRepository.historyFeatures(2,
            setOf(Feature.FIVE_G_NR_MODE.name, Feature.FIVE_G_NR.name, "_config_version")))
        val saved = confirmedTargetHistory(emptyMap(), mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1)),
            TargetConfigMapper.read(mapOf(nrKey to intArrayOf(2)), 34))
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1)), saved)
        assertEquals(saved, confirmedTargetHistory(saved, emptyMap(), TargetConfigMapper.read(mapOf(nrKey to intArrayOf(2)), 34)))
        // 外部改回 NSA + SA 后，旧模式不再续存，开关仍一致则保留。
        assertEquals(mapOf(Feature.FIVE_G_NR to bool(true)),
            confirmedTargetHistory(saved, emptyMap(), TargetConfigMapper.read(mapOf(nrKey to intArrayOf(1, 2)), 34)))
        // 自动恢复：当前 5G 关闭的卡也能按历史开启并写入仅 SA。
        val off = TargetConfigMapper.read(mapOf(nrKey to intArrayOf()), 34)
        assertTrue(saved.keys.all { it in off })
        assertArrayEquals(intArrayOf(2), nrWrites(saved, off))
    }

    @Test
    fun `预设沿用已知 NR 模式而全部开启不自行选择模式`() {
        val sa = mapOf(Feature.FIVE_G_NR to bool(true), Feature.FIVE_G_NR_MODE to integer(1))
        for (preset in ImsConfigPreset.entries) {
            assertFalse(Feature.FIVE_G_NR_MODE in imsConfigPreset(preset))
            val targets = imsConfigPreset(preset, sa)
            val nrOn = imsConfigPreset(preset)[Feature.FIVE_G_NR] == bool(true)
            assertEquals(if (nrOn) integer(1) else null, targets[Feature.FIVE_G_NR_MODE])
            // 只核对共用 NR 键；预设中其他参数项的恢复需要系统默认值，不属于此测试。
            assertArrayEquals(if (nrOn) intArrayOf(2) else intArrayOf(),
                nrWrites(targets.filterKeys { it == Feature.FIVE_G_NR || it == Feature.FIVE_G_NR_MODE }, sa))
        }
        // 当前关闭或未知组合时不附带模式，开启按缺省 NSA + SA 写入。
        for (mode in listOf(-1, (1 shl 3).inv())) {
            val targets = imsConfigPreset(ImsConfigPreset.ALL_ENABLED,
                mapOf(Feature.FIVE_G_NR to bool(false), Feature.FIVE_G_NR_MODE to integer(mode)))
            assertFalse(Feature.FIVE_G_NR_MODE in targets)
            assertArrayEquals(intArrayOf(1, 2), nrWrites(targets.filterKeys { it == Feature.FIVE_G_NR }))
        }
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
