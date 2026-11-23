package app.mystery0.ims.tensor.model

enum class ImsConfigPreset { RECOMMENDED, CHINA_5G, CHINA_LTE, ALL_ENABLED }

/**
 * 预设只声明目标意图，不读取或写入系统；缺失项目保留当前配置。名称格式、USSD 方式和 5G NR 模式始终由用户选择。
 * current 为界面当前值（实际值加草稿）：预设开启 5G NR 时附带已知的模式，避免把仅 SA / 仅 NSA 改回 NSA + SA；
 * 未知组合或 5G 关闭时不附带，按缺省模式写入 [1, 2]。
 */
fun imsConfigPreset(
    preset: ImsConfigPreset,
    current: Map<Feature, FeatureValue> = emptyMap(),
): Map<Feature, FeatureValue> {
    val values = when (preset) {
        ImsConfigPreset.RECOMMENDED -> mapOf(
            Feature.VOLTE to true, Feature.VT to true, Feature.VOWIFI to false,
            Feature.VOWIFI_ROAMING to false, Feature.VONR to true,
            Feature.FIVE_G_NR to true, Feature.FIVE_G_THRESHOLDS to true,
            Feature.SHOW_4G_FOR_LTE to false,
        )
        ImsConfigPreset.CHINA_5G, ImsConfigPreset.CHINA_LTE -> {
            val nr = preset == ImsConfigPreset.CHINA_5G
            mapOf(Feature.VOLTE to true, Feature.VOWIFI to false, Feature.VOWIFI_ROAMING to false,
                Feature.VONR to nr, Feature.VT to true, Feature.CROSS_SIM to true, Feature.UT to true,
                Feature.FIVE_G_NR to nr, Feature.FIVE_G_THRESHOLDS to nr, Feature.FIVE_G_PLUS_ICON to nr,
                Feature.ENHANCED_4G_LTE to true, Feature.HIDE_LTE_PLUS_DATA_ICON to false, Feature.SHOW_4G_FOR_LTE to false)
        }
        // “全部开启”包含始终显示 RAT 开关；整数选项没有“开启”含义，不能选择任意默认值。
        ImsConfigPreset.ALL_ENABLED -> Feature.entries.filter { it.valueType == FeatureValueType.BOOLEAN }.associateWith { true }
    }
    val targets = values.mapValues { FeatureValue(it.value, FeatureValueType.BOOLEAN) }
    val mode = current[Feature.FIVE_G_NR_MODE]
        ?.takeIf { values[Feature.FIVE_G_NR] == true && CarrierConfigChoices.isValidTarget(Feature.FIVE_G_NR_MODE, it) }
    return if (mode == null) targets else targets + (Feature.FIVE_G_NR_MODE to mode)
}
