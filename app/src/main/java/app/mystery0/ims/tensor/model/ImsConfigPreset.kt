package app.mystery0.ims.tensor.model

enum class ImsConfigPreset { RECOMMENDED, CHINA_5G, CHINA_LTE, ALL_ENABLED }

/** 预设只声明目标意图，不读取或写入系统；缺失项目保留当前配置。 */
fun imsConfigPreset(preset: ImsConfigPreset): Map<Feature, FeatureValue> {
    val values = when (preset) {
        ImsConfigPreset.RECOMMENDED -> mapOf(
            Feature.VOLTE to true, Feature.VT to true, Feature.VOWIFI to false,
            Feature.VOWIFI_ROAMING to false, Feature.VONR to true,
            Feature.FIVE_G_NR to true, Feature.FIVE_G_THRESHOLDS to true,
        )
        ImsConfigPreset.CHINA_5G, ImsConfigPreset.CHINA_LTE -> {
            val nr = preset == ImsConfigPreset.CHINA_5G
            mapOf(Feature.VOLTE to true, Feature.VOWIFI to false, Feature.VOWIFI_ROAMING to false,
                Feature.VONR to nr, Feature.VT to true, Feature.CROSS_SIM to true, Feature.UT to true,
                Feature.FIVE_G_NR to nr, Feature.FIVE_G_THRESHOLDS to nr, Feature.FIVE_G_PLUS_ICON to nr,
                Feature.ENHANCED_4G_LTE to true, Feature.HIDE_LTE_PLUS_DATA_ICON to false, Feature.SHOW_4G_FOR_LTE to true)
        }
        ImsConfigPreset.ALL_ENABLED -> Feature.entries.filter { it.valueType == FeatureValueType.BOOLEAN }.associateWith { true }
    }
    return values.mapValues { FeatureValue(it.value, FeatureValueType.BOOLEAN) }
}
