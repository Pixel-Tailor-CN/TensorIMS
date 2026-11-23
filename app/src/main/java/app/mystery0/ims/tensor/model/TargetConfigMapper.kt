package app.mystery0.ims.tensor.model

/**
 * CarrierConfig 键集中保留字符串形式，覆盖公开和隐藏键；Android 13 缺少稳定 VoNR 支持。
 * 新目标协议和旧版仅启用协议分开，避免升级后将旧 false 解释为禁用。
 */
object TargetConfigMapper {
    private val primaryKeys = mapOf(
        Feature.VOLTE to "carrier_volte_available_bool",
        Feature.VOWIFI to "carrier_wfc_ims_available_bool",
        Feature.VOWIFI_ROAMING to "carrier_default_wfc_ims_roaming_enabled_bool",
        Feature.VT to "carrier_vt_available_bool",
        Feature.VONR to "vonr_enabled_bool",
        Feature.CROSS_SIM to "carrier_cross_sim_ims_available_bool",
        Feature.UT to "carrier_supports_ss_over_ut_bool",
        Feature.HIDE_LTE_PLUS_DATA_ICON to "hide_lte_plus_data_icon_bool",
        Feature.SHOW_4G_FOR_LTE to "show_4g_for_lte_data_icon_bool",
        Feature.ALWAYS_SHOW_DATA_RAT_ICON to "always_show_data_rat_icon_bool",
    )
    const val NR_KEY = "carrier_nr_availabilities_int_array"
    private const val NR_NSA = 1
    private const val NR_SA = 2
    /** 无法用位掩码表示的未知 NR 数组（元素超出 0..30）。 */
    const val NR_MODE_UNREPRESENTABLE = Int.MIN_VALUE
    const val NAME_ENABLED = "carrier_name_override_bool"
    const val NAME = "carrier_name_string"
    const val USER_AGENT = "ims.ims_user_agent_string"
    const val WFC_NAME_FORMAT = "wfc_spn_format_idx_int"
    const val WFC_DATA_NAME_FORMAT = "wfc_data_spn_format_idx_int"
    const val WFC_FLIGHT_MODE_NAME_FORMAT = "wfc_flight_mode_spn_format_idx_int"
    const val USSD_METHOD = "carrier_ussd_method_int"

    private val choiceKeys = mapOf(
        Feature.VOWIFI_NAME_FORMAT to WFC_NAME_FORMAT,
        Feature.USSD_METHOD to USSD_METHOD,
    )

    /**
     * 5G NR 模式编号：0 = NSA + SA [1, 2]（默认，与旧版开启 5G NR 写入一致），1 = 仅 SA [2]，2 = 仅 NSA [1]。
     * 0 保持旧行为，便于缺省模式与旧历史等价；1、2 按“收窄到 SA / 收窄到 NSA”排列，不对应系统常量值。
     */
    val NR_MODES: Map<Int, IntArray> = linkedMapOf(
        0 to intArrayOf(NR_NSA, NR_SA),
        1 to intArrayOf(NR_SA),
        2 to intArrayOf(NR_NSA),
    )

    val parameters = setOf(Feature.FIVE_G_THRESHOLDS, Feature.FIVE_G_PLUS_ICON)

    fun enabledValues(feature: Feature): Map<String, Any> = when (feature) {
        Feature.VOWIFI -> mapOf(
            primaryKeys.getValue(feature) to true,
            "carrier_wfc_supports_wifi_only_bool" to true,
            "editable_wfc_mode_bool" to true,
            "editable_wfc_roaming_mode_bool" to true,
            "show_wifi_calling_icon_in_status_bar_bool" to true,
        )
        Feature.VONR -> mapOf(primaryKeys.getValue(feature) to true, "vonr_setting_visibility_bool" to true)
        Feature.CROSS_SIM -> mapOf(primaryKeys.getValue(feature) to true,
            "enable_cross_sim_calling_on_opportunistic_data_bool" to true)
        Feature.FIVE_G_NR -> mapOf(NR_KEY to intArrayOf(1, 2))
        Feature.FIVE_G_THRESHOLDS -> mapOf("5g_nr_ssrsrp_thresholds_int_array" to intArrayOf(-128, -118, -108, -98))
        Feature.FIVE_G_PLUS_ICON -> mapOf(
            FiveGPlusConfig.KEY_NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ to FiveGPlusConfig.NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ,
            FiveGPlusConfig.KEY_INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD_BANDWIDTH to false,
            FiveGPlusConfig.KEY_ADDITIONAL_NR_ADVANCED_BANDS to FiveGPlusConfig.additionalNrAdvancedBands,
            FiveGPlusConfig.KEY_5G_ICON_CONFIGURATION to FiveGPlusConfig.NR_ICON_CONFIGURATION,
            FiveGPlusConfig.KEY_NR_ADVANCED_CAPABLE_PCO_ID to 0,
        )
        Feature.ENHANCED_4G_LTE -> mapOf("editable_enhanced_4g_lte_bool" to true,
            "enhanced_4g_lte_on_by_default_bool" to true, "hide_enhanced_4g_lte_bool" to false)
        Feature.CARRIER_NAME, Feature.IMS_USER_AGENT, Feature.VOWIFI_NAME_FORMAT, Feature.USSD_METHOD,
        Feature.FIVE_G_NR_MODE -> emptyMap()
        else -> mapOf(primaryKeys.getValue(feature) to true)
    }

    fun disabledValues(feature: Feature): Map<String, Any> = when (feature) {
        Feature.FIVE_G_NR -> mapOf(NR_KEY to intArrayOf())
        Feature.ENHANCED_4G_LTE -> enabledValues(feature).mapValues { (_, value) -> !(value as Boolean) }
        else -> primaryKeys[feature]?.let { mapOf(it to false) }.orEmpty()
    }

    fun keys(feature: Feature): Set<String> = when (feature) {
        Feature.CARRIER_NAME -> setOf(NAME_ENABLED, NAME)
        Feature.IMS_USER_AGENT -> setOf(USER_AGENT)
        Feature.VOWIFI_NAME_FORMAT -> setOf(WFC_NAME_FORMAT, WFC_DATA_NAME_FORMAT, WFC_FLIGHT_MODE_NAME_FORMAT)
        Feature.USSD_METHOD -> setOf(USSD_METHOD)
        Feature.FIVE_G_NR_MODE -> setOf(NR_KEY)
        else -> enabledValues(feature).keys
    }

    fun equivalent(first: Any?, second: Any?): Boolean = when {
        first is IntArray && second is IntArray -> first.contentEquals(second)
        else -> first == second
    }

    /** NR 可用性数组只表达集合语义，[2, 1] 与 [1, 2] 视为一致；其他数组参数保持顺序敏感。 */
    fun matches(values: Map<String, Any>, expected: Map<String, Any>): Boolean =
        expected.all { (key, value) ->
            val actual = values[key]
            if (key == NR_KEY && actual is IntArray && value is IntArray) actual.sorted() == value.sorted()
            else equivalent(actual, value)
        }

    /**
     * 实际数组映射为模式：已知组合不区分顺序；其他组合不强行归入选项，以负数位掩码（按位取反）原样保留元素，
     * 空数组为 -1，元素超出 0..30 时为 [NR_MODE_UNREPRESENTABLE]。负数永远不是可选值，不会进入写入目标。
     */
    fun nrModeOf(availabilities: IntArray): Int {
        val sorted = availabilities.sorted()
        NR_MODES.entries.firstOrNull { it.value.sorted() == sorted }?.let { return it.key }
        if (availabilities.any { it !in 0..30 }) return NR_MODE_UNREPRESENTABLE
        return availabilities.fold(0) { mask, mode -> mask or (1 shl mode) }.inv()
    }

    /** 未知模式还原为去重排序后的数组元素，供界面原样展示；已知选项或无法表示时返回 null。 */
    fun unknownNrAvailabilities(mode: Int): List<Int>? {
        if (mode >= 0 || mode == NR_MODE_UNREPRESENTABLE) return null
        val mask = mode.inv()
        return (0..30).filter { mask and (1 shl it) != 0 }
    }

    /**
     * 5G NR 开关与模式共用一个键，必须按整体目标计算：目标未包含开关时沿用当前实际开关；
     * 关闭时始终写空数组，模式不能重新开启 5G；开启且未指定模式时写 [1, 2]，与旧版及旧历史完全一致。
     */
    fun targetWrites(
        targets: Map<Feature, FeatureValue>,
        current: Map<Feature, FeatureValue>,
        defaults: Map<String, Any>,
    ): Map<String, Any> = buildMap {
        targets.forEach { (feature, value) ->
            if (feature != Feature.FIVE_G_NR && feature != Feature.FIVE_G_NR_MODE) putAll(writes(feature, value, defaults))
        }
        if (Feature.FIVE_G_NR !in targets && Feature.FIVE_G_NR_MODE !in targets) return@buildMap
        targets[Feature.FIVE_G_NR]?.let {
            require(CarrierConfigChoices.isWellTyped(Feature.FIVE_G_NR, it)) { "Invalid target type: FIVE_G_NR" }
        }
        val mode = targets[Feature.FIVE_G_NR_MODE]?.let {
            require(CarrierConfigChoices.isValidTarget(Feature.FIVE_G_NR_MODE, it)) { "Invalid target choice: FIVE_G_NR_MODE" }
            it.data as Int
        }
        val enabled = (targets[Feature.FIVE_G_NR] ?: current[Feature.FIVE_G_NR])?.data == true
        put(NR_KEY, if (!enabled) intArrayOf() else NR_MODES.getValue(mode ?: 0).copyOf())
    }

    /** 历史格式代表三个键的组合，不能仅凭语音侧相同就认定完整目标仍然成立。 */
    fun isWfcNameFormatConsistent(values: Map<String, Any>): Boolean {
        val selected = values[WFC_NAME_FORMAT] as? Int ?: return false
        return CarrierConfigChoices.isSelectable(Feature.VOWIFI_NAME_FORMAT, selected) &&
            (values[WFC_DATA_NAME_FORMAT] as? Int) == selected &&
            (values[WFC_FLIGHT_MODE_NAME_FORMAT] as? Int) == -1
    }

    fun recovery(feature: Feature, defaults: Map<String, Any>): ConfigRecovery = when {
        feature.valueType == FeatureValueType.STRING -> ConfigRecovery.RESET_REQUIRED
        feature.valueType == FeatureValueType.INTEGER -> ConfigRecovery.CHOICE
        feature !in parameters -> ConfigRecovery.SWITCH
        enabledValues(feature).all { (key, value) -> defaults[key]?.javaClass == value.javaClass } &&
            !matches(defaults, enabledValues(feature)) -> ConfigRecovery.SYSTEM_DEFAULT
        else -> ConfigRecovery.RESET_REQUIRED
    }

    fun writes(feature: Feature, value: FeatureValue, defaults: Map<String, Any>): Map<String, Any> {
        require(CarrierConfigChoices.isWellTyped(feature, value)) { "Invalid target type: ${feature.name}" }
        if (feature.valueType == FeatureValueType.INTEGER) {
            val selected = value.data as Int
            require(CarrierConfigChoices.isSelectable(feature, selected)) { "Invalid target choice: ${feature.name}" }
            return when (feature) {
                // 模式单独无法决定写入值，必须与开关一起经 targetWrites 计算。
                Feature.FIVE_G_NR_MODE -> error("FIVE_G_NR_MODE must be written through targetWrites")
                // 只在明确选择格式时同步数据侧，飞行模式沿用普通格式；不修改语言区域配置。
                Feature.VOWIFI_NAME_FORMAT -> mapOf(WFC_NAME_FORMAT to selected,
                    WFC_DATA_NAME_FORMAT to selected, WFC_FLIGHT_MODE_NAME_FORMAT to -1)
                Feature.USSD_METHOD -> mapOf(USSD_METHOD to selected)
                else -> error("Unsupported integer feature")
            }
        }
        if (feature.valueType == FeatureValueType.STRING) {
            val text = (value.data as String).trim()
            require(text.isNotEmpty()) { "Reset carrier configuration to restore ${feature.name}" }
            return when (feature) {
                Feature.CARRIER_NAME -> mapOf(NAME_ENABLED to true, NAME to text)
                Feature.IMS_USER_AGENT -> mapOf(USER_AGENT to text)
                else -> error("Unsupported string feature")
            }
        }
        if (value.data as Boolean) return enabledValues(feature)
        return when (recovery(feature, defaults)) {
            ConfigRecovery.SWITCH -> disabledValues(feature)
            ConfigRecovery.SYSTEM_DEFAULT -> defaults.filterKeys { it in keys(feature) }
            ConfigRecovery.RESET_REQUIRED -> error("Reset carrier configuration to restore ${feature.name}")
            ConfigRecovery.CHOICE -> error("An explicit choice is required for ${feature.name}")
        }
    }

    fun read(values: Map<String, Any>, sdk: Int): Map<Feature, FeatureValue> = buildMap {
        primaryKeys.forEach { (feature, key) ->
            if (feature != Feature.VONR || sdk >= 34) {
                (values[key] as? Boolean)?.let { put(feature, FeatureValue(it, FeatureValueType.BOOLEAN)) }
            }
        }
        choiceKeys.forEach { (feature, key) ->
            // 系统扩展值保持原样供界面展示，缺键和错误类型均保留为未知。
            (values[key] as? Int)?.let { put(feature, FeatureValue(it, FeatureValueType.INTEGER)) }
        }
        (values[NR_KEY] as? IntArray)?.let {
            put(Feature.FIVE_G_NR, FeatureValue(it.any { mode -> mode == NR_NSA || mode == NR_SA }, FeatureValueType.BOOLEAN))
            // 模式随数组一起可读；关闭（空数组）时也保留实际值，使开关关闭的卡仍能接受自动恢复的模式目标。
            put(Feature.FIVE_G_NR_MODE, FeatureValue(nrModeOf(it), FeatureValueType.INTEGER))
        }
        for (feature in parameters + Feature.ENHANCED_4G_LTE) {
            val expected = enabledValues(feature)
            if (expected.all { (key, value) -> values[key]?.javaClass == value.javaClass }) {
                put(feature, FeatureValue(matches(values, expected), FeatureValueType.BOOLEAN))
            }
        }
        (values[NAME_ENABLED] as? Boolean)?.let { enabled ->
            val name = if (!enabled) "" else values[NAME] as? String
            name?.let { put(Feature.CARRIER_NAME, FeatureValue(it, FeatureValueType.STRING)) }
        }
        (values[USER_AGENT] as? String)?.let { put(Feature.IMS_USER_AGENT, FeatureValue(it, FeatureValueType.STRING)) }
    }
}
