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
    )
    const val NR_KEY = "carrier_nr_availabilities_int_array"
    const val NAME_ENABLED = "carrier_name_override_bool"
    const val NAME = "carrier_name_string"
    const val USER_AGENT = "ims.ims_user_agent_string"

    val parameters = setOf(Feature.FIVE_G_THRESHOLDS, Feature.FIVE_G_PLUS_ICON)

    fun enabledValues(feature: Feature): Map<String, Any> = when (feature) {
        Feature.VOWIFI -> mapOf(
            primaryKeys.getValue(feature) to true,
            "carrier_wfc_supports_wifi_only_bool" to true,
            "editable_wfc_mode_bool" to true,
            "editable_wfc_roaming_mode_bool" to true,
            "show_wifi_calling_icon_in_status_bar_bool" to true,
            "wfc_spn_format_idx_int" to 6,
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
        Feature.CARRIER_NAME, Feature.IMS_USER_AGENT, Feature.TIKTOK_NETWORK_FIX -> emptyMap()
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
        Feature.TIKTOK_NETWORK_FIX -> setOf(TikTokNetworkFix.COUNTRY_ISO_KEY)
        else -> enabledValues(feature).keys
    }

    fun equivalent(first: Any?, second: Any?): Boolean = when {
        first is IntArray && second is IntArray -> first.contentEquals(second)
        else -> first == second
    }

    fun matches(values: Map<String, Any>, expected: Map<String, Any>): Boolean =
        expected.all { (key, value) -> equivalent(values[key], value) }

    fun recovery(feature: Feature, defaults: Map<String, Any>): ConfigRecovery = when {
        feature.valueType == FeatureValueType.STRING -> ConfigRecovery.RESET_REQUIRED
        feature !in parameters -> ConfigRecovery.SWITCH
        enabledValues(feature).all { (key, value) -> defaults[key]?.javaClass == value.javaClass } &&
            !matches(defaults, enabledValues(feature)) -> ConfigRecovery.SYSTEM_DEFAULT
        else -> ConfigRecovery.RESET_REQUIRED
    }

    fun writes(feature: Feature, value: FeatureValue, defaults: Map<String, Any>): Map<String, Any> {
        require(value.valueType == feature.valueType) { "Invalid target type: ${feature.name}" }
        if (feature.valueType == FeatureValueType.STRING) {
            val text = (value.data as String).trim()
            require(text.isNotEmpty()) { "Reset carrier configuration to restore ${feature.name}" }
            return when (feature) {
                Feature.CARRIER_NAME -> mapOf(NAME_ENABLED to true, NAME to text)
                Feature.IMS_USER_AGENT -> mapOf(USER_AGENT to text)
                Feature.TIKTOK_NETWORK_FIX -> {
                    require(TikTokNetworkFix.isNumericIso(text)) { "TikTok compatibility requires a three-digit numeric ISO" }
                    mapOf(TikTokNetworkFix.COUNTRY_ISO_KEY to text)
                }
                else -> error("Unsupported string feature")
            }
        }
        if (value.data as Boolean) return enabledValues(feature)
        return when (recovery(feature, defaults)) {
            ConfigRecovery.SWITCH -> disabledValues(feature)
            ConfigRecovery.SYSTEM_DEFAULT -> defaults.filterKeys { it in keys(feature) }
            ConfigRecovery.RESET_REQUIRED -> error("Reset carrier configuration to restore ${feature.name}")
        }
    }

    fun read(values: Map<String, Any>, sdk: Int): Map<Feature, FeatureValue> = buildMap {
        primaryKeys.forEach { (feature, key) ->
            if (feature != Feature.VONR || sdk >= 34) {
                (values[key] as? Boolean)?.let { put(feature, FeatureValue(it, FeatureValueType.BOOLEAN)) }
            }
        }
        (values[NR_KEY] as? IntArray)?.let {
            put(Feature.FIVE_G_NR, FeatureValue(it.any { mode -> mode == 1 || mode == 2 }, FeatureValueType.BOOLEAN))
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
        // 与参考实现一致，Android 14+ 才开放操作；Android 13 不因隐藏键存在而推断兼容。
        if (sdk >= 34) (values[TikTokNetworkFix.COUNTRY_ISO_KEY] as? String)?.let {
            put(Feature.TIKTOK_NETWORK_FIX, FeatureValue(it, FeatureValueType.STRING))
        }
        (values[USER_AGENT] as? String)?.let { put(Feature.IMS_USER_AGENT, FeatureValue(it, FeatureValueType.STRING)) }
    }
}
